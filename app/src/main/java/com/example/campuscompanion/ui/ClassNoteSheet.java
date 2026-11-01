package com.example.campuscompanion.ui;

import android.content.Context;
import android.view.WindowManager;

import androidx.appcompat.app.AppCompatActivity;

import com.example.campuscompanion.data.local.AppDatabase;
import com.example.campuscompanion.data.local.AttendanceCourse;
import com.example.campuscompanion.data.local.Topic;
import com.example.campuscompanion.data.local.TopicDao;
import com.example.campuscompanion.databinding.DialogClassNoteBinding;
import com.example.campuscompanion.util.RevisionPlan;
import com.google.android.material.bottomsheet.BottomSheetDialog;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * "What was taught today?" sheet shown after the student taps Present.
 * Saving stores the note on the attendance record and adds the topic(s) to the course checklist.
 */
public final class ClassNoteSheet {
    private ClassNoteSheet() { }

    public static void show(AppCompatActivity act, AttendanceCourse course, int recordId,
                            long date, Runnable onClosed) {
        DialogClassNoteBinding d = DialogClassNoteBinding.inflate(act.getLayoutInflater());
        BottomSheetDialog sheet = new BottomSheetDialog(act);
        sheet.setContentView(d.getRoot());
        if (sheet.getWindow() != null) {
            sheet.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        }

        String when = com.example.campuscompanion.util.DateUtil.sameDay(date, System.currentTimeMillis())
                ? "" : "  •  " + new SimpleDateFormat("EEE, dd MMM", Locale.getDefault()).format(new Date(date));
        d.tvNoteCourse.setText(course.title() + when);

        final boolean[] closed = {false};
        Runnable finish = () -> {
            if (closed[0]) return;
            closed[0] = true;
            if (onClosed != null) onClosed.run();
        };
        sheet.setOnDismissListener(di -> finish.run());

        d.btnNoteSkip.setOnClickListener(v -> sheet.dismiss());
        d.btnNoteSave.setOnClickListener(v -> {
            String raw = d.etNoteTopic.getText() == null ? "" : d.etNoteTopic.getText().toString();
            boolean remind = d.swRemind.isChecked();
            save(act.getApplicationContext(), course, recordId, date, raw, remind);
            sheet.dismiss();
        });

        sheet.show();
        d.etNoteTopic.requestFocus();
    }

    /** One topic per line. Empty lines are ignored, duplicates inside the text are merged. */
    static List<String> split(String raw) {
        List<String> out = new ArrayList<>();
        for (String line : raw.split("\\r?\\n")) {
            String t = line.trim();
            if (t.isEmpty()) continue;
            boolean dup = false;
            for (String o : out) if (o.equalsIgnoreCase(t)) dup = true;
            if (!dup) out.add(t);
        }
        return out;
    }

    private static void save(Context app, AttendanceCourse course, int recordId, long date,
                             String raw, boolean remind) {
        List<String> topics = split(raw);
        if (topics.isEmpty()) return;

        AppDatabase.executor.execute(() -> {
            AppDatabase db = AppDatabase.get(app);
            TopicDao dao = db.topicDao();
            db.attendanceDao().setTopic(recordId, joinTopics(topics));

            long now = System.currentTimeMillis();
            for (String title : topics) {
                Topic t = dao.findByTitle(course.id, title);
                if (t == null) {
                    t = new Topic();
                    t.courseId = course.id;
                    t.title = title;
                    RevisionPlan.startRevision(t, date, remind, now);
                    dao.insert(t);
                } else if (!t.taught) {
                    // It was only planned in the checklist: now it has been taught
                    RevisionPlan.startRevision(t, date, remind, now);
                    dao.update(t);
                }
                // Already taught before: keep its revision progress as it is
            }
        });
    }

    static String joinTopics(List<String> topics) {
        StringBuilder sb = new StringBuilder();
        for (String t : topics) {
            if (sb.length() > 0) sb.append(", ");
            sb.append(t);
        }
        return sb.toString();
    }
}
