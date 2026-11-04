package com.example.campuscompanion.ui;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
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
import com.example.campuscompanion.util.DateUtil;
import com.example.campuscompanion.util.ExamPlan;
import com.example.campuscompanion.util.ExamUtil;
import com.example.campuscompanion.util.RevisionPlan;
import com.example.campuscompanion.util.StudyStats;
import com.google.android.material.snackbar.Snackbar;
import com.google.firebase.auth.FirebaseAuth;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Dynamic exam planner: sized to the study time the student has per day, shows revision progress and
 * workload per exam, lets topics be ticked off and added right here, and starts a focus session.
 */
public class ExamPlannerActivity extends AppCompatActivity {

    private static final SimpleDateFormat DAY = new SimpleDateFormat("EEE d MMM", Locale.getDefault());
    private static final int MINUTES_PER_TOPIC = 30;
    private static final int MIN_BUDGET = 30, MAX_BUDGET = 600, STEP = 30, DEFAULT_BUDGET = 120;

    private ActivityPlannerBinding b;
    private int budget = DEFAULT_BUDGET;

    private SharedPreferences prefs() {
        return getSharedPreferences("planner_" + FirebaseAuth.getInstance().getUid(), Context.MODE_PRIVATE);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        b = ActivityPlannerBinding.inflate(getLayoutInflater());
        setContentView(b.getRoot());
        try {
            budget = prefs().getInt("minutes", DEFAULT_BUDGET);
        } catch (Exception ignored) { }
        b.btnBudgetMinus.setOnClickListener(v -> changeBudget(-STEP));
        b.btnBudgetPlus.setOnClickListener(v -> changeBudget(STEP));
        showBudget();
    }

    private void changeBudget(int delta) {
        budget = Math.max(MIN_BUDGET, Math.min(MAX_BUDGET, budget + delta));
        try {
            prefs().edit().putInt("minutes", budget).apply();
        } catch (Exception ignored) { }
        showBudget();
        load();
    }

    private void showBudget() {
        int h = budget / 60, m = budget % 60;
        b.tvBudget.setText((h > 0 ? h + " h " : "") + (m > 0 ? m + " min" : "").trim());
    }

    @Override
    protected void onResume() {
        super.onResume();
        load();
    }

    private void load() {
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
        long today = DateUtil.startOfDay(now);
        int todaySessions = 0;
        int examsToday = 0;

        for (Exam e : exams) {
            ItemPlannerExamBinding r = ItemPlannerExamBinding.inflate(getLayoutInflater(), b.plannerList, false);
            int left = ExamUtil.daysLeft(e.examDate);
            r.tvPlanTitle.setText(e.subject + "  \u2022  " + ExamUtil.label(left));

            List<Topic> all = ExamPlan.allTopicsFor(e, courses, topics);
            List<Topic> need = ExamPlan.topicsFor(e, courses, topics);
            double progress = ExamPlan.progress(all);
            r.pbReady.setProgress((int) Math.round(progress * 100));
            r.pbReady.setContentDescription("Revision progress " + Math.round(progress * 100) + " percent");

            StringBuilder sub = new StringBuilder(DAY.format(new Date(e.examDate)));
            sub.append("  \u2022  ").append(all.size()).append(all.size() == 1 ? " topic" : " topics")
                    .append("  \u2022  ").append(Math.round(progress * 100)).append("% revised");
            AttendanceCourse c = CourseMatcher.find(courses, e.subject);
            if (c != null) {
                AttendanceMath.Result res = AttendanceStats.forCourse(c, counts);
                if (res.held > 0) sub.append("  \u2022  attendance ").append(res.percent).append("%");
            }
            r.tvPlanSub.setText(sub);

            if (left == 0) {
                examsToday++;
                r.tvPlanStatus.setText("Exam day. You've got this!");
                addLine(r, "Light skim only, then walk in calm.", null);
            } else if (need.isEmpty()) {
                if (all.isEmpty()) {
                    r.tvPlanStatus.setText("No topics yet");
                    addLine(r, "Add the topics you need to cover with \"+ Topic\", or note what was taught in a class.", null);
                } else {
                    r.tvPlanStatus.setText("\u2705 Everything revised. Keep it fresh with a light skim.");
                }
            } else {
                ExamPlan.Plan plan = ExamPlan.planBudget(e, need, now, budget, MINUTES_PER_TOPIC);
                if (plan.overloaded) {
                    r.tvPlanStatus.setText("\u26A0 Heavy: about " + plan.neededMinutesPerDay + " min a day needed, you set "
                            + budget + ". Add time or start today.");
                    r.tvPlanStatus.setTextColor(0xFFE53935);
                } else {
                    r.tvPlanStatus.setText("\u2713 On track, about " + plan.neededMinutesPerDay + " min a day");
                    r.tvPlanStatus.setTextColor(0xFF2E7D32);
                }
                for (ExamPlan.Day d : plan.days) {
                    boolean isToday = d.date == today;
                    if (d.items.isEmpty() && d.note == null) continue;
                    if (isToday) todaySessions += d.items.size();
                    addDay(r, d, isToday);
                }
            }

            r.btnPlanAdd.setOnClickListener(v -> addTopicDialog(e, courses));
            r.btnPlanFocus.setOnClickListener(v -> startActivity(new Intent(this, FocusActivity.class)));
            b.plannerList.addView(r.getRoot());
        }

        if (exams.isEmpty()) {
            b.tvTodaySummary.setText("Add an exam to get a plan.");
        } else if (todaySessions == 0) {
            b.tvTodaySummary.setText(examsToday > 0 ? "Exam today. Good luck!" : "Nothing planned today. Enjoy the break.");
        } else {
            int min = todaySessions * MINUTES_PER_TOPIC;
            b.tvTodaySummary.setText("Today: " + todaySessions + (todaySessions == 1 ? " topic" : " topics")
                    + "  \u2022  about " + (min >= 60 ? (min / 60) + " h " + (min % 60 > 0 ? (min % 60) + " min" : "") : min + " min"));
        }
    }

