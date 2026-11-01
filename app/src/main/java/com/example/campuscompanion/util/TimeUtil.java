package com.example.campuscompanion.util;

import java.util.Locale;

public final class TimeUtil {

    private static final String[] DAYS = {"Sunday", "Monday", "Tuesday", "Wednesday",
            "Thursday", "Friday", "Saturday"};

    private TimeUtil() {}

    /** Calendar.SUNDAY..SATURDAY (1..7) to a name. */
    public static String dayName(int calendarDay) {
        return (calendarDay >= 1 && calendarDay <= 7) ? DAYS[calendarDay - 1] : "?";
    }

    public static String[] dayNames() {
        return DAYS.clone();
    }

    /** Minutes from midnight to "9:05 AM". */
    public static String fmt(int minutes) {
        int h = minutes / 60;
        int m = minutes % 60;
        String ampm = h >= 12 ? "PM" : "AM";
        int h12 = h % 12 == 0 ? 12 : h % 12;
        return String.format(Locale.getDefault(), "%d:%02d %s", h12, m, ampm);
    }

    /** "Today 4:00 PM", "Tomorrow 9:00 AM" or "Sat, 07 Nov  9:00 AM". */
    public static String friendly(long millis) {
        long now = System.currentTimeMillis();
        int mins = DateUtil.minutesOfDay(millis);
        if (DateUtil.sameDay(millis, now)) return "Today " + fmt(mins);
        if (DateUtil.sameDay(millis, DateUtil.addDays(now, 1))) return "Tomorrow " + fmt(mins);
        if (DateUtil.sameDay(millis, DateUtil.addDays(now, -1))) return "Yesterday " + fmt(mins);
        return new java.text.SimpleDateFormat("EEE, dd MMM", Locale.getDefault())
                .format(new java.util.Date(millis)) + "  " + fmt(mins);
    }
}
