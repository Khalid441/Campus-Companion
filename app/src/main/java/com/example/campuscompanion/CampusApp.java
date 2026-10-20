package com.example.campuscompanion;

import android.app.Application;
import android.content.Context;

import androidx.appcompat.app.AppCompatDelegate;

public class CampusApp extends Application {

    public static final String PREFS = "settings";
    public static final String KEY_NIGHT_MODE = "night_mode";

    @Override
    public void onCreate() {
        super.onCreate();
        // Apply the saved light/dark choice before any screen is drawn
        int mode = getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getInt(KEY_NIGHT_MODE, AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
        AppCompatDelegate.setDefaultNightMode(mode);
    }
}
