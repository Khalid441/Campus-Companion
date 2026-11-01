package com.example.campuscompanion.ui;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.widget.CompoundButton;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.content.ContextCompat;

import com.example.campuscompanion.CampusApp;
import com.example.campuscompanion.auth.UserSession;
import com.example.campuscompanion.data.local.AppDatabase;
import com.example.campuscompanion.data.local.Deadline;
import com.example.campuscompanion.data.local.StudySession;
import com.example.campuscompanion.databinding.ActivitySettingsBinding;
import com.example.campuscompanion.provider.DeadlineProvider;
import com.example.campuscompanion.reminder.ReminderScheduler;
import com.example.campuscompanion.reminder.RevisionScheduler;
import com.example.campuscompanion.reminder.StudyReminderScheduler;
import com.example.campuscompanion.service.NextDeadlineService;
import com.example.campuscompanion.sync.NoticeManager;
import com.example.campuscompanion.sync.SyncManager;
import com.example.campuscompanion.widget.NextDeadlineWidget;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

/** Profile, switches and log out. (These used to be at the bottom of the Home screen.) */
public class SettingsActivity extends AppCompatActivity {

    private ActivitySettingsBinding b;
    private SharedPreferences prefs;

    private final CompoundButton.OnCheckedChangeListener trackerListener = (button, on) -> {
        if (on) {
            Intent i = new Intent(this, NextDeadlineService.class)
                    .setAction(NextDeadlineService.ACTION_START);
            ContextCompat.startForegroundService(this, i);
        } else {
            stopService(new Intent(this, NextDeadlineService.class));
        }
        prefs.edit().putBoolean(NextDeadlineService.KEY_TRACKER_ON, on).apply();
    };

    private final CompoundButton.OnCheckedChangeListener darkListener = (button, on) -> {
        int mode = on ? AppCompatDelegate.MODE_NIGHT_YES : AppCompatDelegate.MODE_NIGHT_NO;
        getSharedPreferences(CampusApp.PREFS, Context.MODE_PRIVATE)
                .edit().putInt(CampusApp.KEY_NIGHT_MODE, mode).apply();
        AppCompatDelegate.setDefaultNightMode(mode); // recreates the screen with the new theme
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        b = ActivitySettingsBinding.inflate(getLayoutInflater());
        setContentView(b.getRoot());
        prefs = getSharedPreferences(NextDeadlineService.PREFS, Context.MODE_PRIVATE);

        String name = UserSession.name(this);
        b.tvProfileName.setText(name.isEmpty() ? "Student" : name);
        b.tvProfileSection.setText(UserSession.label(this)
                + (UserSession.isCr(this) ? "  •  Class representative" : ""));
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        String email = user == null ? null : user.getEmail();
        b.tvProfileEmail.setVisibility(email == null || email.isEmpty() ? View.GONE : View.VISIBLE);
        b.tvProfileEmail.setText(email);

        b.btnExactAlarm.setOnClickListener(v -> {
            if (Build.VERSION.SDK_INT >= 31) {
                startActivity(new Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                        Uri.parse("package:" + getPackageName())));
            }
        });
        b.btnLogout.setOnClickListener(v -> logout());
    }

    @Override
    protected void onResume() {
        super.onResume();

        b.swTracker.setOnCheckedChangeListener(null);
        b.swTracker.setChecked(prefs.getBoolean(NextDeadlineService.KEY_TRACKER_ON, false));
        b.swTracker.setOnCheckedChangeListener(trackerListener);

        b.swRevision.setOnCheckedChangeListener(null);
        b.swRevision.setChecked(RevisionScheduler.isEnabled(this));
        b.swRevision.setOnCheckedChangeListener((button, on) -> RevisionScheduler.setEnabled(this, on));

        boolean isDark = (getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK)
                == Configuration.UI_MODE_NIGHT_YES;
        b.swDark.setOnCheckedChangeListener(null);
        b.swDark.setChecked(isDark);
        b.swDark.setOnCheckedChangeListener(darkListener);

        b.btnExactAlarm.setVisibility(ReminderScheduler.canScheduleExact(this) ? View.GONE : View.VISIBLE);
        showProviderCount();
    }

    /** ContentProvider demo: count deadlines through a content:// URI. */
    private void showProviderCount() {
        AppDatabase.executor.execute(() -> {
            int count = 0;
            try (Cursor c = getContentResolver().query(
                    DeadlineProvider.CONTENT_URI, new String[]{"id"}, null, null, null)) {
                if (c != null) count = c.getCount();
            }
            final int n = count;
            runOnUiThread(() -> {
                if (!isFinishing() && !isDestroyed()) {
                    b.tvProviderCount.setText("Deadlines read via ContentProvider: " + n);
                }
            });
        });
    }

    private void logout() {
        // Cancel this account's reminders before switching (must run before SyncManager.stop)
        Context app = getApplicationContext();
        AppDatabase.executor.execute(() -> {
            for (Deadline d : AppDatabase.get(app).deadlineDao().getPending()) {
                ReminderScheduler.cancel(app, d.id);
            }
            for (StudySession s : AppDatabase.get(app).studyDao().getAllNow()) {
                StudyReminderScheduler.cancel(app, s.id);
            }
        });

        stopService(new Intent(this, NextDeadlineService.class));
        prefs.edit().putBoolean(NextDeadlineService.KEY_TRACKER_ON, false).apply();
        RevisionScheduler.cancel(this);
        SyncManager.stop(this);
        NoticeManager.stop();
        FirebaseAuth.getInstance().signOut();
        NextDeadlineWidget.refresh(this);   // widget switches to "Sign in..."
        UserSession.clear(this);

        Intent i = new Intent(this, LoginActivity.class);
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(i);
        finish();
    }
}
