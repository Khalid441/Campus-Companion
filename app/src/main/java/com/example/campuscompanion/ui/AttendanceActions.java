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
