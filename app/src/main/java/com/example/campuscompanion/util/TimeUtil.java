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
}
