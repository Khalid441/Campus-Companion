package com.example.campuscompanion.util;

/** XP, levels and anonymous names. Pure Java. */
public final class LevelMath {
    private LevelMath() { }

    private static final String[] TITLES = {"Newcomer", "Learner", "Scholar", "Achiever", "Expert", "Mentor", "Master", "Legend"};
    private static final String[] ADJECTIVES = {"Brave", "Calm", "Swift", "Bright", "Clever", "Bold", "Quiet", "Lucky",
            "Sharp", "Steady", "Eager", "Noble", "Witty", "Keen", "Daring", "Gentle"};
    private static final String[] ANIMALS = {"Falcon", "Tiger", "Otter", "Panda", "Fox", "Eagle", "Dolphin", "Owl",
            "Lynx", "Heron", "Wolf", "Koala", "Raven", "Bison", "Gecko", "Hawk"};

    /** XP needed to reach level n (level 1 starts at 0): 0, 100, 300, 600, 1000 ... */
    public static int xpForLevel(int n) {
        return n <= 1 ? 0 : 100 * n * (n - 1) / 2;
    }

    public static int levelFor(int xp) {
        int n = 1;
        while (xp >= xpForLevel(n + 1)) n++;
        return n;
    }

    public static String title(int level) {
        return TITLES[Math.min(level, TITLES.length) - 1];
    }

    /** XP: 5 per class attended, 10 per revision, 1 per focus minute, 8 per deadline done, 15 per topic mastered. */
    public static int xp(int present, int revisions, int focusMinutes, int deadlinesDone, int mastered) {
        return present * 5 + revisions * 10 + focusMinutes + deadlinesDone * 8 + mastered * 15;
    }

    /** A friendly name that hides who it is, the same every time for the same id. */
    public static String alias(String uid) {
        int h = uid == null ? 0 : uid.hashCode();
        int a = Math.abs(h % ADJECTIVES.length);
        int b = Math.abs((h / 31) % ANIMALS.length);
        return ADJECTIVES[a] + " " + ANIMALS[b];
    }
}
