package com.example.campuscompanion.ui;

import android.app.DatePickerDialog;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.example.campuscompanion.R;
import com.example.campuscompanion.data.local.AppDatabase;
import com.example.campuscompanion.data.local.AttendanceCourse;
import com.example.campuscompanion.data.local.Exam;
import com.example.campuscompanion.data.local.Topic;
import com.example.campuscompanion.data.local.TopicDao;
import com.example.campuscompanion.databinding.ActivityRevisionBinding;
import com.example.campuscompanion.databinding.DialogRevisionAddBinding;
import com.example.campuscompanion.databinding.ItemRevisionBinding;
import com.example.campuscompanion.util.CourseMatcher;
import com.example.campuscompanion.util.DateUtil;
import com.example.campuscompanion.util.ExamUtil;
import com.example.campuscompanion.util.RevisionPlan;
import com.example.campuscompanion.util.StudyStats;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Revision list. The student adds topics here by hand (+), and topics noted after a class
 * show up too. Each one comes back 1, 3 and 7 days later.
 */
public class RevisionActivity extends AppCompatActivity {

    private static final SimpleDateFormat DAY = new SimpleDateFormat("EEE, dd MMM", Locale.getDefault());

    private ActivityRevisionBinding b;
    private TopicDao dao;
    private List<Topic> topics = new ArrayList<>();
    private List<AttendanceCourse> courses = new ArrayList<>();
    private List<Exam> exams = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        b = ActivityRevisionBinding.inflate(getLayoutInflater());
        setContentView(b.getRoot());

