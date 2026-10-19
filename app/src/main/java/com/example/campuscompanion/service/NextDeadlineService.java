package com.example.campuscompanion.service;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.IBinder;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.Observer;

import com.example.campuscompanion.R;
import com.example.campuscompanion.data.local.AppDatabase;
import com.example.campuscompanion.data.local.Deadline;
import com.example.campuscompanion.notification.NotificationHelper;

/** Keeps a persistent notification showing the next unfinished deadline. */
public class NextDeadlineService extends Service {

    public static final String ACTION_START = "com.example.campuscompanion.action.START_TRACKER";
    public static final String ACTION_STOP = "com.example.campuscompanion.action.STOP_TRACKER";
    public static final String PREFS = "settings";
    public static final String KEY_TRACKER_ON = "tracker_on";

    private static final int NOTIFICATION_ID = 900001;

    private LiveData<Deadline> nextDeadline;
    private final Observer<Deadline> observer = this::updateNotification;
    private boolean observing = false;

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null; // started service, not bound
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && ACTION_STOP.equals(intent.getAction())) {
            getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                    .edit().putBoolean(KEY_TRACKER_ON, false).apply();
            stopForeground(Service.STOP_FOREGROUND_REMOVE);
            stopSelf();
            return START_NOT_STICKY;
        }

        NotificationHelper.createChannels(this);

        // Must call startForeground() within a few seconds of startForegroundService().
        Notification first = buildNotification(null);
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(NOTIFICATION_ID, first, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
        } else {
            startForeground(NOTIFICATION_ID, first);
        }

        if (!observing) {
            observing = true;
            nextDeadline = AppDatabase.get(this).deadlineDao().getNextPending();
            nextDeadline.observeForever(observer); // onStartCommand runs on the main thread
        }
        return START_STICKY;
    }

    private void updateNotification(Deadline d) {
        NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        nm.notify(NOTIFICATION_ID, buildNotification(d));
    }

    private Notification buildNotification(@Nullable Deadline d) {
        String title = d == null ? "No pending deadlines" : "Next: " + d.title;
        String text = d == null ? "You're all caught up" : NotificationHelper.formatDue(d.dueAt);

        Intent stop = new Intent(this, NextDeadlineService.class).setAction(ACTION_STOP);
        PendingIntent stopPi = PendingIntent.getService(this, 0, stop,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        return new NotificationCompat.Builder(this, NotificationHelper.CHANNEL_TRACKER)
                .setSmallIcon(android.R.drawable.ic_popup_reminder)
                .setContentTitle(title)
                .setContentText(text)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setContentIntent(NotificationHelper.openDeadlinesIntent(this, 1))
                .addAction(0, "Stop", stopPi)
                .build();
    }

    @Override
    public void onDestroy() {
        if (nextDeadline != null) {
            nextDeadline.removeObserver(observer);
        }
        super.onDestroy();
    }
}
