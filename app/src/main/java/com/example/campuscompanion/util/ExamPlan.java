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
        /** Topic sessions for this day (budget-based plan). */
        public final List<Item> items = new ArrayList<>();
    }

    /** One study session: a topic, possibly a second pass over it. */
    public static final class Item {
        public final Topic topic;
        public final boolean again;
        public Item(Topic topic, boolean again) { this.topic = topic; this.again = again; }
    }

    public static final class Plan {
        public final List<Day> days = new ArrayList<>();
        public int neededMinutesPerDay;
        public boolean overloaded;
    }

    /** Every topic of this exam's subject, including fully revised ones. */
    public static List<Topic> allTopicsFor(Exam exam, List<AttendanceCourse> courses, List<Topic> all) {
        AttendanceCourse course = CourseMatcher.find(courses, exam.subject);
        List<Topic> out = new ArrayList<>();
        for (Topic t : all) {
            boolean mine = (course != null && t.courseId == course.id)
                    || (t.subject != null && !t.subject.isEmpty() && CourseMatcher.same(t.subject, exam.subject));
            if (mine) out.add(t);
        }
        return out;
    }

    /** Share of revision done over all topics, 0..1 (1 when there are no topics to do). */
    public static double progress(List<Topic> allTopics) {
        if (allTopics.isEmpty()) return 0;
        double sum = 0;
        for (Topic t : allTopics) sum += Math.min(RevisionPlan.STAGES, t.revisedCount) / (double) RevisionPlan.STAGES;
        return sum / allTopics.size();
    }

    /**
     * Plan that respects how much time the student has per day. Weakest topics come first, the work is
     * spread evenly, the last day (when there are 3+) is a light review, and any spare time is used
     * for a second pass over the weakest topics.
     */
    public static Plan planBudget(Exam exam, List<Topic> topics, long now, int minutesPerDay, int minutesPerTopic) {
        Plan plan = new Plan();
        int left = ExamUtil.daysLeft(exam.examDate);
        if (left < 0) return plan;
        int span = Math.max(1, left);
        long today = DateUtil.startOfDay(now);
        for (int i = 0; i < span; i++) {
            Day d = new Day();
            d.date = DateUtil.addDays(today, i);
            plan.days.add(d);
        }
        if (span >= 3) plan.days.get(span - 1).note = "Light review of weak spots, then rest";
        int n = topics.size();
        if (n == 0) return plan;

        int usable = span >= 3 ? span - 1 : span;
        int cap = Math.max(1, minutesPerDay / Math.max(1, minutesPerTopic));
        int need = (int) Math.ceil(n / (double) usable);
        plan.neededMinutesPerDay = need * minutesPerTopic;
        plan.overloaded = need > cap;

        // First pass: even spread, earlier days take the remainder
        int base = n / usable, extra = n % usable, idx = 0;
        int[] firstDay = new int[n];
        for (int i = 0; i < usable; i++) {
            int count = base + (i < extra ? 1 : 0);
            for (int k = 0; k < count; k++) {
                firstDay[idx] = i;
                plan.days.get(i).items.add(new Item(topics.get(idx++), false));
            }
        }
        // Second pass with spare time: weakest topics first, on a later day with room
        for (int t = 0; t < n; t++) {
            for (int j = firstDay[t] + 1; j < usable; j++) {
                Day d = plan.days.get(j);
                if (d.items.size() >= cap) continue;
                d.items.add(new Item(topics.get(t), true));
                break;
            }
        }
        return plan;
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