        AppDatabase db = AppDatabase.get(this);
        dao = db.topicDao();
        db.attendanceDao().getCourses().observe(this, list -> { courses = list; render(); });
        db.examDao().getAll().observe(this, list -> { exams = list; render(); });
        dao.getAll().observe(this, list -> { topics = list; render(); });
        b.fabAdd.setOnClickListener(v -> showAddDialog());
    }

    /** Name shown for a topic: its attendance course, or the subject the student typed. */
    private String subjectOf(Topic t) {
        for (AttendanceCourse c : courses) if (c.id == t.courseId) return c.title();
        return t.subject == null || t.subject.isEmpty() ? "Revision" : t.subject;
    }

    /** Days to the nearest upcoming exam of the same subject; a large number when none. */
    private int examDays(Topic t) {
        String subject = subjectOf(t);
        int best = 100000;
        for (Exam e : exams) {
            int d = ExamUtil.daysLeft(e.examDate);
            if (d >= 0 && d < best && CourseMatcher.same(subject, e.subject)) best = d;
        }
        return best;
    }

    private void render() {
        long now = System.currentTimeMillis();
        List<Topic> due = new ArrayList<>();
        List<Topic> upcoming = new ArrayList<>();
        for (Topic t : topics) {
            if (!RevisionPlan.isActive(t)) continue;
            if (RevisionPlan.isDue(t, now)) due.add(t); else upcoming.add(t);
        }
        // Topics of the subject whose exam is nearest come first
        Collections.sort(due, (x, y) -> {
            int dx = examDays(x), dy = examDays(y);
            if (dx != dy) return Integer.compare(dx, dy);
            return Long.compare(x.nextRevisionAt, y.nextRevisionAt);
        });
        Collections.sort(upcoming, (x, y) -> Long.compare(x.nextRevisionAt, y.nextRevisionAt));

        b.list.removeAllViews();
        b.emptyText.setVisibility(due.isEmpty() && upcoming.isEmpty() ? View.VISIBLE : View.GONE);
        b.tvRevisionSub.setText((due.isEmpty() ? "Nothing due today"
                : due.size() + (due.size() == 1 ? " topic" : " topics") + " to revise today")
                + "\n" + StudyStats.summary(this));

        if (!due.isEmpty()) {
            addHeading("Revise today");
            for (Topic t : due) b.list.addView(row(t, true));
        }
        if (!upcoming.isEmpty()) {
            addHeading("Coming up");
            for (Topic t : upcoming) b.list.addView(row(t, false));
        }
    }

    private void addHeading(String text) {
        TextView h = new TextView(this);
        h.setText(text);
        h.setTextSize(14);
        h.setTypeface(h.getTypeface(), android.graphics.Typeface.BOLD);
        h.setTextColor(getColor(R.color.study_teal_text));
        int pad = (int) (16 * getResources().getDisplayMetrics().density);
        h.setPadding(pad, pad / 2, pad, 0);
        b.list.addView(h);
    }

    private View row(Topic t, boolean due) {
        ItemRevisionBinding r = ItemRevisionBinding.inflate(LayoutInflater.from(this), b.list, false);
        r.tvRevCourse.setText(subjectOf(t));
        r.tvRevTopic.setText(t.title);

        int exam = examDays(t);
        r.tvRevInfo.setText("Revision " + (t.revisedCount + 1) + " of " + RevisionPlan.STAGES
                + "  •  from " + DAY.format(new Date(t.taughtAt))
                + (due ? "" : "  •  due " + DAY.format(new Date(t.nextRevisionAt)))
                + (exam < 100000 ? "  •  exam " + ExamUtil.label(exam) : ""));

        r.btnRevDone.setVisibility(due ? View.VISIBLE : View.GONE);
        r.btnRevDone.setOnClickListener(v -> AppDatabase.executor.execute(() -> {
            Topic u = t.copy();
            RevisionPlan.markRevised(u, System.currentTimeMillis());
            dao.update(u);
            StudyStats.recordRevision(getApplicationContext());
            runOnUiThread(this::render);
        }));

        r.getRoot().setOnLongClickListener(v -> {
            new MaterialAlertDialogBuilder(this)
                    .setTitle("Remove from revision?")
                    .setMessage(t.title)
                    .setPositiveButton("Remove", (d, w) -> AppDatabase.executor.execute(() -> dao.delete(t)))
                    .setNegativeButton("Cancel", null)
                    .show();
            return true;
        });
        return r.getRoot();
    }

    // ---------------------------------------------------------------------
    // Add by hand
    // ---------------------------------------------------------------------

    private void showAddDialog() {
        DialogRevisionAddBinding d = DialogRevisionAddBinding.inflate(getLayoutInflater());
        final long[] date = {DateUtil.startOfDay(System.currentTimeMillis())};
        d.etRevDate.setText(DAY.format(new Date(date[0])));
        d.etRevDate.setOnClickListener(v -> {
            Calendar start = Calendar.getInstance();
            start.setTimeInMillis(date[0]);
            DatePickerDialog picker = new DatePickerDialog(this, (pk, y, m, day) -> {
                Calendar chosen = Calendar.getInstance();
                chosen.set(y, m, day);
                date[0] = DateUtil.startOfDay(chosen.getTimeInMillis());
                d.etRevDate.setText(DAY.format(new Date(date[0])));
            }, start.get(Calendar.YEAR), start.get(Calendar.MONTH), start.get(Calendar.DAY_OF_MONTH));
            picker.getDatePicker().setMaxDate(System.currentTimeMillis());
            picker.show();
        });

        androidx.appcompat.app.AlertDialog dialog = new MaterialAlertDialogBuilder(this)
                .setTitle("Add revision topic")
                .setView(d.getRoot())
                .setPositiveButton("Add", null)
                .setNegativeButton("Cancel", null)
                .create();
        dialog.show();

        dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String subject = d.etRevSubject.getText() == null ? "" : d.etRevSubject.getText().toString().trim();
            String title = d.etRevTopic.getText() == null ? "" : d.etRevTopic.getText().toString().trim();
            d.tilRevSubject.setError(null);
            d.tilRevTopic.setError(null);
            if (subject.isEmpty()) { d.tilRevSubject.setError("Enter the subject"); return; }
            if (title.isEmpty()) { d.tilRevTopic.setError("Enter the topic"); return; }

            // If it is one of the attendance courses, keep the link so both lists agree
            int courseId = 0;
            for (AttendanceCourse c : courses) {
                if (CourseMatcher.same(c.title(), subject)) { courseId = c.id; break; }
            }
            final int cid = courseId;
            final long day = date[0];
            AppDatabase.executor.execute(() -> {
                if (cid != 0 && dao.findByTitle(cid, title) != null) return;   // already there
                Topic t = new Topic();
                t.courseId = cid;
                t.subject = subject;
                t.title = title;
                RevisionPlan.startRevision(t, day, true, System.currentTimeMillis());
                dao.insert(t);
            });
            dialog.dismiss();
        });
    }
}