    private void addDay(ItemPlannerExamBinding r, ExamPlan.Day d, boolean isToday) {
        TextView head = new TextView(this);
        head.setTextSize(14);
        head.setTypeface(null, android.graphics.Typeface.BOLD);
        head.setPadding(0, dp(8), 0, dp(2));
        head.setText((isToday ? "Today  \u2022  " : "") + DAY.format(new Date(d.date)));
        r.planDays.addView(head);

        for (ExamPlan.Item it : d.items) {
            String label = it.topic.title + (it.again ? "  (second pass)" : "");
            if (isToday) {
                CheckBox cb = new CheckBox(this);
                cb.setText(label);
                cb.setTextSize(14);
                cb.setOnCheckedChangeListener((btn, on) -> {
                    if (on) markRevised(it.topic, btn);
                });
                r.planDays.addView(cb);
            } else {
                TextView t = new TextView(this);
                t.setTextSize(14);
                t.setPadding(dp(8), dp(2), 0, dp(2));
                t.setText("\u2022 " + label);
                r.planDays.addView(t);
            }
        }
        if (d.note != null) {
            TextView t = new TextView(this);
            t.setTextSize(13);
            t.setPadding(dp(8), dp(2), 0, dp(2));
            t.setText(d.note);
            r.planDays.addView(t);
        }
    }

    private void addLine(ItemPlannerExamBinding r, String text, String day) {
        TextView t = new TextView(this);
        t.setTextSize(14);
        t.setPadding(0, dp(4), 0, dp(4));
        t.setText(text);
        r.planDays.addView(t);
    }

    private void markRevised(Topic topic, View anchor) {
        final Topic before = topic.copy();
        Topic t = topic.copy();
        long now = System.currentTimeMillis();
        if (t.taught && t.taughtAt > 0) {
            RevisionPlan.markRevised(t, now);
        } else {
            t.revisedCount = Math.min(RevisionPlan.STAGES, t.revisedCount + 1);
            t.nextRevisionAt = 0;
        }
        final Topic after = t;
        Context app = getApplicationContext();
        AppDatabase.executor.execute(() -> {
            AppDatabase.get(app).topicDao().update(after);
            StudyStats.recordRevision(app);
            runOnUiThread(() -> {
                if (isFinishing() || isDestroyed()) return;
                load();
                Snackbar.make(b.getRoot(), "Marked as revised", Snackbar.LENGTH_LONG)
                        .setAction("Undo", v -> AppDatabase.executor.execute(() -> {
                            AppDatabase.get(app).topicDao().update(before);
                            runOnUiThread(this::load);
                        })).show();
            });
        });
    }

    private void addTopicDialog(Exam e, List<AttendanceCourse> courses) {
        EditText input = new EditText(this);
        input.setHint("Topic, e.g. Context-free grammars");
        input.setSingleLine(true);
        int pad = dp(20);
        android.widget.FrameLayout box = new android.widget.FrameLayout(this);
        box.setPadding(pad, dp(8), pad, 0);
        box.addView(input);
        new AlertDialog.Builder(this)
                .setTitle("Add topic to " + e.subject)
                .setView(box)
                .setPositiveButton("Add", (d, w) -> {
                    String title = input.getText().toString().trim();
                    if (title.isEmpty()) return;
                    AttendanceCourse c = CourseMatcher.find(courses, e.subject);
                    Topic t = new Topic();
                    t.courseId = c == null ? 0 : c.id;
                    t.subject = e.subject;
                    t.title = title;
                    t.taught = false;
                    Context app = getApplicationContext();
                    AppDatabase.executor.execute(() -> {
                        AppDatabase.get(app).topicDao().insert(t);
                        runOnUiThread(this::load);
                    });
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density);
    }
}
