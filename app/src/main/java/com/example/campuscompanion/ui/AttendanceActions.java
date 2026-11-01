package com.example.campuscompanion.ui;

import androidx.appcompat.app.AppCompatActivity;

import com.example.campuscompanion.data.local.AppDatabase;
import com.example.campuscompanion.data.local.AttendanceCourse;
import com.example.campuscompanion.data.local.AttendanceDao;
import com.example.campuscompanion.data.local.AttendanceRecord;

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
}
