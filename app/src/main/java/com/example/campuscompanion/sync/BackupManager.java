package com.example.campuscompanion.sync;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;

import androidx.work.Constraints;
import androidx.work.ExistingWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;

import com.example.campuscompanion.data.local.AppDatabase;
import com.example.campuscompanion.data.local.AttendanceCourse;
import com.example.campuscompanion.data.local.AttendanceRecord;
import com.example.campuscompanion.data.local.Deadline;
import com.example.campuscompanion.data.local.RoutineEntry;
import com.example.campuscompanion.data.local.StudySession;
import com.example.campuscompanion.data.local.Topic;
import com.example.campuscompanion.reminder.ReminderScheduler;
import com.example.campuscompanion.reminder.StudyReminderScheduler;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FirebaseFirestore;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Optional cloud backup. The student's own data (attendance, class notes, topics, routine,
 * study plan and personal deadlines) is copied to users/{uid}/backup/latest in Firestore.
 * Only that student can read or write it (see firestore.rules). It is OFF until switched on.
 */
public final class BackupManager {

    private static final String PREFS = "settings";
    private static final String KEY_ON = "cloud_backup_on";
    private static final String KEY_LAST = "cloud_backup_last";
    private static final String WORK_NAME = "cloud_backup_auto";

    private BackupManager() { }

    public interface Callback {
        void done(boolean ok, String message);
    }

    // ---------------------------------------------------------------------
    // Settings
    // ---------------------------------------------------------------------

