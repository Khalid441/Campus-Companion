package com.example.campuscompanion.receiver;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import com.example.campuscompanion.data.local.AppDatabase;
import com.example.campuscompanion.data.local.Deadline;
import com.example.campuscompanion.notification.NotificationHelper;
import com.example.campuscompanion.reminder.ReminderScheduler;

public class ReminderReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        int id = intent.getIntExtra(ReminderScheduler.EXTRA_ID, -1);
        if (id < 0) return;

        // The database must not be read on the main thread, so keep the receiver alive
        // with goAsync() while a background thread does the work.
        PendingResult result = goAsync();
        Context app = context.getApplicationContext();

        AppDatabase.executor.execute(() -> {
            try {
                Deadline d = AppDatabase.get(app).deadlineDao().getById(id);
                // Skip if the user deleted it or already finished it
                if (d != null && !d.done) {
                    NotificationHelper.showReminder(app, d);
                }
            } finally {
                result.finish();
            }
        });
    }
}
