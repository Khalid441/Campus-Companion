package com.example.campuscompanion.util;

/** Days left until an exam. */
public final class ExamUtil {
    private ExamUtil() { }

    /** Days from today to the exam day, or -1 when there is no exam or it has passed. */
    public static int daysLeft(long examDate) {
        if (examDate <= 0) return -1;
        long today = DateUtil.startOfDay(System.currentTimeMillis());
        long days = Math.round((DateUtil.startOfDay(examDate) - today) / 86_400_000.0);
        return days < 0 ? -1 : (int) days;
    }

    /** "today", "tomorrow" or "12 days left". */
    public static String label(int days) {
        if (days == 0) return "today";
        if (days == 1) return "tomorrow";
        return days + " days left";
    }
}
