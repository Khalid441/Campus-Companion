package com.example.campuscompanion.ui;

import androidx.appcompat.app.AppCompatActivity;

import com.example.campuscompanion.data.local.AppDatabase;
import com.example.campuscompanion.data.local.AttendanceCourse;
import com.example.campuscompanion.data.local.AttendanceDao;
import com.example.campuscompanion.data.local.AttendanceRecord;
import com.example.campuscompanion.notification.NotificationHelper;
import com.example.campuscompanion.util.AttendanceMath;

/** Marking a class, shared by the Attendance screen and the "My day" timeline. */
public final class AttendanceActions {
    private AttendanceActions() { }

    /** Minimum attendance used when a course is added automatically; the student can change it later. */
    public static final int DEFAULT_MIN_PERCENT = 75;

    /**
     * Makes sure the routine class has an attendance course, creating one when missing.
     * Call from a background thread. Returns the course (existing or new).
     */
    public static AttendanceCourse ensureCourse(android.content.Context app, String routineCourse) {
        if (routineCourse == null || routineCourse.trim().length() < 3) return null;
        AttendanceDao dao = AppDatabase.get(app).attendanceDao();
        java.util.List<AttendanceCourse> all = dao.getCoursesNow();
        AttendanceCourse found = com.example.campuscompanion.util.CourseMatcher.find(all, routineCourse);
        if (found != null) return found;

        String text = routineCourse.trim();
        AttendanceCourse c = new AttendanceCourse();
        java.util.regex.Matcher m = java.util.regex.Pattern
                .compile("\\b([A-Za-z]{2,4})[\\s-]?(\\d{3,4})\\b").matcher(text);
        if (m.find()) {
            c.code = m.group(1).toUpperCase(java.util.Locale.ROOT) + " " + m.group(2);
            String rest = (text.substring(0, m.start()) + " " + text.substring(m.end())).replaceAll("\\s+", " ").trim();
            c.name = rest.isEmpty() ? c.code : rest;
        } else {
            c.code = "";
            c.name = text;
        }
        c.minPercent = DEFAULT_MIN_PERCENT;
        c.totalPlanned = 0;
        c.createdAt = System.currentTimeMillis();
        c.examDate = 0;
        c.id = (int) dao.insertCourse(c);
        return c;
    }

    public interface Saved {
        void onSaved(AttendanceRecord record);
    }

    /**
     * Saves one class. For Present the "What was taught today?" sheet opens first;
     * {@code done} is called once the sheet is closed (immediately for Absent / Cancelled).
     */
    public static void mark(AppCompatActivity act, AttendanceCourse course, int status,
                            long date, Saved done) {
        AttendanceRecord r = new AttendanceRecord();
        r.courseId = course.id;
        r.date = date;
        r.status = status;

        AttendanceDao dao = AppDatabase.get(act).attendanceDao();
        AppDatabase.executor.execute(() -> {
            r.id = (int) dao.insertRecord(r);
            if (status == AttendanceRecord.ABSENT) warnIfLow(act.getApplicationContext(), course.id);
            act.runOnUiThread(() -> {
                if (act.isFinishing() || act.isDestroyed()) return;
                if (status == AttendanceRecord.PRESENT) {
                    ClassNoteSheet.show(act, course, r.id, date, () -> done.onSaved(r));
                } else {
                    done.onSaved(r);
                }
            });
        });
    }

    /** After an absence: notify when the course is below, or within 5 points of, the minimum. */
    private static void warnIfLow(android.content.Context app, int courseId) {
        AttendanceDao dao = AppDatabase.get(app).attendanceDao();
        AttendanceCourse c = dao.courseNow(courseId);
        if (c == null) return;
        int present = 0, absent = 0, cancelled = 0;
        for (AttendanceRecord x : dao.recordsOfNow(courseId)) {
            if (x.status == AttendanceRecord.PRESENT) present++;
            else if (x.status == AttendanceRecord.ABSENT) absent++;
            else cancelled++;
        }
        AttendanceMath.Result res = AttendanceMath.compute(present, absent, cancelled,
                c.minPercent, c.totalPlanned);
        if (res.level == AttendanceMath.SAFE) return;

        String lead = res.level == AttendanceMath.LOW
                ? "Attendance is " + res.percent + "%, below your " + c.minPercent + "%. "
                : "Attendance is " + res.percent + "%, close to your " + c.minPercent + "% limit. ";
        NotificationHelper.showAttendanceWarning(app, courseId,
                c.title() + (res.level == AttendanceMath.LOW ? ": below minimum" : ": careful"),
                lead + res.advice);
    }
}
