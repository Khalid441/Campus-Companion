package com.example.campuscompanion.util;

import android.content.Context;
import android.content.SharedPreferences;

import com.example.campuscompanion.data.local.AppDatabase;
import com.example.campuscompanion.data.local.AttendanceRecord;
import com.example.campuscompanion.data.local.Deadline;
import com.example.campuscompanion.data.local.Topic;
import com.google.firebase.auth.FirebaseAuth;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/** Works out XP, level and badges from what the student has done. Call off the main thread. */
public final class Gamification {
    private Gamification() { }

    public static final class Badge {
        public final String id, emoji, name, description;
        Badge(String id, String emoji, String name, String description) {
            this.id = id;
            this.emoji = emoji;
            this.name = name;
            this.description = description;
        }
    }

    public static final class Snapshot {
        public int present, attendanceStreak, revisions, revisionStreak, focusMinutes, deadlinesDone, mastered;
        public int xp, level;
        public int xpThisLevel, xpNextLevel;
        public final List<String> unlocked = new ArrayList<>();      // badge ids unlocked (ever)
        public final List<Badge> newlyUnlocked = new ArrayList<>();  // unlocked by this call
    }

    public static final Badge[] BADGES = {
            new Badge("first_class", "\uD83C\uDF92", "First class", "Mark your first class as attended"),
            new Badge("att5", "\uD83D\uDD25", "5 in a row", "Attend 5 classes in a row"),
            new Badge("att10", "\uD83D\uDD25", "10 in a row", "Attend 10 classes in a row"),
            new Badge("att20", "\uD83C\uDFC6", "20 in a row", "Attend 20 classes in a row"),
            new Badge("rev3", "\uD83D\uDD01", "3-day streak", "Revise 3 days in a row"),
            new Badge("rev7", "\uD83D\uDCDA", "Week warrior", "Revise 7 days in a row"),
            new Badge("rev30", "\uD83E\uDDE0", "Memory master", "Revise 30 days in a row"),
            new Badge("focus1", "\u23F1\uFE0F", "First hour", "Focus for 1 hour in total"),
            new Badge("focus10", "\uD83C\uDFAF", "10 hours", "Focus for 10 hours in total"),
            new Badge("focus50", "\uD83D\uDE80", "50 hours", "Focus for 50 hours in total"),
            new Badge("dl5", "\u2705", "Finisher", "Complete 5 deadlines"),
            new Badge("dl20", "\uD83C\uDFC5", "Deadline crusher", "Complete 20 deadlines"),
            new Badge("master10", "\uD83C\uDF93", "Topic master", "Revise 10 topics three times each"),
    };

    private static SharedPreferences prefs(Context c) {
        String uid = FirebaseAuth.getInstance().getUid();
        return c.getApplicationContext().getSharedPreferences(
                "gamify_" + (uid == null ? "guest" : uid), Context.MODE_PRIVATE);
    }

    private static boolean reached(String id, Snapshot s) {
        switch (id) {
            case "first_class": return s.present >= 1;
            case "att5": return s.attendanceStreak >= 5;
            case "att10": return s.attendanceStreak >= 10;
            case "att20": return s.attendanceStreak >= 20;
            case "rev3": return s.revisionStreak >= 3;
            case "rev7": return s.revisionStreak >= 7;
            case "rev30": return s.revisionStreak >= 30;
            case "focus1": return s.focusMinutes >= 60;
            case "focus10": return s.focusMinutes >= 600;
            case "focus50": return s.focusMinutes >= 3000;
            case "dl5": return s.deadlinesDone >= 5;
            case "dl20": return s.deadlinesDone >= 20;
            case "master10": return s.mastered >= 10;
            default: return false;
        }
    }

    /** Reads the database and counters, saves any newly earned badges, and returns the result. */
    public static Snapshot compute(Context context) {
        Context app = context.getApplicationContext();
        AppDatabase db = AppDatabase.get(app);
        Snapshot s = new Snapshot();

        List<AttendanceRecord> records = db.attendanceDao().getRecordsNow();
        for (AttendanceRecord r : records) if (r.status == AttendanceRecord.PRESENT) s.present++;
        Collections.sort(records, new Comparator<AttendanceRecord>() {
            @Override
            public int compare(AttendanceRecord a, AttendanceRecord b) {
                if (a.date != b.date) return Long.compare(b.date, a.date);
                return Integer.compare(b.id, a.id);
            }
        });
        s.attendanceStreak = AttendanceStats.streak(records);

        s.revisions = StudyStats.total(app);
        s.revisionStreak = StudyStats.streak(app);
        s.focusMinutes = FocusStats.total(app);
        for (Deadline d : db.deadlineDao().getAllNow()) if (d.done) s.deadlinesDone++;
        for (Topic t : db.topicDao().getAllNow()) if (t.revisedCount >= RevisionPlan.STAGES) s.mastered++;

        s.xp = LevelMath.xp(s.present, s.revisions, s.focusMinutes, s.deadlinesDone, s.mastered);
        s.level = LevelMath.levelFor(s.xp);
        s.xpThisLevel = LevelMath.xpForLevel(s.level);
        s.xpNextLevel = LevelMath.xpForLevel(s.level + 1);

        // A badge, once earned, stays earned even if a streak later breaks
        SharedPreferences p = prefs(app);
        SharedPreferences.Editor e = p.edit();
        for (Badge b : BADGES) {
            boolean had = p.getLong("badge_" + b.id, 0) > 0;
            if (had) {
                s.unlocked.add(b.id);
            } else if (reached(b.id, s)) {
                e.putLong("badge_" + b.id, System.currentTimeMillis());
                s.unlocked.add(b.id);
                s.newlyUnlocked.add(b);
            }
        }
        e.apply();
        return s;
    }

    // ---- leaderboard opt-in (kept on the phone; the data itself lives in Firestore) ----

    public static boolean leaderboardOn(Context c) {
        return prefs(c).getBoolean("leaderboard", false);
    }

    public static void setLeaderboard(Context c, boolean on) {
        prefs(c).edit().putBoolean("leaderboard", on).apply();
    }

    public static int lastUploadedXp(Context c) {
        return prefs(c).getInt("lb_xp", -1);
    }

    public static void setLastUploadedXp(Context c, int xp) {
        prefs(c).edit().putInt("lb_xp", xp).apply();
    }
}
