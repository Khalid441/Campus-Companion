package com.example.campuscompanion.receiver;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import com.example.campuscompanion.data.local.AppDatabase;
import com.example.campuscompanion.data.local.StudySession;
import com.example.campuscompanion.notification.NotificationHelper;
import com.example.campuscompanion.reminder.StudyReminderScheduler;
import com.google.firebase.auth.FirebaseAuth;

/** Fires at the reminder time of a study session: shows the notification, then re-arms for next week. */
public class StudyReminderReceiver extends BroadcastReceiver {

    public static final String EXTRA_ID = "study_id";

    @Override
    public void onReceive(Context context, Intent intent) {
        final int id = intent.getIntExtra(EXTRA_ID, -1);
        if (id < 0) return;

        final PendingResult result = goAsync();
        final Context app = context.getApplicationContext();

        AppDatabase.executor.execute(() -> {
            try {
                if (FirebaseAuth.getInstance().getCurrentUser() == null) return;   // signed out
                StudySession s = AppDatabase.get(app).studyDao().getById(id);
                if (s != null && s.enabled) {
                    NotificationHelper.showStudyReminder(app, s);
                    StudyReminderScheduler.schedule(app, s);    // next occurrence
                }
            } finally {
                result.finish();
            }
        });
    }
}
