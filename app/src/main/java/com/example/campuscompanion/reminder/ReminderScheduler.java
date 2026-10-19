package com.example.campuscompanion.reminder;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import com.example.campuscompanion.data.local.Deadline;
import com.example.campuscompanion.receiver.ReminderReceiver;

public final class ReminderScheduler {

    public static final String EXTRA_ID = "deadline_id";

    private ReminderScheduler() {}

    public static boolean canScheduleExact(Context c) {
        if (Build.VERSION.SDK_INT < 31) return true;
        AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
        return am.canScheduleExactAlarms();
    }

    /** (Re)schedules the reminder for one deadline. Needs deadline.id to be set. */
    public static void schedule(Context c, Deadline d) {
        cancel(c, d.id);
        if (d.done || d.remindBeforeMinutes < 0) return;

        long triggerAt = d.dueAt - d.remindBeforeMinutes * 60_000L;
        if (triggerAt <= System.currentTimeMillis()) return; // reminder time already passed

        AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
        PendingIntent pi = pendingIntent(c, d.id);

        try {
            if (canScheduleExact(c)) {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi);
            } else {
                // Exact-alarm permission not granted: still fires, but Android may delay it a bit.
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi);
            }
        } catch (SecurityException e) {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi);
        }
    }

    public static void cancel(Context c, int deadlineId) {
        AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
        am.cancel(pendingIntent(c, deadlineId));
    }

    private static PendingIntent pendingIntent(Context c, int deadlineId) {
        Intent i = new Intent(c, ReminderReceiver.class);
        i.putExtra(EXTRA_ID, deadlineId);
        // requestCode = deadline id, so every deadline has its own alarm
        return PendingIntent.getBroadcast(c, deadlineId, i,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }
}
