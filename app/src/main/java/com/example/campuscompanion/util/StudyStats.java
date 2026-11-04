package com.example.campuscompanion.util;

import android.content.Context;
import android.content.SharedPreferences;

import com.google.firebase.auth.FirebaseAuth;

import java.util.HashSet;
import java.util.Set;

/** Revision streak and the weekly revision goal. Kept on the phone, one file per account. */
public final class StudyStats {
    private static final String KEY_REVISIONS = "revisions";
    private static final String KEY_GOAL = "goal";
    private static final String KEY_TOTAL = "revisions_total";
    public static final int DEFAULT_GOAL = 5;
    /** 0 means "no goal". */
    public static final int[] GOAL_CHOICES = {0, 1, 2, 3, 5, 7, 10, 15, 20};   // (no longer used by the dialog)

    private StudyStats() { }

    private static SharedPreferences prefs(Context c) {
        String uid = FirebaseAuth.getInstance().getUid();
        return c.getApplicationContext().getSharedPreferences(
                "study_stats_" + (uid == null ? "guest" : uid), Context.MODE_PRIVATE);
    }

    /** Call every time the student taps "Revised". */
    public static void recordRevision(Context c) {
        SharedPreferences p = prefs(c);
        long now = System.currentTimeMillis();
        long keepFrom = DateUtil.addDays(now, -120);
        Set<String> out = new HashSet<>();
        for (String s : p.getStringSet(KEY_REVISIONS, new HashSet<>())) {
            try {
                if (Long.parseLong(s) >= keepFrom) out.add(s);
            } catch (NumberFormatException ignored) { }
        }
        out.add(String.valueOf(now));
        p.edit().putStringSet(KEY_REVISIONS, out)
                .putInt(KEY_TOTAL, p.getInt(KEY_TOTAL, 0) + 1)
                .apply();
    }

    private static Set<Long> days(Context c) {
        Set<Long> d = new HashSet<>();
        for (String s : prefs(c).getStringSet(KEY_REVISIONS, new HashSet<>())) {
            try {
                d.add(DateUtil.startOfDay(Long.parseLong(s)));
            } catch (NumberFormatException ignored) { }
        }
        return d;
    }

    /** Revisions done in the last 7 days (today included). */
    public static int revisionsThisWeek(Context c) {
        long from = DateUtil.addDays(System.currentTimeMillis(), -6);
        int n = 0;
        for (String s : prefs(c).getStringSet(KEY_REVISIONS, new HashSet<>())) {
            try {
                if (Long.parseLong(s) >= from) n++;
            } catch (NumberFormatException ignored) { }
        }
        return n;
    }

    /** Days in a row with at least one revision. Today may still be empty without breaking it. */
    public static int streak(Context c) {
        Set<Long> d = days(c);
        long day = DateUtil.startOfDay(System.currentTimeMillis());
        if (!d.contains(day)) day = DateUtil.addDays(day, -1);
        int n = 0;
        while (d.contains(day)) {
            n++;
            day = DateUtil.addDays(day, -1);
        }
        return n;
    }

    /** Revisions ever recorded on this phone for this account. */
    public static int total(Context c) {
        SharedPreferences p = prefs(c);
        // Accounts that revised before the counter existed: use what the recent list holds
        return Math.max(p.getInt(KEY_TOTAL, 0), p.getStringSet(KEY_REVISIONS, new HashSet<>()).size());
    }

    /** Revisions per day (key = start of the day) over the last 120 days. */
    public static java.util.Map<Long, Integer> revisionsPerDay(Context c) {
        java.util.Map<Long, Integer> map = new java.util.HashMap<>();
        for (String s : prefs(c).getStringSet(KEY_REVISIONS, new HashSet<>())) {
            try {
                long day = DateUtil.startOfDay(Long.parseLong(s));
                Integer n = map.get(day);
                map.put(day, n == null ? 1 : n + 1);
            } catch (NumberFormatException ignored) { }
        }
        return map;
    }

    public static int goal(Context c) {
        return prefs(c).getInt(KEY_GOAL, DEFAULT_GOAL);
    }

    public static void setGoal(Context c, int goal) {
        prefs(c).edit().putInt(KEY_GOAL, goal).apply();
    }

    /** "3 day streak  •  4 of 5 revisions this week" */
    public static String summary(Context c) {
        int streak = streak(c);
        int week = revisionsThisWeek(c);
        int goal = goal(c);
        String s = streak >= 2 ? streak + "-day streak  •  " : "";
        return goal == 0 ? s + week + (week == 1 ? " revision" : " revisions") + " this week"
                : s + week + " of " + goal + " revisions this week";
    }
}
