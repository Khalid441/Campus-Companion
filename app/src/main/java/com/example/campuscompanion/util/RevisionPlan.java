package com.example.campuscompanion.util;

import com.example.campuscompanion.data.local.Topic;

/**
 * Spaced revision: a taught topic is revised 1, 3 and 7 days after the class.
 * If the student is late, the next revision is pushed to at least tomorrow.
 */
public final class RevisionPlan {
    public static final int[] OFFSET_DAYS = {1, 3, 7};
    public static final int STAGES = OFFSET_DAYS.length;

    private RevisionPlan() { }

    /** The day (start of day) for revision number {@code stage} (0-based), or 0 when finished. */
    public static long nextDue(long taughtAt, int stage, long now) {
        if (stage < 0 || stage >= STAGES) return 0;
        long planned = DateUtil.addDays(taughtAt, OFFSET_DAYS[stage]);
        long tomorrow = DateUtil.addDays(now, 1);
        // Never schedule in the past when the student revised late (stage > 0)
        return stage == 0 ? planned : Math.max(planned, tomorrow);
    }

    /** Call when a topic has just been taught (or marked taught) on {@code day}. */
    public static void startRevision(Topic t, long day, boolean remind, long now) {
        t.taught = true;
        t.taughtAt = day;
        t.revisedCount = 0;
        t.remind = remind;
        t.nextRevisionAt = remind ? nextDue(day, 0, now) : 0;
    }

    /** Call when the student taps "Revised". */
    public static void markRevised(Topic t, long now) {
        t.revisedCount = Math.min(STAGES, t.revisedCount + 1);
        t.nextRevisionAt = t.revisedCount >= STAGES ? 0 : nextDue(t.taughtAt, t.revisedCount, now);
    }

    /** True while the topic still has revisions waiting. */
    public static boolean isActive(Topic t) {
        return t.taught && t.remind && t.revisedCount < STAGES && t.nextRevisionAt > 0;
    }

    /** True when the next revision day is today or earlier. */
    public static boolean isDue(Topic t, long now) {
        return isActive(t) && t.nextRevisionAt <= DateUtil.endOfDay(now);
    }
}
