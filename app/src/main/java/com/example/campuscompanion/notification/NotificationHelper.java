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
import com.example.campuscompanion.data.local.StudySession;
import com.example.campuscompanion.ui.DeadlinesActivity;
import com.example.campuscompanion.ui.StudyActivity;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public final class NotificationHelper {

    public static final String CHANNEL_REMINDERS = "deadline_reminders";
    public static final String CHANNEL_TRACKER = "deadline_tracker";
    public static final String CHANNEL_STUDY = "study_reminders";

    private NotificationHelper() {}

    /** Safe to call many times; channels are only created once. */
    public static void createChannels(Context c) {
        if (Build.VERSION.SDK_INT < 26) return;
        NotificationManager nm = c.getSystemService(NotificationManager.class);

        // Clean up channels from the custom-sound experiment, if they exist
        nm.deleteNotificationChannel("deadline_reminders_v2");
        nm.deleteNotificationChannel("section_notices_v2");
        nm.deleteNotificationChannel("shared_deadlines_v2");

        NotificationChannel reminders = new NotificationChannel(
                CHANNEL_REMINDERS, "Deadline reminders", NotificationManager.IMPORTANCE_HIGH);
        reminders.setDescription("Reminders before assignments, labs and quizzes are due");

        NotificationChannel tracker = new NotificationChannel(
                CHANNEL_TRACKER, "Next deadline tracker", NotificationManager.IMPORTANCE_LOW);
        tracker.setDescription("Persistent notification showing your next deadline");

        NotificationChannel study = new NotificationChannel(
                CHANNEL_STUDY, "Study reminders", NotificationManager.IMPORTANCE_HIGH);
        study.setDescription("Reminders for your own study sessions (not university classes)");

        nm.createNotificationChannel(reminders);
        nm.createNotificationChannel(tracker);
        nm.createNotificationChannel(study);
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

    /** Reminder for a personal study session. Uses the phone's normal notification sound. */
    @SuppressLint("MissingPermission") // guarded by canNotify()
    public static void showStudyReminder(Context c, StudySession s) {
        createChannels(c);
        if (!canNotify(c)) return;

        String time = com.example.campuscompanion.util.TimeUtil.fmt(s.startMinutes) + " \u2013 "
                + com.example.campuscompanion.util.TimeUtil.fmt(s.endMinutes);
        String text = (s.topic == null || s.topic.isEmpty()) ? time : time + "  \u2022  " + s.topic;

        Intent open = new Intent(c, StudyActivity.class);
        open.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent pi = PendingIntent.getActivity(c, 60000 + s.id, open,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        NotificationCompat.Builder b = new NotificationCompat.Builder(c, CHANNEL_STUDY)
                .setSmallIcon(android.R.drawable.ic_menu_agenda)
                .setColor(0xFF00897B)
                .setContentTitle("\uD83D\uDCD6 Time to study: " + s.subject)
                .setContentText(text)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(text))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_REMINDER)
                .setAutoCancel(true)
                .setContentIntent(pi);

        NotificationManagerCompat.from(c).notify(60000 + s.id, b.build());
    }
}
