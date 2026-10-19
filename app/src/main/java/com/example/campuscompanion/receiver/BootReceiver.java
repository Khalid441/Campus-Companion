package com.example.campuscompanion.receiver;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import com.example.campuscompanion.data.local.AppDatabase;
import com.example.campuscompanion.data.local.Deadline;
import com.example.campuscompanion.reminder.ReminderScheduler;

import java.util.List;

/** Alarms are wiped when the phone restarts, so schedule them again from the database. */
public class BootReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        if (!Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) return;

        PendingResult result = goAsync();
        Context app = context.getApplicationContext();

        AppDatabase.executor.execute(() -> {
            try {
                List<Deadline> pending = AppDatabase.get(app).deadlineDao().getPending();
                for (Deadline d : pending) {
                    ReminderScheduler.schedule(app, d);
                }
            } finally {
                result.finish();
            }
        });
    }
}
