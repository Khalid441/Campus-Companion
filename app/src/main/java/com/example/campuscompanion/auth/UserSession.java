package com.example.campuscompanion.auth;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * Small local cache of the signed-in user's profile.
 *
 * NOTE: {@link #section(Context)} returns the class id ("groupId", e.g. KUET_CSE_105_B =
 * university code + section). It is the Firestore document id under /sections, so every
 * place that already used section() now automatically separates universities too.
 */
public final class UserSession {

    private static final String PREFS = "session";

    private UserSession() {}

    private static SharedPreferences p(Context c) {
        return c.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    /** "cse 105 b" / "CSE-105-B" -> "CSE_105_B" so everyone in a section gets the same id. */
    public static String normalizeSection(String raw) {
        return raw.trim().toUpperCase(java.util.Locale.ROOT)
                .replaceAll("[^A-Z0-9]+", "_")
                .replaceAll("^_+|_+$", "");
    }

    /** groupId = university + "_" + section, e.g. KUET_CSE_105_B. */
    public static String groupId(String university, String section) {
        return university + "_" + section;
    }

    public static void save(Context c, String name, String university, String section, String role) {
        p(c).edit()
                .putString("name", name == null ? "" : name)
                .putString("university", university)
                .putString("sectionName", section)
                .putString("section", groupId(university, section))
                .putString("role", role == null ? "student" : role)
                .apply();
    }

    public static String name(Context c) { return p(c).getString("name", ""); }
    /** The class id (groupId) used as the Firestore path. */
    public static String section(Context c) { return p(c).getString("section", null); }
    public static String university(Context c) { return p(c).getString("university", null); }
    public static String sectionName(Context c) { return p(c).getString("sectionName", null); }
    public static String role(Context c) { return p(c).getString("role", "student"); }
    public static boolean isCr(Context c) { return "cr".equalsIgnoreCase(role(c)); }

    /** True when a complete (university-aware) profile is cached. */
    public static boolean hasProfile(Context c) {
        return section(c) != null && university(c) != null;
    }

    /** "KUET - CSE 105 B" for the home screen. */
    public static String label(Context c) {
        String uni = university(c);
        String sec = sectionName(c);
        if (uni == null || sec == null) return "";
        return uni + " • " + sec.replace('_', ' ');
    }

    public static void clear(Context c) { p(c).edit().clear().apply(); }

    // ---- pending sign-up (university-email accounts wait for email verification) ----

    public static void savePending(Context c, String uid, String name, String university,
                                   String section, String email) {
        p(c).edit()
                .putString("pending_uid", uid)
                .putString("pending_name", name)
                .putString("pending_university", university)
                .putString("pending_section", section)
                .putString("pending_email", email)
                .apply();
    }

    public static boolean hasPending(Context c, String uid) {
        return uid != null && uid.equals(p(c).getString("pending_uid", null));
    }

    public static String pendingName(Context c) { return p(c).getString("pending_name", ""); }
    public static String pendingUniversity(Context c) { return p(c).getString("pending_university", null); }
    public static String pendingSection(Context c) { return p(c).getString("pending_section", null); }
    public static String pendingEmail(Context c) { return p(c).getString("pending_email", ""); }

    public static void clearPending(Context c) {
        p(c).edit()
                .remove("pending_uid").remove("pending_name").remove("pending_university")
                .remove("pending_section").remove("pending_email")
                .apply();
    }
}
