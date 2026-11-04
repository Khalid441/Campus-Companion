package com.example.campuscompanion.util;

import java.util.List;

/** Grade points and GPA maths on the standard 4.0 scale. Pure Java, no Android classes. */
public final class GpaCalc {
    private GpaCalc() { }

    public static final String[] LETTERS = {"A+", "A", "A-", "B+", "B", "B-", "C+", "C", "D", "F"};
    public static final double[] POINTS = {4.00, 3.75, 3.50, 3.25, 3.00, 2.75, 2.50, 2.25, 2.00, 0.00};
    public static final String[] MARKS = {"80-100", "75-79", "70-74", "65-69", "60-64", "55-59", "50-54", "45-49", "40-44", "below 40"};

    /** One course. grade = index into LETTERS, or -1 when not graded yet. */
    public static final class Course {
        public String name;
        public double credits;
        public int grade = -1;

        public Course(String name, double credits, int grade) {
            this.name = name;
            this.credits = credits;
            this.grade = grade;
        }
    }

    public static String label(int grade) {
        return grade < 0 ? "not graded yet" : LETTERS[grade] + " (" + String.format(java.util.Locale.US, "%.2f", POINTS[grade]) + ")";
    }

    /** Credits that already have a grade. */
    public static double gradedCredits(List<Course> list) {
        double c = 0;
        for (Course x : list) if (x.grade >= 0) c += x.credits;
        return c;
    }

    /** Sum of credits x grade points over graded courses. */
    public static double gradedPoints(List<Course> list) {
        double p = 0;
        for (Course x : list) if (x.grade >= 0) p += x.credits * POINTS[x.grade];
        return p;
    }

    public static double ungradedCredits(List<Course> list) {
        double c = 0;
        for (Course x : list) if (x.grade < 0) c += x.credits;
        return c;
    }

    /** Semester GPA over the graded courses, or -1 when nothing is graded. */
    public static double sgpa(List<Course> list) {
        double c = gradedCredits(list);
        return c <= 0 ? -1 : gradedPoints(list) / c;
    }

    /** Cumulative GPA after the graded courses are added to the earlier record, or -1 when no credits. */
    public static double cgpa(double prevCgpa, double prevCredits, List<Course> list) {
        double total = prevCredits + gradedCredits(list);
        return total <= 0 ? -1 : (prevCgpa * prevCredits + gradedPoints(list)) / total;
    }

    /**
     * Average grade point the ungraded courses must reach to finish on the target CGPA.
     * Returns NaN when there are no ungraded courses.
     */
    public static double requiredAverage(double prevCgpa, double prevCredits, List<Course> list, double target) {
        double remaining = ungradedCredits(list);
        if (remaining <= 0) return Double.NaN;
        double totalCredits = prevCredits + gradedCredits(list) + remaining;
        return (target * totalCredits - prevCgpa * prevCredits - gradedPoints(list)) / remaining;
    }

    /** Lowest letter grade whose points reach {@code needed}; -1 when even A+ is not enough. */
    public static int lowestLetterFor(double needed) {
        if (needed > POINTS[0] + 1e-9) return -1;
        int best = 0;
        for (int i = 0; i < POINTS.length; i++) if (POINTS[i] + 1e-9 >= needed) best = i;
        return best;
    }
}