    private static SharedPreferences prefs(Context c) {
        return c.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public static boolean isEnabled(Context c) {
        return prefs(c).getBoolean(KEY_ON, false);
    }

    public static long lastBackup(Context c) {
        return prefs(c).getLong(KEY_LAST, 0);
    }

    public static void setEnabled(Context c, boolean on) {
        prefs(c).edit().putBoolean(KEY_ON, on).apply();
        if (on) scheduleIfEnabled(c); else cancel(c);
    }

    public static void cancel(Context c) {
        WorkManager.getInstance(c.getApplicationContext()).cancelUniqueWork(WORK_NAME);
    }

    /** Called after every data change. Waits 2 minutes so many edits become one upload. */
    public static void scheduleIfEnabled(Context c) {
        if (!isEnabled(c) || FirebaseAuth.getInstance().getUid() == null) return;
        OneTimeWorkRequest request = new OneTimeWorkRequest.Builder(BackupWorker.class)
                .setInitialDelay(2, TimeUnit.MINUTES)
                .setConstraints(new Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build();
        WorkManager.getInstance(c.getApplicationContext())
                .enqueueUniqueWork(WORK_NAME, ExistingWorkPolicy.REPLACE, request);
    }

    private static DocumentReference doc() {
        String uid = FirebaseAuth.getInstance().getUid();
        if (uid == null) return null;
        return FirebaseFirestore.getInstance().collection("users").document(uid)
                .collection("backup").document("latest");
    }

    // ---------------------------------------------------------------------
    // Back up
    // ---------------------------------------------------------------------

    /** Used by the background worker (call on a background thread). Returns true on success. */
    public static boolean backupBlocking(Context c) {
        try {
            DocumentReference ref = doc();
            if (ref == null) return false;
            Context app = c.getApplicationContext();
            String json = buildJson(app);
            Map<String, Object> map = new HashMap<>();
            map.put("data", json);
            map.put("updatedAt", System.currentTimeMillis());
            map.put("version", 1);
            Tasks.await(ref.set(map), 30, TimeUnit.SECONDS);
            prefs(app).edit().putLong(KEY_LAST, System.currentTimeMillis()).apply();
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /** "Back up now" button. */
    public static void backupNow(Context c, Callback cb) {
        Context app = c.getApplicationContext();
        AppDatabase.executor.execute(() -> {
            boolean ok = backupBlocking(app);
            post(cb, ok, ok ? "Backup saved" : "Backup failed. Check your internet connection and sign-in.");
        });
    }

    // ---------------------------------------------------------------------
    // Restore
    // ---------------------------------------------------------------------

    /** "Restore" button: replaces this phone's data with the last backup. */
    public static void restore(Context c, Callback cb) {
        Context app = c.getApplicationContext();
        DocumentReference ref = doc();
        if (ref == null) {
            post(cb, false, "Sign in first");
            return;
        }
        ref.get().addOnSuccessListener(snap -> {
            String json = snap.exists() ? snap.getString("data") : null;
            if (json == null) {
                post(cb, false, "No backup found for this account");
                return;
            }
            AppDatabase.executor.execute(() -> {
                try {
                    int n = applyJson(app, json);
                    post(cb, true, "Restored " + n + " items");
                } catch (Exception e) {
                    post(cb, false, "Restore failed: " + e.getMessage());
                }
            });
        }).addOnFailureListener(e -> post(cb, false, "Restore failed: " + e.getMessage()));
    }

    private static void post(Callback cb, boolean ok, String msg) {
        if (cb == null) return;
        new Handler(Looper.getMainLooper()).post(() -> cb.done(ok, msg));
    }

    // ---------------------------------------------------------------------
    // JSON
    // ---------------------------------------------------------------------

    static String buildJson(Context app) throws JSONException {
        AppDatabase db = AppDatabase.get(app);
        JSONObject root = new JSONObject();
        root.put("v", 1);

        JSONArray courses = new JSONArray();
        for (AttendanceCourse c : db.attendanceDao().getCoursesNow()) {
            courses.put(new JSONObject().put("id", c.id).put("name", c.name).put("code", c.code)
                    .put("minPercent", c.minPercent).put("totalPlanned", c.totalPlanned)
                    .put("createdAt", c.createdAt).put("examDate", c.examDate));
        }
        root.put("courses", courses);

        JSONArray records = new JSONArray();
        for (AttendanceRecord r : db.attendanceDao().getRecordsNow()) {
            records.put(new JSONObject().put("id", r.id).put("courseId", r.courseId)
                    .put("date", r.date).put("status", r.status).put("topic", r.topic));
        }
        root.put("records", records);

        JSONArray topics = new JSONArray();
        for (Topic t : db.topicDao().getAllNow()) {
            topics.put(new JSONObject().put("id", t.id).put("courseId", t.courseId)
                    .put("title", t.title).put("taught", t.taught).put("taughtAt", t.taughtAt)
                    .put("revisedCount", t.revisedCount).put("nextRevisionAt", t.nextRevisionAt)
                    .put("remind", t.remind));
        }
        root.put("topics", topics);

        JSONArray routine = new JSONArray();
        for (RoutineEntry e : db.routineDao().getAllNow()) {
            routine.put(new JSONObject().put("course", e.course).put("room", e.room)
                    .put("teacher", e.teacher).put("dayOfWeek", e.dayOfWeek)
                    .put("startMinutes", e.startMinutes).put("endMinutes", e.endMinutes));
        }
        root.put("routine", routine);

        JSONArray study = new JSONArray();
        for (StudySession s : db.studyDao().getAllNow()) {
            study.put(new JSONObject().put("subject", s.subject).put("topic", s.topic)
                    .put("daysMask", s.daysMask).put("startMinutes", s.startMinutes)
                    .put("endMinutes", s.endMinutes).put("reminderMinutes", s.reminderMinutes)
                    .put("enabled", s.enabled));
        }
        root.put("study", study);

        JSONArray deadlines = new JSONArray();
        for (Deadline d : db.deadlineDao().getPersonal()) {
            deadlines.put(new JSONObject().put("title", d.title).put("course", d.course)
                    .put("note", d.note).put("priority", d.priority).put("dueAt", d.dueAt)
                    .put("remindBeforeMinutes", d.remindBeforeMinutes).put("done", d.done));
        }
        root.put("deadlines", deadlines);
        return root.toString();
    }

    /** Replaces this phone's data with the backup. Runs on the database thread. */
    static int applyJson(Context app, String json) throws JSONException {
        JSONObject root = new JSONObject(json);
        AppDatabase db = AppDatabase.get(app);

        // Old alarms must go before the old rows disappear
        for (Deadline d : db.deadlineDao().getPersonal()) ReminderScheduler.cancel(app, d.id);
        for (StudySession s : db.studyDao().getAllNow()) StudyReminderScheduler.cancel(app, s.id);

        final int[] count = {0};
        final JSONException[] error = {null};
        db.runInTransaction(() -> {
            try {
                db.attendanceDao().deleteAllRecords();
                db.topicDao().deleteAll();
                db.attendanceDao().deleteAllCourses();
                db.routineDao().deleteAll();
                db.studyDao().deleteAll();
                db.deadlineDao().deletePersonal();

                JSONArray a = root.optJSONArray("courses");
                for (int i = 0; a != null && i < a.length(); i++) {
                    JSONObject o = a.getJSONObject(i);
                    AttendanceCourse c = new AttendanceCourse();
                    c.id = o.getInt("id");
                    c.name = str(o, "name");
                    c.code = str(o, "code");
                    c.minPercent = o.optInt("minPercent", 75);
                    c.totalPlanned = o.optInt("totalPlanned", 0);
                    c.createdAt = o.optLong("createdAt", 0);
                    c.examDate = o.optLong("examDate", 0);
                    db.attendanceDao().insertCourse(c);
                    count[0]++;
                }
                a = root.optJSONArray("records");
                for (int i = 0; a != null && i < a.length(); i++) {
                    JSONObject o = a.getJSONObject(i);
                    AttendanceRecord r = new AttendanceRecord();
                    r.id = o.getInt("id");
                    r.courseId = o.getInt("courseId");
                    r.date = o.getLong("date");
                    r.status = o.getInt("status");
                    r.topic = str(o, "topic");
                    db.attendanceDao().insertRecord(r);
                    count[0]++;
                }
                a = root.optJSONArray("topics");
                for (int i = 0; a != null && i < a.length(); i++) {
                    JSONObject o = a.getJSONObject(i);
                    Topic t = new Topic();
                    t.id = o.getInt("id");
                    t.courseId = o.getInt("courseId");
                    t.title = str(o, "title");
                    t.taught = o.optBoolean("taught", false);
                    t.taughtAt = o.optLong("taughtAt", 0);
                    t.revisedCount = o.optInt("revisedCount", 0);
                    t.nextRevisionAt = o.optLong("nextRevisionAt", 0);
                    t.remind = o.optBoolean("remind", false);
                    db.topicDao().insert(t);
                    count[0]++;
                }
                a = root.optJSONArray("routine");
                for (int i = 0; a != null && i < a.length(); i++) {
                    JSONObject o = a.getJSONObject(i);
                    RoutineEntry e = new RoutineEntry();
                    e.course = str(o, "course");
                    e.room = str(o, "room");
                    e.teacher = str(o, "teacher");
                    e.dayOfWeek = o.getInt("dayOfWeek");
                    e.startMinutes = o.getInt("startMinutes");
                    e.endMinutes = o.getInt("endMinutes");
                    db.routineDao().insert(e);
                    count[0]++;
                }
                a = root.optJSONArray("study");
                for (int i = 0; a != null && i < a.length(); i++) {
                    JSONObject o = a.getJSONObject(i);
                    StudySession s = new StudySession();
                    s.subject = str(o, "subject");
                    s.topic = str(o, "topic");
                    s.daysMask = o.getInt("daysMask");
                    s.startMinutes = o.getInt("startMinutes");
                    s.endMinutes = o.getInt("endMinutes");
                    s.reminderMinutes = o.optInt("reminderMinutes", -1);
                    s.enabled = o.optBoolean("enabled", true);
                    s.id = (int) db.studyDao().insert(s);
                    count[0]++;
                }
                a = root.optJSONArray("deadlines");
                for (int i = 0; a != null && i < a.length(); i++) {
                    JSONObject o = a.getJSONObject(i);
                    Deadline d = new Deadline();
                    d.title = str(o, "title");
                    d.course = str(o, "course");
                    d.note = str(o, "note");
                    d.priority = o.optString("priority", Deadline.MEDIUM);
                    d.dueAt = o.getLong("dueAt");
                    d.remindBeforeMinutes = o.optInt("remindBeforeMinutes", -1);
                    d.done = o.optBoolean("done", false);
                    d.isShared = false;
                    d.id = (int) db.deadlineDao().insert(d);
                    count[0]++;
                }
            } catch (JSONException e) {
                error[0] = e;
                throw new IllegalStateException(e);   // rolls the whole restore back
            }
        });
        if (error[0] != null) throw error[0];

        // Re-arm the reminders of the restored rows
        for (Deadline d : db.deadlineDao().getPending()) ReminderScheduler.schedule(app, d);
        for (StudySession s : db.studyDao().getAllNow()) StudyReminderScheduler.schedule(app, s);
        return count[0];
    }

    /** Null-safe string (JSON null becomes Java null, not the text "null"). */
    private static String str(JSONObject o, String key) {
        return o.isNull(key) ? null : o.optString(key, null);
    }
}
