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
import android.widget.Toast;

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
import com.example.campuscompanion.sync.BackupManager;
import com.example.campuscompanion.sync.NoticeManager;
import com.example.campuscompanion.sync.SyncManager;
import com.example.campuscompanion.util.SampleData;
import com.example.campuscompanion.util.StudyStats;
import com.example.campuscompanion.widget.NextDeadlineWidget;
import com.google.android.material.color.DynamicColors;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
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
        b.btnAbout.setOnClickListener(v -> startActivity(new Intent(this, AboutActivity.class)));
        b.btnGoal.setOnClickListener(v -> chooseGoal());
        b.btnCalendar.setOnClickListener(v -> CalendarExport.show(this));
        b.btnSampleLoad.setOnClickListener(v -> SampleData.load(this, () -> runOnUiThread(() ->
                Toast.makeText(this, "Sample data added", Toast.LENGTH_SHORT).show())));
        b.btnSampleRemove.setOnClickListener(v -> SampleData.remove(this, () -> runOnUiThread(() ->
                Toast.makeText(this, "Sample data removed", Toast.LENGTH_SHORT).show())));
        b.btnBackupNow.setOnClickListener(v -> backupNow());
        b.btnRestore.setOnClickListener(v -> confirmRestore());
    }

    // ---------------------------------------------------------------------
    // Weekly goal and cloud backup
    // ---------------------------------------------------------------------

    private static final String[] THEME_NAMES = {"System default", "Light", "Dark"};
    private static final int[] THEME_MODES = {AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM,
            AppCompatDelegate.MODE_NIGHT_NO, AppCompatDelegate.MODE_NIGHT_YES};

    private int themeIndex() {
        int mode = getSharedPreferences(CampusApp.PREFS, Context.MODE_PRIVATE)
                .getInt(CampusApp.KEY_NIGHT_MODE, AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
        for (int i = 0; i < THEME_MODES.length; i++) if (THEME_MODES[i] == mode) return i;
        return 0;
    }

    private void showTheme() {
        b.btnTheme.setText("Theme: " + THEME_NAMES[themeIndex()]);
    }

    private void chooseTheme() {
        new MaterialAlertDialogBuilder(this)
                .setTitle("Theme")
                .setSingleChoiceItems(THEME_NAMES, themeIndex(), (d, which) -> {
                    getSharedPreferences(CampusApp.PREFS, Context.MODE_PRIVATE)
                            .edit().putInt(CampusApp.KEY_NIGHT_MODE, THEME_MODES[which]).apply();
                    d.dismiss();
                    AppCompatDelegate.setDefaultNightMode(THEME_MODES[which]);   // recreates the screen
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void chooseGoal() {
        GoalDialog.show(this, this::showGoal);
    }

    private void showGoal() {
        int g = StudyStats.goal(this);
        b.btnGoal.setText("Weekly revision goal: " + (g == 0 ? "off" : String.valueOf(g)));
    }

    private void showBackupInfo() {
        long last = BackupManager.lastBackup(this);
        b.tvBackupInfo.setText(last == 0 ? "No backup yet"
                : "Last backup: " + new java.text.SimpleDateFormat("d MMM, h:mm a", java.util.Locale.getDefault())
                .format(new java.util.Date(last)));
    }

    private void backupNow() {
        b.btnBackupNow.setEnabled(false);
        BackupManager.backupNow(this, (ok, msg) -> {
            if (isFinishing() || isDestroyed()) return;
            b.btnBackupNow.setEnabled(true);
            showBackupInfo();
            Toast.makeText(this, msg, Toast.LENGTH_LONG).show();
        });
    }

    private void confirmRestore() {
        new MaterialAlertDialogBuilder(this)
                .setTitle("Restore from backup?")
                .setMessage("This replaces the attendance, notes, topics, routine, study plan and personal "
                        + "deadlines on this phone with your last backup.")
                .setPositiveButton("Restore", (d, w) -> {
                    b.btnRestore.setEnabled(false);
                    BackupManager.restore(this, (ok, msg) -> {
                        if (isFinishing() || isDestroyed()) return;
                        b.btnRestore.setEnabled(true);
                        Toast.makeText(this, msg, Toast.LENGTH_LONG).show();
                    });
                })
                .setNegativeButton("Cancel", null)
                .show();
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

        showTheme();
        b.btnTheme.setOnClickListener(v -> chooseTheme());
        boolean dynamicOk = DynamicColors.isDynamicColorAvailable();
        b.swDynamic.setVisibility(dynamicOk ? View.VISIBLE : View.GONE);
        b.swDynamic.setOnCheckedChangeListener(null);
        b.swDynamic.setChecked(getSharedPreferences(CampusApp.PREFS, Context.MODE_PRIVATE)
                .getBoolean(CampusApp.KEY_DYNAMIC, false));
        b.swDynamic.setOnCheckedChangeListener((button, on) -> {
            getSharedPreferences(CampusApp.PREFS, Context.MODE_PRIVATE)
                    .edit().putBoolean(CampusApp.KEY_DYNAMIC, on).apply();
            Toast.makeText(this, "Close and reopen the app to apply the colours", Toast.LENGTH_LONG).show();
        });

        b.swBackup.setOnCheckedChangeListener(null);
        b.swBackup.setChecked(BackupManager.isEnabled(this));
        b.swBackup.setOnCheckedChangeListener((button, on) -> {
            BackupManager.setEnabled(this, on);
            if (on) backupNow();
        });
        showBackupInfo();
        showGoal();

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
        BackupManager.setEnabled(this, false);   // the next account starts with backup off
        SyncManager.stop(this);
        NoticeManager.stop();
        FirebaseAuth.getInstance().signOut();
        NextDeadlineWidget.refresh(this);   // widgets switch to "Sign in..."
        com.example.campuscompanion.widget.TodayWidget.refresh(this);
        com.example.campuscompanion.widget.AttendanceWidget.refresh(this);
        UserSession.clear(this);

        Intent i = new Intent(this, LoginActivity.class);
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(i);
        finish();
    }
}
