package com.example.campuscompanion;

import android.app.Application;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;

import androidx.appcompat.app.AppCompatDelegate;

import com.example.campuscompanion.auth.UserSession;
import com.example.campuscompanion.ui.CompleteProfileActivity;
import com.example.campuscompanion.ui.LoginActivity;
import com.example.campuscompanion.ui.RegisterActivity;
import com.example.campuscompanion.ui.VerifyEmailActivity;
import com.google.android.material.color.DynamicColors;
import com.google.firebase.auth.FirebaseAuth;

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

        // One guard for every screen of the app: nobody gets past the sign-in screens without
        // an account, even through a widget, a notification, a shortcut or the recent-apps list.
        registerActivityLifecycleCallbacks(new ActivityLifecycleCallbacks() {
            @Override
            public void onActivityCreated(Activity activity, Bundle savedInstanceState) {
                if (!activity.getClass().getName().startsWith(getPackageName())) return;   // library screens
                if (activity instanceof LoginActivity || activity instanceof RegisterActivity
                        || activity instanceof VerifyEmailActivity
                        || activity instanceof CompleteProfileActivity) return;
                boolean signedIn = FirebaseAuth.getInstance().getCurrentUser() != null
                        && UserSession.hasProfile(activity);
                if (signedIn) return;
                Intent i = new Intent(activity, LoginActivity.class);
                i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                activity.startActivity(i);
                activity.finish();
            }

            @Override public void onActivityStarted(Activity a) { }
            @Override public void onActivityResumed(Activity a) { }
            @Override public void onActivityPaused(Activity a) { }
            @Override public void onActivityStopped(Activity a) { }
            @Override public void onActivitySaveInstanceState(Activity a, Bundle b) { }
            @Override public void onActivityDestroyed(Activity a) { }
        });
    }
}
