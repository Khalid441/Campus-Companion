package com.example.campuscompanion.util;

import java.util.Calendar;

/** Small calendar helpers (all in the phone's local time). */
public final class DateUtil {
    private DateUtil() { }

    public static long startOfDay(long millis) {
        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(millis);
        c.set(Calendar.HOUR_OF_DAY, 0);
        c.set(Calendar.MINUTE, 0);
        c.set(Calendar.SECOND, 0);
        c.set(Calendar.MILLISECOND, 0);
        return c.getTimeInMillis();
    }

    /** Last millisecond of that day. */
    public static long endOfDay(long millis) {
        return addDays(startOfDay(millis), 1) - 1;
    }

    /** Start of the day that is {@code days} after (or before, if negative) the given day. */
    public static long addDays(long millis, int days) {
        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(startOfDay(millis));
        c.add(Calendar.DAY_OF_YEAR, days);
        return c.getTimeInMillis();
    }

    public static boolean sameDay(long a, long b) {
        return startOfDay(a) == startOfDay(b);
    }

    /** Minutes since midnight of the given moment. */
    public static int minutesOfDay(long millis) {
        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(millis);
        return c.get(Calendar.HOUR_OF_DAY) * 60 + c.get(Calendar.MINUTE);
    }

    /** Calendar.SUNDAY..SATURDAY (1..7) of the given moment. */
    public static int dayOfWeek(long millis) {
        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(millis);
        return c.get(Calendar.DAY_OF_WEEK);
    }
}
