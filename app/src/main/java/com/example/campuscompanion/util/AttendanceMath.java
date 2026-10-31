package com.example.campuscompanion.util;

/**
 * Attendance rules in one place (plain Java, no Android classes, easy to test).
 * Cancelled classes are not counted. percentage = present / (present + absent).
 */
public final class AttendanceMath {
    private AttendanceMath() { }

    public static final int SAFE = 0;    // comfortably above the student's minimum
    public static final int CLOSE = 1;   // above it, but within 5 points
    public static final int LOW = 2;     // below it

    public static final class Result {
        public int present, absent, cancelled, held;
        public int minPercent;
        public int percent;         // rounded, 0..100
        public int level;           // SAFE, CLOSE or LOW
        public String advice;
    }

    public static Result compute(int present, int absent, int cancelled,
                                 int minPercent, int totalPlanned) {
        Result r = new Result();
        r.present = present;
        r.absent = absent;
        r.cancelled = cancelled;
        r.held = present + absent;
        r.minPercent = minPercent;

        double pct = r.held == 0 ? 0 : 100.0 * present / r.held;
        r.percent = (int) Math.round(pct);

        if (r.held == 0) {
            r.level = SAFE;
            r.advice = "Mark your first class to start tracking.";
            return r;
        }
        r.level = pct < minPercent ? LOW : (pct < minPercent + 5 ? CLOSE : SAFE);

        if (totalPlanned > 0) {
            r.advice = advicePlanned(present, absent, minPercent, totalPlanned);
        } else {
            r.advice = adviceOpen(present, r.held, pct, minPercent);
        }
        return r;
    }

    /** The student gave the number of classes expected this term. */
    private static String advicePlanned(int present, int absent, int min, int total) {
        int held = present + absent;
        int remaining = Math.max(0, total - held);
        int needPresent = (min * total + 99) / 100;         // ceil(min% of total)
        int maxAbsent = total - needPresent;

        if (remaining == 0) {
            int finalPct = (int) Math.round(100.0 * present / Math.max(1, held));
            return finalPct >= min
                    ? "All planned classes are marked. Final attendance " + finalPct + "%."
                    : "All planned classes are marked. Final attendance " + finalPct
                      + "%, below your " + min + "%.";
        }
        if (absent > maxAbsent) {
            int best = (int) Math.round(100.0 * (present + remaining) / total);
            return "Even if you attend all " + remaining + " remaining classes you will reach "
                    + best + "%, below your " + min + "%.";
        }
        int canMiss = maxAbsent - absent;
        if (canMiss == 0) {
            return "Do not miss any more classes (" + remaining + " left) to reach " + min + "%.";
        }
        return "You can miss " + canMiss + " more " + (canMiss == 1 ? "class" : "classes") + " (" + remaining
                + " left) and still reach " + min + "%.";
    }

    /** No total given: what happens over the next classes. */
    private static String adviceOpen(int present, int held, double pct, int min) {
        if (pct >= min) {
            int n = (int) Math.floor(present * 100.0 / min - held + 1e-9);
            if (n <= 0) return "You are right at your limit. Do not miss the next class.";
            return "You can miss the next " + classes(n) + " and stay at or above " + min + "%.";
        }
        if (min >= 100) return "You cannot get back to 100% any more.";
        int m = (int) Math.ceil((min * held - 100.0 * present) / (100.0 - min) - 1e-9);
        return "Attend the next " + classes(m) + " in a row to get back to " + min + "%.";
    }

    private static String classes(int n) {
        return n == 1 ? "1 class" : n + " classes";
    }

    /**
     * Classes attended in a row, newest first. Cancelled classes are skipped,
     * an absence ends the streak. 0 = PRESENT, 1 = ABSENT, 2 = CANCELLED.
     */
    public static int streak(int[] statusNewestFirst) {
        int n = 0;
        for (int s : statusNewestFirst) {
            if (s == 2) continue;
            if (s == 0) n++;
            else break;
        }
        return n;
    }
}
