package com.example.campuscompanion.ui;

import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.provider.Settings;
import androidx.activity.OnBackPressedCallback;
import android.content.SharedPreferences;
import android.text.InputType;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.os.Build;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.view.View;
import android.view.WindowManager;
import android.widget.ArrayAdapter;

import androidx.appcompat.app.AppCompatActivity;

import com.example.campuscompanion.CampusApp;
import com.example.campuscompanion.R;
import com.example.campuscompanion.data.local.AppDatabase;
import com.example.campuscompanion.data.local.AttendanceCourse;
import com.example.campuscompanion.databinding.ActivityFocusBinding;
import com.example.campuscompanion.util.FocusStats;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Pomodoro-style study timer. Finished minutes count towards today's and this week's focus time. */
public class FocusActivity extends AppCompatActivity {

    private ActivityFocusBinding b;
    private final List<String> names = new ArrayList<>();

    private long totalMs = 25 * 60_000L;
    private long remainingMs = totalMs;
    private boolean running = false;
    private boolean isBreak = false;
    private long endAt = 0;
    private CountDownTimer timer;

    private static final String KEY_CUSTOM = "focus_custom_min";
    private static final String KEY_BREAK = "focus_break_min";
    private static final String KEY_DND = "focus_dnd";
    private static final String KEY_LOCK = "focus_lock";
    private static final String KEY_PREV_FILTER = "focus_prev_filter";   // set only while we changed it
    private boolean dndActive = false;
    private boolean lockActive = false;
    private int customMin = 30;
    private int breakMin = 5;

    private SharedPreferences prefs() {
        return getSharedPreferences(CampusApp.PREFS, Context.MODE_PRIVATE);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        b = ActivityFocusBinding.inflate(getLayoutInflater());
        setContentView(b.getRoot());

        names.add("General study");
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, names);
        b.spFocusCourse.setAdapter(adapter);
        AppDatabase.executor.execute(() -> {
            List<AttendanceCourse> list = AppDatabase.get(getApplicationContext()).attendanceDao().getCoursesNow();
            runOnUiThread(() -> {
                if (isFinishing() || isDestroyed()) return;
                for (AttendanceCourse c : list) names.add(c.title());
                adapter.notifyDataSetChanged();
            });
        });

        customMin = prefs().getInt(KEY_CUSTOM, 30);
        breakMin = prefs().getInt(KEY_BREAK, 5);
        showCustomLabels();

        b.toggleLength.check(R.id.btnLen25);
        b.toggleLength.addOnButtonCheckedListener((group, id, checked) -> {
            if (!checked || running) return;
            if (id == R.id.btnLen15) setLength(15, false);
            else if (id == R.id.btnLen25) setLength(25, false);
            else if (id == R.id.btnLen45) setLength(45, false);
            else if (id == R.id.btnLenCustom) setLength(customMin, false);
            else setLength(breakMin, true);
        });
        // Tapping Custom or Break again lets you change its minutes
        b.btnLenCustom.setOnClickListener(v -> {
            if (!running && b.toggleLength.getCheckedButtonId() == R.id.btnLenCustom) askMinutes();
        });
        b.btnLenBreak.setOnClickListener(v -> {
            if (!running && b.toggleLength.getCheckedButtonId() == R.id.btnLenBreak) askMinutes();
        });
        b.tvTimer.setOnClickListener(v -> { if (!running) askMinutes(); });

