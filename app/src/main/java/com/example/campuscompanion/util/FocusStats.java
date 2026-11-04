package com.example.campuscompanion.util;

import android.content.Context;
import android.content.SharedPreferences;

import com.google.firebase.auth.FirebaseAuth;

/** Minutes spent in the focus timer, per day. Kept on the phone, one file per account. */
public final class FocusStats {
    private FocusStats() { }

    private static SharedPreferences prefs(Context c) {
        String uid = FirebaseAuth.getInstance().getUid();
        return c.getApplicationContext().getSharedPreferences(
                "focus_stats_" + (uid == null ? "guest" : uid), Context.MODE_PRIVATE);
    }

    private static String dayKey(long millis) {
        return "day_" + DateUtil.startOfDay(millis);
    }

    /** Call when a focus session finishes. */
    public static void add(Context c, int minutes, String course) {
        if (minutes <= 0) return;
        SharedPreferences p = prefs(c);
        String key = dayKey(System.currentTimeMillis());
        SharedPreferences.Editor e = p.edit().putInt(key, p.getInt(key, 0) + minutes);
        if (course != null && !course.isEmpty()) {
            String ck = "course_" + course;
            e.putInt(ck, p.getInt(ck, 0) + minutes);
        }
        e.apply();
    }

    /** All-time focus minutes for one course name. */
    public static int forCourse(Context c, String course) {
        return prefs(c).getInt("course_" + course, 0);
    }

    public static int today(Context c) {
        return prefs(c).getInt(dayKey(System.currentTimeMillis()), 0);
    }

    /** Last 7 days, today included. */
    public static int week(Context c) {
        SharedPreferences p = prefs(c);
        long now = System.currentTimeMillis();
        int total = 0;
        for (int i = 0; i < 7; i++) total += p.getInt(dayKey(DateUtil.addDays(now, -i)), 0);
        return total;
    }

    /** Minutes focused on one day (pass the start of that day). */
    public static int forDay(Context c, long dayStart) {
        return prefs(c).getInt("day_" + DateUtil.startOfDay(dayStart), 0);
    }

    /** All-time focus minutes. */
    public static int total(Context c) {
        int total = 0;
        for (java.util.Map.Entry<String, ?> e : prefs(c).getAll().entrySet()) {
            if (e.getKey().startsWith("day_") && e.getValue() instanceof Integer) total += (Integer) e.getValue();
        }
        return total;
    }

    public static String format(int minutes) {
        if (minutes < 60) return minutes + " min";
        int h = minutes / 60, m = minutes % 60;
        return m == 0 ? h + " h" : h + " h " + m + " min";
    }
}
