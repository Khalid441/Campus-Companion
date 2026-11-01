package com.example.campuscompanion.ui;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.example.campuscompanion.data.local.AppDatabase;
import com.example.campuscompanion.data.local.AttendanceCourse;
import com.example.campuscompanion.data.local.Topic;
import com.example.campuscompanion.data.local.TopicDao;
import com.example.campuscompanion.databinding.ActivityRevisionBinding;
import com.example.campuscompanion.databinding.ItemRevisionBinding;
import com.example.campuscompanion.util.DateUtil;
import com.example.campuscompanion.util.ExamUtil;
import com.example.campuscompanion.util.StudyStats;
import com.example.campuscompanion.util.RevisionPlan;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Topics that are due for revision today, and the ones coming up. */
public class RevisionActivity extends AppCompatActivity {

    private static final SimpleDateFormat DAY = new SimpleDateFormat("EEE, dd MMM", Locale.getDefault());

    private ActivityRevisionBinding b;
    private TopicDao dao;
    private List<Topic> topics = new ArrayList<>();
    private Map<Integer, String> courseNames = new HashMap<>();
    private Map<Integer, Long> courseExams = new HashMap<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        b = ActivityRevisionBinding.inflate(getLayoutInflater());
        setContentView(b.getRoot());

        AppDatabase db = AppDatabase.get(this);
        dao = db.topicDao();
        db.attendanceDao().getCourses().observe(this, list -> {
            courseNames = new HashMap<>();
            courseExams = new HashMap<>();
            for (AttendanceCourse c : list) {
                courseNames.put(c.id, c.title());
                courseExams.put(c.id, c.examDate);
            }
            render();
        });
        dao.getAll().observe(this, list -> {
            topics = list;
            render();
        });
    }

    private void render() {
        long now = System.currentTimeMillis();
        List<Topic> due = new ArrayList<>();
        List<Topic> upcoming = new ArrayList<>();
        for (Topic t : topics) {
            if (!RevisionPlan.isActive(t)) continue;
            if (RevisionPlan.isDue(t, now)) due.add(t); else upcoming.add(t);
        }
        // Topics of the course whose exam is nearest come first
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
            for (Topic t : due) b.list.addView(row(t, true, now));
        }
        if (!upcoming.isEmpty()) {
            addHeading("Coming up");
            for (Topic t : upcoming) b.list.addView(row(t, false, now));
        }
    }

    /** Days to the exam of the topic's course; a large number when there is none. */
    private int examDays(Topic t) {
        Long exam = courseExams.get(t.courseId);
        int d = exam == null ? -1 : ExamUtil.daysLeft(exam);
        return d < 0 ? 100000 : d;
    }

    private void addHeading(String text) {
        TextView h = new TextView(this);
        h.setText(text);
        h.setTextSize(14);
        h.setTypeface(h.getTypeface(), android.graphics.Typeface.BOLD);
        h.setTextColor(getColor(com.example.campuscompanion.R.color.study_teal_text));
        int pad = (int) (16 * getResources().getDisplayMetrics().density);
        h.setPadding(pad, pad / 2, pad, 0);
        b.list.addView(h);
    }

    private View row(Topic t, boolean due, long now) {
        ItemRevisionBinding r = ItemRevisionBinding.inflate(LayoutInflater.from(this), b.list, false);
        String course = courseNames.get(t.courseId);
        r.tvRevCourse.setText(course == null ? "Course" : course);
        r.tvRevTopic.setText(t.title);

        int stage = t.revisedCount + 1;
        r.tvRevInfo.setText("Revision " + stage + " of " + RevisionPlan.STAGES + "  •  taught "
                + DAY.format(new Date(t.taughtAt))
                + (due ? "" : "  •  due " + DAY.format(new Date(t.nextRevisionAt)))
                + (examDays(t) < 100000 ? "  •  exam " + ExamUtil.label(examDays(t)) : ""));

        r.btnRevDone.setVisibility(due ? View.VISIBLE : View.GONE);
        r.btnRevDone.setOnClickListener(v -> AppDatabase.executor.execute(() -> {
            Topic u = t.copy();
            RevisionPlan.markRevised(u, System.currentTimeMillis());
            dao.update(u);
            StudyStats.recordRevision(getApplicationContext());
            runOnUiThread(this::render);
        }));
        return r.getRoot();
    }
}
