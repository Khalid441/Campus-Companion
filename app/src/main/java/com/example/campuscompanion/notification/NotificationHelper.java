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
import com.example.campuscompanion.ui.AttendanceActivity;
import com.example.campuscompanion.ui.DeadlinesActivity;
import com.example.campuscompanion.ui.RevisionActivity;
import com.example.campuscompanion.ui.StudyActivity;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public final class NotificationHelper {

    public static final String CHANNEL_REMINDERS = "deadline_reminders";
    public static final String CHANNEL_TRACKER = "deadline_tracker";
    public static final String CHANNEL_STUDY = "study_reminders";
    public static final String CHANNEL_REVISION = "revision_reminders";
    public static final String CHANNEL_ALERTS = "attendance_alerts";

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

        NotificationChannel revision = new NotificationChannel(
                CHANNEL_REVISION, "Revision reminders", NotificationManager.IMPORTANCE_DEFAULT);
        revision.setDescription("Daily reminder to revise topics taught 1, 3 and 7 days ago");

        NotificationChannel alerts = new NotificationChannel(
                CHANNEL_ALERTS, "Attendance alerts", NotificationManager.IMPORTANCE_HIGH);
        alerts.setDescription("Warns you when a course gets close to your minimum attendance");

        nm.createNotificationChannel(alerts);
        nm.createNotificationChannel(revision);
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

    /** Daily "N topics to revise" notification. */
    @SuppressLint("MissingPermission") // guarded by canNotify()
    public static void showRevisionReminder(Context c, int count, String firstTitle) {
        createChannels(c);
        if (!canNotify(c) || count <= 0) return;

        Intent open = new Intent(c, RevisionActivity.class);
        open.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent pi = PendingIntent.getActivity(c, 70000, open,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        String text = count == 1 ? firstTitle : firstTitle + " and " + (count - 1) + " more";
        NotificationCompat.Builder b = new NotificationCompat.Builder(c, CHANNEL_REVISION)
                .setSmallIcon(android.R.drawable.ic_menu_agenda)
                .setColor(0xFF00897B)
                .setContentTitle(count + (count == 1 ? " topic" : " topics") + " to revise today")
                .setContentText(text)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(text))
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setCategory(NotificationCompat.CATEGORY_REMINDER)
                .setAutoCancel(true)
                .setContentIntent(pi);

        NotificationManagerCompat.from(c).notify(70000, b.build());
    }

    /** "CSE 2209 is close to your minimum" style warning after an absence. */
    @SuppressLint("MissingPermission") // guarded by canNotify()
    public static void showAttendanceWarning(Context c, int courseId, String title, String text) {
        createChannels(c);
        if (!canNotify(c)) return;

        Intent open = new Intent(c, AttendanceActivity.class);
        open.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent pi = PendingIntent.getActivity(c, 80000 + courseId, open,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        NotificationCompat.Builder b = new NotificationCompat.Builder(c, CHANNEL_ALERTS)
                .setSmallIcon(android.R.drawable.ic_dialog_alert)
                .setColor(0xFFF59E0B)
                .setContentTitle(title)
                .setContentText(text)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(text))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_STATUS)
                .setAutoCancel(true)
                .setContentIntent(pi);

        NotificationManagerCompat.from(c).notify(80000 + courseId, b.build());
    }
}
