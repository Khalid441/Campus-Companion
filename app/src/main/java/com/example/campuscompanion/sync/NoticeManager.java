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
import androidx.lifecycle.MutableLiveData;

import com.example.campuscompanion.model.Notice;
import com.example.campuscompanion.notification.NotificationHelper;
import com.example.campuscompanion.ui.NoticesActivity;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.Query;

/**
 * Listens (in real time) for the newest notice of the user's section, feeds the home screen
 * card and shows a notification when a new notice arrives from the class representative.
 */
public final class NoticeManager {

    /** Newest notice of the section (null = none yet). */
    public static final MutableLiveData<Notice> latest = new MutableLiveData<>();

    private static final String PREFS = "notice_prefs";
    private static final String CHANNEL = "section_notices";

    private static ListenerRegistration registration;
    private static String activeSection;

    private NoticeManager() {}

    public static synchronized void start(Context context, String section) {
        if (section == null) return;
        if (section.equals(activeSection) && registration != null) return;
        stopListener();

        Context app = context.getApplicationContext();
        activeSection = section;
        final String uid = FirebaseAuth.getInstance().getUid();

        registration = FirebaseFirestore.getInstance()
                .collection("sections").document(section).collection("notices")
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .limit(1)
                .addSnapshotListener((snap, error) -> {
                    if (error != null || snap == null) return;

                    SharedPreferences sp = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
                    String key = "notified_" + uid;

                    if (snap.isEmpty()) {
                        if (!snap.getMetadata().isFromCache()) latest.postValue(null);
                        // Nothing yet: any notice that arrives later counts as new
                        if (!sp.contains(key)) sp.edit().putLong(key, 0).apply();
                        return;
                    }

                    Notice n = Notice.from(snap.getDocuments().get(0));
                    latest.postValue(n);

                    if (!sp.contains(key)) {            // first run on this phone: don't replay old notices
                        sp.edit().putLong(key, n.createdAt).apply();
                        return;
                    }
                    if (n.createdAt <= sp.getLong(key, 0)) return;
                    sp.edit().putLong(key, n.createdAt).apply();

                    boolean mine = uid != null && uid.equals(n.authorUid);
                    if (!mine) showNotification(app, n);
                });
    }

    /** Called on logout. */
    public static synchronized void stop() {
        stopListener();
        latest.postValue(null);
    }

    private static void stopListener() {
        if (registration != null) registration.remove();
        registration = null;
        activeSection = null;
    }

    // ---- "seen" bookkeeping for the NEW dot on the home card ----

    public static long lastSeen(Context c) {
        return c.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getLong("seen_" + FirebaseAuth.getInstance().getUid(), 0);
    }

    public static void markSeen(Context c, long createdAt) {
        c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putLong("seen_" + FirebaseAuth.getInstance().getUid(), createdAt).apply();
    }

    @SuppressLint("MissingPermission") // guarded by canNotify()
    private static void showNotification(Context c, Notice n) {
        if (!NotificationHelper.canNotify(c)) return;

        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel ch = new NotificationChannel(
                    CHANNEL, "Section notices", NotificationManager.IMPORTANCE_HIGH);
            ch.setDescription("Notices posted by your class representative");
            c.getSystemService(NotificationManager.class).createNotificationChannel(ch);
        }

        Intent open = new Intent(c, NoticesActivity.class);
        open.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent pi = PendingIntent.getActivity(c, 7001, open,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        String who = (n.author == null || n.author.isEmpty()) ? "Your CR" : n.author;
        NotificationCompat.Builder b = new NotificationCompat.Builder(c, CHANNEL)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle("New notice from " + who)
                .setContentText(n.message)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(n.message))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .setContentIntent(pi);

        NotificationManagerCompat.from(c).notify(7001, b.build());
    }
}
