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
    public static final int DEFAULT_GOAL = 5;
    /** 0 means "no goal". */
    public static final int[] GOAL_CHOICES = {0, 1, 2, 3, 5, 7, 10, 15, 20};

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
        long keepFrom = DateUtil.addDays(now, -90);
        Set<String> out = new HashSet<>();
        for (String s : p.getStringSet(KEY_REVISIONS, new HashSet<>())) {
            try {
                if (Long.parseLong(s) >= keepFrom) out.add(s);
            } catch (NumberFormatException ignored) { }
        }
        out.add(String.valueOf(now));
        p.edit().putStringSet(KEY_REVISIONS, out).apply();
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
