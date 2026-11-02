package com.example.campuscompanion;

import android.app.Application;
import android.content.Context;

import androidx.appcompat.app.AppCompatDelegate;

import com.google.android.material.color.DynamicColors;

public class CampusApp extends Application {

    public static final String PREFS = "settings";
    public static final String KEY_NIGHT_MODE = "night_mode";
    public static final String KEY_DYNAMIC = "dynamic_colors";

    @Override
    public void onCreate() {
        super.onCreate();
        // Apply the saved light/dark choice before any screen is drawn
        int mode = getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getInt(KEY_NIGHT_MODE, AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
        AppCompatDelegate.setDefaultNightMode(mode);

        // Optional: colours taken from the wallpaper (Android 12+)
        if (getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_DYNAMIC, false)) {
            DynamicColors.applyToActivitiesIfAvailable(this);
        }
    }
}
