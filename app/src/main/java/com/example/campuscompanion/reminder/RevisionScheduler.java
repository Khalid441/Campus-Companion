package com.example.campuscompanion.reminder;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;

import com.example.campuscompanion.util.DateUtil;

import java.util.concurrent.TimeUnit;

/** Once a day (about 7 PM) a notification says how many topics are due for revision. */
public final class RevisionScheduler {
    private static final String WORK_NAME = "revision_daily";
    private static final String PREFS = "settings";
    private static final String KEY_ON = "revision_reminder_on";
    private static final int HOUR = 19;

    private RevisionScheduler() { }

    private static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public static boolean isEnabled(Context c) {
        return prefs(c).getBoolean(KEY_ON, true);
    }

    public static void setEnabled(Context c, boolean on) {
        prefs(c).edit().putBoolean(KEY_ON, on).apply();
        if (on) ensure(c); else cancel(c);
    }

    /** Safe to call often: an existing schedule is kept. */
    public static void ensure(Context c) {
        if (!isEnabled(c)) return;
        long now = System.currentTimeMillis();
        long first = DateUtil.startOfDay(now) + HOUR * 3600_000L;
        if (first <= now) first = DateUtil.addDays(now, 1) + HOUR * 3600_000L;

        PeriodicWorkRequest request = new PeriodicWorkRequest.Builder(RevisionWorker.class, 24, TimeUnit.HOURS)
                .setInitialDelay(first - now, TimeUnit.MILLISECONDS)
                .build();
        WorkManager.getInstance(c.getApplicationContext())
                .enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request);
    }

    public static void cancel(Context c) {
        WorkManager.getInstance(c.getApplicationContext()).cancelUniqueWork(WORK_NAME);
    }
}
