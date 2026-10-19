package com.example.campuscompanion.notification;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;

import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;

import com.example.campuscompanion.data.local.Deadline;
import com.example.campuscompanion.ui.DeadlinesActivity;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public final class NotificationHelper {

    public static final String CHANNEL_REMINDERS = "deadline_reminders";
    public static final String CHANNEL_TRACKER = "deadline_tracker";

    private NotificationHelper() {}

    /** Safe to call many times; channels are only created once. */
    public static void createChannels(Context c) {
        if (Build.VERSION.SDK_INT < 26) return;
        NotificationManager nm = c.getSystemService(NotificationManager.class);

        NotificationChannel reminders = new NotificationChannel(
                CHANNEL_REMINDERS, "Deadline reminders", NotificationManager.IMPORTANCE_HIGH);
        reminders.setDescription("Reminders before assignments, labs and quizzes are due");

        NotificationChannel tracker = new NotificationChannel(
                CHANNEL_TRACKER, "Next deadline tracker", NotificationManager.IMPORTANCE_LOW);
        tracker.setDescription("Persistent notification showing your next deadline");

        nm.createNotificationChannel(reminders);
        nm.createNotificationChannel(tracker);
    }

    public static boolean canNotify(Context c) {
        return Build.VERSION.SDK_INT < 33
                || ContextCompat.checkSelfPermission(c, Manifest.permission.POST_NOTIFICATIONS)
                == PackageManager.PERMISSION_GRANTED;
    }

    public static String formatDue(long millis) {
        return new SimpleDateFormat("EEE, dd MMM  hh:mm a", Locale.getDefault()).format(new Date(millis));
    }

    public static PendingIntent openDeadlinesIntent(Context c, int requestCode) {
        Intent open = new Intent(c, DeadlinesActivity.class);
        open.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        return PendingIntent.getActivity(c, requestCode, open,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    @SuppressLint("MissingPermission") // guarded by canNotify()
    public static void showReminder(Context c, Deadline d) {
        createChannels(c);
        if (!canNotify(c)) return;

        String course = (d.course == null || d.course.isEmpty()) ? "" : d.course + " \u2022 ";
        String text = course + "Due " + formatDue(d.dueAt);
        String big = (d.note == null || d.note.isEmpty()) ? text : text + "\n" + d.note;

        NotificationCompat.Builder b = new NotificationCompat.Builder(c, CHANNEL_REMINDERS)
                .setSmallIcon(android.R.drawable.ic_popup_reminder)
                .setContentTitle(d.title)
                .setContentText(text)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(big))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_REMINDER)
                .setAutoCancel(true)
                .setContentIntent(openDeadlinesIntent(c, d.id));

        NotificationManagerCompat.from(c).notify(d.id, b.build());
    }
}
