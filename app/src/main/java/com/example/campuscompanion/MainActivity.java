package com.example.campuscompanion;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.widget.CompoundButton;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.example.campuscompanion.data.local.AppDatabase;
import com.example.campuscompanion.databinding.ActivityMainBinding;
import com.example.campuscompanion.notification.NotificationHelper;
import com.example.campuscompanion.provider.DeadlineProvider;
import com.example.campuscompanion.reminder.ReminderScheduler;
import com.example.campuscompanion.service.NextDeadlineService;
import com.example.campuscompanion.ui.DeadlinesActivity;

public class MainActivity extends AppCompatActivity {

    private ActivityMainBinding b;
    private SharedPreferences prefs;

    // Android 13+ asks for the notification permission at runtime
    private final ActivityResultLauncher<String> notificationPermission =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), granted -> { });

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
        b = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(b.getRoot());

        prefs = getSharedPreferences(NextDeadlineService.PREFS, Context.MODE_PRIVATE);
        NotificationHelper.createChannels(this);

        if (Build.VERSION.SDK_INT >= 33 && !NotificationHelper.canNotify(this)) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS);
        }

        b.btnDeadlines.setOnClickListener(v ->
                startActivity(new Intent(this, DeadlinesActivity.class)));

        b.btnExactAlarm.setOnClickListener(v -> {
            if (Build.VERSION.SDK_INT >= 31) {
                startActivity(new Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                        Uri.parse("package:" + getPackageName())));
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();

        // Keep the switch in sync with the service state without re-triggering the listener
        b.swTracker.setOnCheckedChangeListener(null);
        b.swTracker.setChecked(prefs.getBoolean(NextDeadlineService.KEY_TRACKER_ON, false));
        b.swTracker.setOnCheckedChangeListener(trackerListener);

        // Only show the button when exact alarms are not allowed yet
        b.btnExactAlarm.setVisibility(
                ReminderScheduler.canScheduleExact(this) ? android.view.View.GONE : android.view.View.VISIBLE);

        showProviderCount();
    }

    /** Demo of the ContentProvider: read the deadline count through a content:// URI. */
    private void showProviderCount() {
        AppDatabase.executor.execute(() -> {
            int count = 0;
            try (Cursor c = getContentResolver().query(
                    DeadlineProvider.CONTENT_URI, new String[]{"id"}, null, null, null)) {
                if (c != null) count = c.getCount();
            }
            final int n = count;
            runOnUiThread(() -> b.tvProviderCount.setText("Deadlines read via ContentProvider: " + n));
        });
    }
}
