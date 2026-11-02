package com.example.campuscompanion.ui;

import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.example.campuscompanion.data.local.AppDatabase;
import com.example.campuscompanion.data.local.AttendanceCourse;
import com.example.campuscompanion.data.local.Exam;
import com.example.campuscompanion.data.local.Topic;
import com.example.campuscompanion.databinding.ActivityPlannerBinding;
import com.example.campuscompanion.databinding.ItemPlannerExamBinding;
import com.example.campuscompanion.util.AttendanceMath;
import com.example.campuscompanion.util.AttendanceStats;
import com.example.campuscompanion.util.CourseMatcher;
import com.example.campuscompanion.util.ExamPlan;
import com.example.campuscompanion.util.ExamUtil;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Day-by-day revision plan for every upcoming exam. */
public class ExamPlannerActivity extends AppCompatActivity {

    private static final SimpleDateFormat DAY = new SimpleDateFormat("EEE d MMM", Locale.getDefault());

    private ActivityPlannerBinding b;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        b = ActivityPlannerBinding.inflate(getLayoutInflater());
        setContentView(b.getRoot());
    }

    @Override
    protected void onResume() {
        super.onResume();
        AppDatabase.executor.execute(() -> {
            AppDatabase db = AppDatabase.get(getApplicationContext());
            List<AttendanceCourse> courses = db.attendanceDao().getCoursesNow();
            List<Topic> topics = db.topicDao().getAllNow();
            Map<Integer, int[]> counts = AttendanceStats.counts(db.attendanceDao().getRecordsNow());
            List<Exam> exams = new ArrayList<>();
            for (Exam e : db.examDao().getAllNow()) if (ExamUtil.daysLeft(e.examDate) >= 0) exams.add(e);
            runOnUiThread(() -> {
                if (isFinishing() || isDestroyed()) return;
                render(exams, courses, topics, counts);
            });
        });
    }

    private void render(List<Exam> exams, List<AttendanceCourse> courses, List<Topic> topics,
                        Map<Integer, int[]> counts) {
        b.plannerList.removeAllViews();
        b.tvPlannerEmpty.setVisibility(exams.isEmpty() ? View.VISIBLE : View.GONE);
        long now = System.currentTimeMillis();

        for (Exam e : exams) {
            ItemPlannerExamBinding r = ItemPlannerExamBinding.inflate(getLayoutInflater(), b.plannerList, false);
            int left = ExamUtil.daysLeft(e.examDate);
            r.tvPlanTitle.setText(e.subject + "  \u2022  " + ExamUtil.label(left));

            List<Topic> need = ExamPlan.topicsFor(e, courses, topics);
            StringBuilder sub = new StringBuilder(DAY.format(new Date(e.examDate)));
            sub.append("  \u2022  ").append(need.size()).append(need.size() == 1 ? " topic" : " topics").append(" to revise");
            AttendanceCourse c = CourseMatcher.find(courses, e.subject);
            if (c != null) {
                AttendanceMath.Result res = AttendanceStats.forCourse(c, counts);
                if (res.held > 0) sub.append("  \u2022  attendance ").append(res.percent).append("%");
            }
            r.tvPlanSub.setText(sub);

            if (left == 0) {
                addLine(r, "Today is the exam. Good luck!", null);
            } else if (need.isEmpty()) {
                addLine(r, "No topics noted for this subject yet. Tap Present in a class and note what was taught, "
                        + "or add topics in Revision.", null);
            } else {
                for (ExamPlan.Day d : ExamPlan.plan(e, need, now)) {
                    if (d.topics.isEmpty() && d.note == null) continue;
                    String text = d.topics.isEmpty() ? d.note : android.text.TextUtils.join(", ", d.topics);
                    addLine(r, text, DAY.format(new Date(d.date)));
                }
            }
            b.plannerList.addView(r.getRoot());
        }
    }

    private void addLine(ItemPlannerExamBinding r, String text, String day) {
        TextView t = new TextView(this);
        t.setTextSize(14);
        t.setPadding(0, dp(4), 0, dp(4));
        t.setText(day == null ? text : day + ":  " + text);
        if (day != null) {
            android.text.SpannableString sp = new android.text.SpannableString(t.getText());
            sp.setSpan(new android.text.style.StyleSpan(android.graphics.Typeface.BOLD), 0, day.length() + 1,
                    android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            t.setText(sp);
        }
        r.planDays.addView(t);
    }

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density);
    }
}
