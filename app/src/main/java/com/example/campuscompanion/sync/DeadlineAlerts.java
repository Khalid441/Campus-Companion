package com.example.campuscompanion.sync;

import android.annotation.SuppressLint;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;

import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

import com.example.campuscompanion.notification.NotificationHelper;
import com.example.campuscompanion.ui.DeadlinesActivity;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentSnapshot;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Shows a "New deadline from your CR" notification when the sync finds a shared deadline
 * this account has not seen before. Used by the live listener and by the background sync.
 */
final class DeadlineAlerts {

    private static final String PREFS = "deadline_alert_prefs";
    private static final String CHANNEL = "shared_deadlines";
    private static final int NOTIFICATION_ID = 7002;

    private DeadlineAlerts() {}

    /**
     * Returns the documents that are new to this account. The very first sync on a phone only
     * records what already exists, so a fresh login never floods the user with old deadlines.
     * The CR's own posts are never announced to the CR.
     */
    static List<DocumentSnapshot> findNew(Context app, List<DocumentSnapshot> docs) {
        SharedPreferences sp = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        String uid = FirebaseAuth.getInstance().getUid();
        String key = "known_" + uid;

        boolean firstRun = !sp.contains(key);
        Set<String> known = new HashSet<>(sp.getStringSet(key, new HashSet<String>()));
        Set<String> current = new HashSet<>();
        List<DocumentSnapshot> fresh = new ArrayList<>();
        long now = System.currentTimeMillis();

        for (DocumentSnapshot d : docs) {
            current.add(d.getId());
            if (firstRun || known.contains(d.getId())) continue;
            if (uid != null && uid.equals(d.getString("createdBy"))) continue;   // own post
            Long dueAt = d.getLong("dueAt");
            if (d.getString("title") == null || dueAt == null || dueAt < now) continue;
            fresh.add(d);
        }

        sp.edit().putStringSet(key, current).apply();
        return fresh;
    }

    @SuppressLint("MissingPermission") // guarded by canNotify()
    static void announce(Context app, List<DocumentSnapshot> fresh) {
        if (fresh.isEmpty() || !NotificationHelper.canNotify(app)) return;

        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel ch = new NotificationChannel(
                    CHANNEL, "New shared deadlines", NotificationManager.IMPORTANCE_HIGH);
            ch.setDescription("Deadlines posted by your class representative");
            app.getSystemService(NotificationManager.class).createNotificationChannel(ch);
        }

        String title;
        String text;
        SimpleDateFormat fmt = new SimpleDateFormat("EEE, dd MMM  hh:mm a", Locale.getDefault());

        if (fresh.size() == 1) {
            DocumentSnapshot d = fresh.get(0);
            String course = d.getString("course");
            title = "New deadline from your CR";
            text = d.getString("title")
                    + (course == null || course.isEmpty() ? "" : "  \u2022  " + course)
                    + "\nDue " + fmt.format(new Date(d.getLong("dueAt")));
        } else {
            title = fresh.size() + " new deadlines from your CR";
            StringBuilder sb = new StringBuilder();
            for (DocumentSnapshot d : fresh) {
                if (sb.length() > 0) sb.append("\n");
                sb.append(d.getString("title")).append("  \u2022  ")
                        .append(fmt.format(new Date(d.getLong("dueAt"))));
            }
            text = sb.toString();
        }

        Intent open = new Intent(app, DeadlinesActivity.class);
        open.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent pi = PendingIntent.getActivity(app, 7002, open,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        NotificationCompat.Builder b = new NotificationCompat.Builder(app, CHANNEL)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle(title)
                .setContentText(text.split("\n")[0])
                .setStyle(new NotificationCompat.BigTextStyle().bigText(text))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .setContentIntent(pi);

        NotificationManagerCompat.from(app).notify(NOTIFICATION_ID, b.build());
    }
}