        restoreLeftoverFilter();
        b.swDnd.setChecked(prefs().getBoolean(KEY_DND, false));
        b.swLock.setChecked(prefs().getBoolean(KEY_LOCK, false));
        b.swDnd.setOnCheckedChangeListener((bt, on) -> prefs().edit().putBoolean(KEY_DND, on).apply());
        b.swLock.setOnCheckedChangeListener((bt, on) -> prefs().edit().putBoolean(KEY_LOCK, on).apply());
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override public void handleOnBackPressed() {
                if (!running || isBreak) { finish(); return; }
                new MaterialAlertDialogBuilder(FocusActivity.this)
                        .setTitle("Stop this focus session?")
                        .setMessage("The minutes done so far are saved.")
                        .setPositiveButton("Stop", (d, w) -> { reset(); finish(); })
                        .setNegativeButton("Keep going", null)
                        .show();
            }
        });

        b.btnFocusStart.setOnClickListener(v -> {
            if (running) pause(); else start();
        });
        b.btnFocusReset.setOnClickListener(v -> reset());
        render();
        showStats();
    }

    private void showCustomLabels() {
        b.btnLenCustom.setText(customMin + " min");
        b.btnLenBreak.setText("Break " + breakMin);
    }

    /** Lets the student type any number of minutes for the current mode. */
    private void askMinutes() {
        final boolean forBreak = isBreak;
        final int max = forBreak ? 60 : 240;
        EditText input = new EditText(this);
        input.setInputType(InputType.TYPE_CLASS_NUMBER);
        input.setText(String.valueOf(forBreak ? breakMin : (b.toggleLength.getCheckedButtonId() == R.id.btnLenCustom
                ? customMin : (int) (totalMs / 60_000L))));
        input.setSelectAllOnFocus(true);
        FrameLayout box = new FrameLayout(this);
        int pad = (int) (24 * getResources().getDisplayMetrics().density);
        box.setPadding(pad, pad / 2, pad, 0);
        box.addView(input);

        androidx.appcompat.app.AlertDialog dialog = new MaterialAlertDialogBuilder(this)
                .setTitle(forBreak ? "Break length (minutes)" : "Focus length (minutes)")
                .setMessage("Any number from 1 to " + max)
                .setView(box)
                .setPositiveButton("Set", null)
                .setNegativeButton("Cancel", null)
                .create();
        dialog.show();
        dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            int minutes;
            try {
                minutes = Integer.parseInt(input.getText().toString().trim());
            } catch (NumberFormatException e) {
                input.setError("Enter a number");
                return;
            }
            if (minutes < 1 || minutes > max) {
                input.setError("Between 1 and " + max);
                return;
            }
            if (forBreak) {
                breakMin = minutes;
                prefs().edit().putInt(KEY_BREAK, minutes).apply();
            } else {
                customMin = minutes;
                prefs().edit().putInt(KEY_CUSTOM, minutes).apply();
            }
            showCustomLabels();
            setLength(minutes, forBreak);
            if (!forBreak && b.toggleLength.getCheckedButtonId() != R.id.btnLenCustom) {
                b.toggleLength.check(R.id.btnLenCustom);
                setLength(minutes, false);   // the check listener may have reset it
            }
            dialog.dismiss();
        });
    }

    private void setLength(int minutes, boolean breakTime) {
        isBreak = breakTime;
        totalMs = minutes * 60_000L;
        remainingMs = totalMs;
        render();
    }

    private void start() {
        // Deep focus needs Do Not Disturb access once; ask before starting
        if (!isBreak && b.swDnd.isChecked() && !policyGranted()) {
            new MaterialAlertDialogBuilder(this)
                    .setTitle("Allow Do Not Disturb")
                    .setMessage("To silence calls and notifications during a session, switch on "
                            + "Campus Companion in the next screen, then come back and press Start. "
                            + "Alarms still ring, and the setting returns to normal when the session ends.")
                    .setPositiveButton("Open settings", (d, w) -> startActivity(
                            new Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)))
                    .setNegativeButton("Focus without it", (d, w) -> {
                        b.swDnd.setChecked(false);
                        start();
                    })
                    .show();
            return;
        }
        if (remainingMs <= 0) remainingMs = totalMs;
        running = true;
        endAt = System.currentTimeMillis() + remainingMs;
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        b.spFocusCourse.setEnabled(false);
        if (!isBreak) enterDeepFocus();
        schedule();
        render();
    }

    private void schedule() {
        if (timer != null) timer.cancel();
        timer = new CountDownTimer(Math.max(1, endAt - System.currentTimeMillis()), 500) {
            @Override public void onTick(long left) {
                remainingMs = Math.max(0, endAt - System.currentTimeMillis());
                render();
            }
            @Override public void onFinish() { finished(); }
        }.start();
    }

    private void pause() {
        remainingMs = Math.max(0, endAt - System.currentTimeMillis());
        stopTimer();
        render();
    }

    private void reset() {
        if (running) remainingMs = Math.max(0, endAt - System.currentTimeMillis());
        credit(false);
        stopTimer();
        remainingMs = totalMs;
        render();
        showStats();
    }

    // ---------------------------------------------------------------------
    // Deep focus: Do Not Disturb (alarms only) and screen pinning
    // ---------------------------------------------------------------------

    private boolean policyGranted() {
        NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        return nm != null && nm.isNotificationPolicyAccessGranted();
    }

    private void enterDeepFocus() {
        NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (b.swDnd.isChecked() && nm != null && nm.isNotificationPolicyAccessGranted() && !dndActive) {
            // Remember the user's own setting so it can always be put back, even after a crash
            prefs().edit().putInt(KEY_PREV_FILTER, nm.getCurrentInterruptionFilter()).apply();
            nm.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_ALARMS);
            dndActive = true;
        }
        if (b.swLock.isChecked() && !lockActive) {
            try {
                startLockTask();
                lockActive = true;
            } catch (Exception ignored) { }
        }
    }

    private void leaveDeepFocus() {
        if (dndActive) {
            NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            if (nm != null && nm.isNotificationPolicyAccessGranted()) {
                nm.setInterruptionFilter(prefs().getInt(KEY_PREV_FILTER, NotificationManager.INTERRUPTION_FILTER_ALL));
            }
            prefs().edit().remove(KEY_PREV_FILTER).apply();
            dndActive = false;
        }
        if (lockActive) {
            try {
                stopLockTask();
            } catch (Exception ignored) { }
            lockActive = false;
        }
    }

    /** If the app was killed during a session, give the user their normal phone settings back. */
    private void restoreLeftoverFilter() {
        if (!prefs().contains(KEY_PREV_FILTER) || running) return;
        NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm != null && nm.isNotificationPolicyAccessGranted()) {
            nm.setInterruptionFilter(prefs().getInt(KEY_PREV_FILTER, NotificationManager.INTERRUPTION_FILTER_ALL));
        }
        prefs().edit().remove(KEY_PREV_FILTER).apply();
    }

    private void stopTimer() {
        leaveDeepFocus();
        running = false;
        if (timer != null) timer.cancel();
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        b.spFocusCourse.setEnabled(true);
    }

    /** Adds the minutes done so far; a finished session counts fully. */
    private void credit(boolean complete) {
        if (isBreak) return;
        long doneMs = complete ? totalMs : totalMs - remainingMs;
        int minutes = (int) (doneMs / 60_000L);
        if (minutes <= 0) return;
        String sel = (String) b.spFocusCourse.getSelectedItem();
        FocusStats.add(this, minutes, "General study".equals(sel) ? null : sel);
    }

    private void finished() {
        if (!running) return;
        remainingMs = 0;
        credit(true);
        stopTimer();
        buzz();
        render();
        showStats();
        new MaterialAlertDialogBuilder(this)
                .setTitle(isBreak ? "Break over" : "Session complete")
                .setMessage(isBreak ? "Ready for the next focus session?"
                        : "Nice work! Take a " + breakMin + " minute break.")
                .setPositiveButton("OK", (d, w) -> {
                    remainingMs = totalMs;
                    render();
                })
                .show();
    }

    private void buzz() {
        Vibrator v = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
        if (v == null || !v.hasVibrator()) return;
        if (Build.VERSION.SDK_INT >= 26) {
            v.vibrate(VibrationEffect.createOneShot(600, VibrationEffect.DEFAULT_AMPLITUDE));
        } else {
            v.vibrate(600);
        }
    }

    private void render() {
        long ms = running ? Math.max(0, endAt - System.currentTimeMillis()) : remainingMs;
        long sec = (ms + 999) / 1000;
        b.tvTimer.setText(String.format(Locale.US, "%02d:%02d", sec / 60, sec % 60));
        b.focusRing.setProgressCompat(totalMs == 0 ? 0 : (int) (1000 * ms / totalMs), false);
        b.btnFocusStart.setText(running ? "Pause" : (remainingMs < totalMs ? "Resume" : "Start"));
        b.btnFocusReset.setVisibility(running || remainingMs < totalMs ? View.VISIBLE : View.INVISIBLE);
    }

    private void showStats() {
        b.tvFocusStats.setText("Today " + FocusStats.format(FocusStats.today(this))
                + "  \u2022  last 7 days " + FocusStats.format(FocusStats.week(this)));
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Time passed while the screen was off: catch up
        if (running) {
            if (System.currentTimeMillis() >= endAt) finished(); else schedule();
        }
    }

    @Override
    protected void onDestroy() {
        if (timer != null) timer.cancel();
        leaveDeepFocus();
        super.onDestroy();
    }
}
