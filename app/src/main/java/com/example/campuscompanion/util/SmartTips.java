package com.example.campuscompanion.util;

import android.content.Context;

import com.example.campuscompanion.data.local.AppDatabase;
import com.example.campuscompanion.data.local.AttendanceCourse;
import com.example.campuscompanion.data.local.Deadline;
import com.example.campuscompanion.data.local.Exam;
import com.example.campuscompanion.data.local.Topic;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** One-line suggestions for the Home screen, most urgent first. Call off the main thread. */
public final class SmartTips {
    private SmartTips() { }

    public static List<String> build(Context context) {
        Context app = context.getApplicationContext();
        AppDatabase db = AppDatabase.get(app);
        long now = System.currentTimeMillis();
        List<String> tips = new ArrayList<>();

        List<AttendanceCourse> courses = db.attendanceDao().getCoursesNow();
        List<Topic> topics = db.topicDao().getAllNow();

        // 1. A close exam with topics that are not fully revised
        Exam nearest = null;
        int nearestDays = Integer.MAX_VALUE;
        for (Exam e : db.examDao().getAllNow()) {
            int d = ExamUtil.daysLeft(e.examDate);
            if (d >= 0 && d < nearestDays) {
                nearest = e;
                nearestDays = d;
            }
        }
        if (nearest != null && nearestDays <= 10) {
            int open = ExamPlan.topicsFor(nearest, courses, topics).size();
            String when = nearestDays == 0 ? "today" : nearestDays == 1 ? "tomorrow" : "in " + nearestDays + " days";
            if (open > 0) {
                tips.add(nearest.subject + " exam " + when + ": " + open
                        + (open == 1 ? " topic" : " topics") + " still to revise. Open the planner.");
            } else {
                tips.add(nearest.subject + " exam " + when + ". Add its topics to get a revision plan.");
            }
        }

        // 2. Deadline within a day
        Deadline soon = null;
        for (Deadline d : db.deadlineDao().getPending()) {
            if (d.dueAt >= now && d.dueAt - now <= 24L * 3600_000 && (soon == null || d.dueAt < soon.dueAt)) soon = d;
        }
        if (soon != null) {
            long hours = Math.max(1, (soon.dueAt - now) / 3600_000);
            tips.add("\"" + soon.title + "\" is due in " + hours + (hours == 1 ? " hour." : " hours."));
        }

        // 3. Revision waiting today
        int due = 0;
        for (Topic t : topics) if (RevisionPlan.isDue(t, now)) due++;
        if (due > 0) {
            tips.add(due + (due == 1 ? " topic is" : " topics are") + " due for revision today.");
        }

        // 4. Attendance: the course that needs attention most
        Map<Integer, int[]> counts = AttendanceStats.counts(db.attendanceDao().getRecordsNow());
        AttendanceCourse worst = null;
        AttendanceMath.Result worstR = null;
        for (AttendanceCourse c : courses) {
            AttendanceMath.Result r = AttendanceStats.forCourse(c, counts);
            if (r.held == 0) continue;
            if (worstR == null || r.percent - r.minPercent < worstR.percent - worstR.minPercent) {
                worst = c;
                worstR = r;
            }
        }
        if (worst != null) {
            tips.add(worst.title() + " is at " + worstR.percent + "% (min " + worstR.minPercent + "%). " + worstR.advice);
        }

        // 5. Focus time today
        int focus = FocusStats.today(app);
        if (focus > 0) tips.add("You focused for " + FocusStats.format(focus) + " today. Nice work!");

        // 6. Nothing special
        if (tips.isEmpty()) {
            tips.add(courses.isEmpty()
                    ? "Add your courses in the Attendance tab to start tracking classes."
                    : "All clear. Mark today's classes to keep your attendance up to date.");
        }
        return tips;
    }
}
