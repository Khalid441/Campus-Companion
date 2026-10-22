package com.example.campuscompanion.sync;

import android.content.Context;

import androidx.work.Constraints;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;

import com.example.campuscompanion.data.local.AppDatabase;
import com.example.campuscompanion.data.local.Deadline;
import com.example.campuscompanion.data.local.DeadlineDao;
import com.example.campuscompanion.reminder.ReminderScheduler;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * Keeps the local Room database in sync with the deadlines the class representative posts.
 * Room stays the single source of truth for the UI, so the app works offline.
 */
public final class SyncManager {

    private static final String WORK_NAME = "section_sync";
    private static ListenerRegistration registration;
    private static String activeSection;

    private SyncManager() {}

    /** Real-time listener: every change in Firestore is pushed here instantly. */
    public static synchronized void start(Context context, String section) {
        if (section == null) return;
        if (section.equals(activeSection) && registration != null) return;
        stopListener();

        Context app = context.getApplicationContext();
        activeSection = section;
        registration = FirebaseFirestore.getInstance()
                .collection("sections").document(section).collection("deadlines")
                .addSnapshotListener((snap, error) -> {
                    if (error != null || snap == null) return;
                    // An empty snapshot from a cold cache says nothing about the server
                    if (snap.isEmpty() && snap.getMetadata().isFromCache()) return;
                    List<DocumentSnapshot> docs = snap.getDocuments();
                    AppDatabase.executor.execute(() -> reconcileBlocking(app, docs));
                });
    }

    /** Called on logout: stop listening and drop the shared deadlines from this phone. */
    public static synchronized void stop(Context context) {
        stopListener();
        WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME);
        Context app = context.getApplicationContext();
        AppDatabase.executor.execute(() -> {
            synchronized (SyncManager.class) {
                DeadlineDao dao = AppDatabase.get(app).deadlineDao();
                for (Deadline d : dao.getShared()) ReminderScheduler.cancel(app, d.id);
                dao.deleteShared();
            }
        });
    }

    private static void stopListener() {
        if (registration != null) registration.remove();
        registration = null;
        activeSection = null;
    }

    /** Background fallback: also fetch every ~15 minutes when the phone has internet. */
    public static void schedulePeriodic(Context context) {
        Constraints constraints = new Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED).build();
        PeriodicWorkRequest request = new PeriodicWorkRequest.Builder(
                SyncWorker.class, 15, TimeUnit.MINUTES)
                .setConstraints(constraints).build();
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request);
    }

    /**
     * Makes the local shared deadlines match the Firestore documents:
     * add new, update changed, remove deleted. Must run off the main thread.
     */
    public static void reconcileBlocking(Context app, List<DocumentSnapshot> docs) {
        synchronized (SyncManager.class) {
            DeadlineDao dao = AppDatabase.get(app).deadlineDao();
            Set<String> keep = new HashSet<>();

            // Shared deadlines this account has not seen before (for the notification)
            List<DocumentSnapshot> fresh = DeadlineAlerts.findNew(app, docs);

            for (DocumentSnapshot doc : docs) {
                String remoteId = doc.getId();
                String title = doc.getString("title");
                Long dueAt = doc.getLong("dueAt");
                if (title == null || dueAt == null) continue;
                keep.add(remoteId);

                Deadline existing = dao.getByRemoteId(remoteId);
                Deadline d = existing != null ? existing : new Deadline();
                d.title = title;
                d.course = nz(doc.getString("course"));
                d.note = nz(doc.getString("note"));
                String priority = doc.getString("priority");
                d.priority = priority == null ? Deadline.MEDIUM : priority;
                d.dueAt = dueAt;
                d.isShared = true;
                d.remoteId = remoteId;

                if (existing == null) {
                    d.remindBeforeMinutes = 60; // shared items remind 1 hour before by default
                    d.done = false;
                    d.id = (int) dao.insert(d);
                } else {
                    dao.update(d); // keeps the student's own "done" tick
                }
                ReminderScheduler.schedule(app, d);
            }

            for (Deadline old : dao.getShared()) {
                if (old.remoteId == null || !keep.contains(old.remoteId)) {
                    ReminderScheduler.cancel(app, old.id);
                    dao.delete(old);
                }
            }

            DeadlineAlerts.announce(app, fresh);
        }
    }

    private static String nz(String s) {
        return s == null ? "" : s;
    }
}
