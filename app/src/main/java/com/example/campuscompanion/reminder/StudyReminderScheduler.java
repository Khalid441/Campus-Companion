package com.example.campuscompanion.reminder;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;

import com.example.campuscompanion.data.local.StudySession;
import com.example.campuscompanion.receiver.StudyReminderReceiver;

import java.util.Calendar;

/**
 * Schedules the next reminder of a weekly study session. When it fires, the receiver shows the
 * notification and calls schedule() again, so the session repeats every week on its days.
 */
public final class StudyReminderScheduler {

    private static final int REQUEST_BASE = 50000;

    private StudyReminderScheduler() {}

    public static void schedule(Context c, StudySession s) {
        cancel(c, s.id);
        if (!s.enabled || s.reminderMinutes < 0 || s.daysMask == 0) return;

        long triggerAt = nextTrigger(s);
        if (triggerAt < 0) return;

        AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
        PendingIntent pi = pending(c, s.id, PendingIntent.FLAG_UPDATE_CURRENT);
        try {
            if (ReminderScheduler.canScheduleExact(c)) {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi);
            } else {
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi);
            }
        } catch (SecurityException e) {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi);
        }
    }

    public static void cancel(Context c, int sessionId) {
        PendingIntent pi = pending(c, sessionId, PendingIntent.FLAG_NO_CREATE);
        if (pi != null) {
            AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
            am.cancel(pi);
            pi.cancel();
        }
    }

    /** The next time (millis) a reminder is due, looking up to a week ahead. -1 if none. */
    static long nextTrigger(StudySession s) {
        long now = System.currentTimeMillis();
        for (int offset = 0; offset <= 7; offset++) {
            Calendar t = Calendar.getInstance();
            t.add(Calendar.DAY_OF_YEAR, offset);
            int dayIndex = t.get(Calendar.DAY_OF_WEEK) - 1;          // 0 = Sunday
            if ((s.daysMask & (1 << dayIndex)) == 0) continue;

            t.set(Calendar.HOUR_OF_DAY, 0);
            t.set(Calendar.MINUTE, 0);
            t.set(Calendar.SECOND, 0);
            t.set(Calendar.MILLISECOND, 0);
            t.add(Calendar.MINUTE, s.startMinutes - s.reminderMinutes);

            if (t.getTimeInMillis() > now) return t.getTimeInMillis();
        }
        return -1;
    }

    private static PendingIntent pending(Context c, int sessionId, int flags) {
        Intent i = new Intent(c, StudyReminderReceiver.class)
                .putExtra(StudyReminderReceiver.EXTRA_ID, sessionId);
        return PendingIntent.getBroadcast(c, REQUEST_BASE + sessionId, i,
                flags | PendingIntent.FLAG_IMMUTABLE);
    }
}
