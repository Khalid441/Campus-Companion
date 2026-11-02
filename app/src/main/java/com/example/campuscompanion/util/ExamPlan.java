package com.example.campuscompanion.util;

import com.example.campuscompanion.data.local.AttendanceCourse;
import com.example.campuscompanion.data.local.Exam;
import com.example.campuscompanion.data.local.Topic;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/** Spreads the topics that are not fully revised over the days left before an exam. */
public final class ExamPlan {
    private ExamPlan() { }

    public static final class Day {
        public long date;
        public final List<String> topics = new ArrayList<>();
        public String note;
    }

    /** Topics of this exam's subject that still need revision, least revised first. */
    public static List<Topic> topicsFor(Exam exam, List<AttendanceCourse> courses, List<Topic> all) {
        AttendanceCourse course = CourseMatcher.find(courses, exam.subject);
        List<Topic> out = new ArrayList<>();
        for (Topic t : all) {
            boolean mine = (course != null && t.courseId == course.id)
                    || (t.subject != null && !t.subject.isEmpty() && CourseMatcher.same(t.subject, exam.subject));
            if (mine && t.revisedCount < RevisionPlan.STAGES) out.add(t);
        }
        Collections.sort(out, new Comparator<Topic>() {
            @Override
            public int compare(Topic a, Topic b) {
                if (a.revisedCount != b.revisedCount) return Integer.compare(a.revisedCount, b.revisedCount);
                return Long.compare(a.taughtAt, b.taughtAt);
            }
        });
        return out;
    }

    /** One entry per day from today to the day before the exam (at least one day). */
    public static List<Day> plan(Exam exam, List<Topic> topics, long now) {
        List<Day> days = new ArrayList<>();
        int left = ExamUtil.daysLeft(exam.examDate);
        if (left < 0) return days;

        int span = Math.max(1, left);              // revision days before exam day
        long today = DateUtil.startOfDay(now);
        for (int i = 0; i < span; i++) {
            Day d = new Day();
            d.date = DateUtil.addDays(today, i);
            days.add(d);
        }
        if (topics.isEmpty()) return days;

        // Leave the last day for a light look-over when there is time
        int usable = span >= 3 ? span - 1 : span;
        int perDay = (int) Math.ceil(topics.size() / (double) usable);
        int idx = 0;
        for (int i = 0; i < usable && idx < topics.size(); i++) {
            for (int k = 0; k < perDay && idx < topics.size(); k++) {
                days.get(i).topics.add(topics.get(idx++).title);
            }
        }
        if (span >= 3) days.get(span - 1).note = "Light review of weak spots, then rest";
        return days;
    }
}
