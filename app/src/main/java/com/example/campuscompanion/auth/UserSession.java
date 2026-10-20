package com.example.campuscompanion.auth;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.Locale;

/** Small local cache of the signed-in user's profile (name, section, role). */
public final class UserSession {

    private static final String PREFS = "session";

    private UserSession() {}

    private static SharedPreferences p(Context c) {
        return c.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public static void save(Context c, String name, String section, String role) {
        p(c).edit()
                .putString("name", name == null ? "" : name)
                .putString("section", section)
                .putString("role", role == null ? "student" : role)
                .apply();
    }

    public static String name(Context c) { return p(c).getString("name", ""); }
    public static String section(Context c) { return p(c).getString("section", null); }
    public static String role(Context c) { return p(c).getString("role", "student"); }
    public static boolean isCr(Context c) { return "cr".equalsIgnoreCase(role(c)); }
    public static void clear(Context c) { p(c).edit().clear().apply(); }

    /** "cse 2 a" / "CSE-2-A" -> "CSE_2_A" so everyone in a section gets the same id. */
    public static String normalizeSection(String raw) {
        return raw.trim().toUpperCase(Locale.ROOT)
                .replaceAll("[^A-Z0-9]+", "_")
                .replaceAll("^_+|_+$", "");
    }
}
