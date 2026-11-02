package com.example.campuscompanion.util;

import android.content.Context;

import com.example.campuscompanion.data.local.AppDatabase;
import com.example.campuscompanion.data.local.AttendanceCourse;
import com.example.campuscompanion.data.local.AttendanceRecord;
import com.example.campuscompanion.data.local.Deadline;
import com.example.campuscompanion.data.local.Exam;
import com.example.campuscompanion.data.local.RoutineEntry;
import com.example.campuscompanion.data.local.StudySession;
import com.example.campuscompanion.data.local.Topic;
import com.example.campuscompanion.reminder.ReminderScheduler;

import java.util.Calendar;

/**
 * Demo content so the app can be shown with real-looking data in seconds.
 * Everything it creates starts with "[Demo]" so it can be removed again without touching
 * the student's own entries.
 */
public final class SampleData {
    public static final String TAG = "[Demo] ";

    private SampleData() { }

    public interface Done { void onDone(); }

    public static void load(Context context, Done done) {
        Context app = context.getApplicationContext();
        AppDatabase.executor.execute(() -> {
            AppDatabase db = AppDatabase.get(app);
            long now = System.currentTimeMillis();
            long today = DateUtil.startOfDay(now);

            // ---- courses with marked classes ----
            int ds = course(db, "Data Structures", "CSE 2101", 75, 40, 11, 2, today);
            int al = course(db, "Algorithms", "CSE 2203", 75, 40, 6, 4, today);

            // ---- topics (one is due for revision today, so the plan has something to show) ----
            topic(db, ds, "Linked lists", today, -1, 0);
            topic(db, ds, "Stacks and queues", today, -4, 1);
            topic(db, ds, "Binary trees", today, -9, 2);
            topic(db, al, "Divide and conquer", today, -2, 0);
            topic(db, al, "Dynamic programming", today, 0, 0);

            // ---- deadlines ----
            deadline(db, app, "Assignment 2", TAG.trim() + " Data Structures", now + 20L * 3600_000, Deadline.HIGH);
            deadline(db, app, "Lab report", TAG.trim() + " Algorithms", now + 4L * 86_400_000, Deadline.MEDIUM);

            // ---- exam ----
            Exam e = new Exam();
            e.subject = TAG + "Data Structures";
            e.examDate = DateUtil.addDays(today, 6);
            e.note = "Class test 1";
            db.examDao().insert(e);

            // ---- study session (every day, 8 PM) ----
            StudySession s = new StudySession();
            s.subject = TAG + "Algorithms practice";
            s.topic = "Solve 3 problems";
            s.daysMask = 0x7F;
            s.startMinutes = 20 * 60;
            s.endMinutes = 21 * 60;
            s.reminderMinutes = -1;
            s.enabled = true;
            db.studyDao().insert(s);

            // ---- a class today ----
            RoutineEntry r = new RoutineEntry();
            r.course = TAG + "Data Structures";
            r.room = "Room 201";
            r.teacher = "Demo teacher";
            r.dayOfWeek = Calendar.getInstance().get(Calendar.DAY_OF_WEEK);
            r.startMinutes = 23 * 60;
            r.endMinutes = 23 * 60 + 50;
            db.routineDao().insert(r);

            if (done != null) done.onDone();
        });
    }

    /** Removes only what load() created. */
    public static void remove(Context context, Done done) {
        Context app = context.getApplicationContext();
        AppDatabase.executor.execute(() -> {
            AppDatabase db = AppDatabase.get(app);
            for (AttendanceCourse c : db.attendanceDao().getCoursesNow()) {
                if (c.name != null && c.name.startsWith(TAG)) {
                    db.attendanceDao().deleteRecordsOf(c.id);
                    db.topicDao().deleteOf(c.id);
                    db.attendanceDao().deleteCourse(c);
                }
            }
            for (Deadline d : db.deadlineDao().getAllNow()) {
                if (d.title != null && d.title.startsWith(TAG)) {
                    ReminderScheduler.cancel(app, d.id);
                    db.deadlineDao().delete(d);
                }
            }
            for (Exam e : db.examDao().getAllNow()) {
                if (e.subject != null && e.subject.startsWith(TAG)) db.examDao().delete(e);
            }
            for (StudySession s : db.studyDao().getAllNow()) {
                if (s.subject != null && s.subject.startsWith(TAG)) db.studyDao().delete(s);
            }
            for (RoutineEntry r : db.routineDao().getAllNow()) {
                if (r.course != null && r.course.startsWith(TAG)) db.routineDao().delete(r);
            }
            if (done != null) done.onDone();
        });
    }

    private static int course(AppDatabase db, String name, String code, int min, int total,
                              int present, int absent, long today) {
        AttendanceCourse c = new AttendanceCourse();
        c.name = TAG + name;
        c.code = code;
        c.minPercent = min;
        c.totalPlanned = total;
        c.createdAt = System.currentTimeMillis();
        int id = (int) db.attendanceDao().insertCourse(c);
        int day = 0;
        for (int i = 0; i < present + absent; i++) {
            AttendanceRecord r = new AttendanceRecord();
            r.courseId = id;
            r.date = DateUtil.addDays(today, -(2 + day * 2));
            r.status = i < present ? AttendanceRecord.PRESENT : AttendanceRecord.ABSENT;
            db.attendanceDao().insertRecord(r);
            day++;
        }
        return id;
    }

    private static void topic(AppDatabase db, int courseId, String title, long today,
                              int taughtOffsetDays, int revised) {
        Topic t = new Topic();
        t.courseId = courseId;
        t.title = title;
        long now = System.currentTimeMillis();
        RevisionPlan.startRevision(t, DateUtil.addDays(today, taughtOffsetDays), true, now);
        t.revisedCount = revised;
        if (revised > 0) t.nextRevisionAt = RevisionPlan.nextDue(t.taughtAt, revised, now);
        db.topicDao().insert(t);
    }

    private static void deadline(AppDatabase db, Context app, String title, String course,
                                 long dueAt, String priority) {
        Deadline d = new Deadline();
        d.title = TAG + title;
        d.course = course;
        d.note = "Sample entry";
        d.priority = priority;
        d.dueAt = dueAt;
        d.remindBeforeMinutes = -1;
        d.id = (int) db.deadlineDao().insert(d);
    }
}
