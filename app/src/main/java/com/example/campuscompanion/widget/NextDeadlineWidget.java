package com.example.campuscompanion.widget;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.widget.RemoteViews;

import com.example.campuscompanion.MainActivity;
import com.example.campuscompanion.R;
import com.example.campuscompanion.data.local.AppDatabase;
import com.example.campuscompanion.data.local.Deadline;
import com.example.campuscompanion.util.TimeUtil;
import com.google.firebase.auth.FirebaseAuth;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/** Home screen widget: next deadline, time left, overdue count and the next class today. */
public class NextDeadlineWidget extends AppWidgetProvider {

    private static final long HOUR = 60L * 60 * 1000;
    private static final long DAY = 24 * HOUR;

    @Override
    public void onUpdate(Context context, AppWidgetManager manager, int[] appWidgetIds) {
        // The database must not be read on the main thread
        PendingResult result = goAsync();
        Context app = context.getApplicationContext();
        AppDatabase.executor.execute(() -> {
            try {
                RemoteViews views = build(app);
                for (int id : appWidgetIds) manager.updateAppWidget(id, views);
            } finally {
                result.finish();
            }
        });
    }

    /** Ask every placed widget to redraw (call after data changes or login/logout). */
    public static void refresh(Context context) {
        Context app = context.getApplicationContext();
        AppWidgetManager manager = AppWidgetManager.getInstance(app);
        int[] ids = manager.getAppWidgetIds(new ComponentName(app, NextDeadlineWidget.class));
        if (ids == null || ids.length == 0) return;
        Intent i = new Intent(app, NextDeadlineWidget.class)
                .setAction(AppWidgetManager.ACTION_APPWIDGET_UPDATE)
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids);
        app.sendBroadcast(i);
    }

    private static RemoteViews build(Context c) {
        RemoteViews v = new RemoteViews(c.getPackageName(), R.layout.widget_next_deadline);

        PendingIntent open = PendingIntent.getActivity(c, 0, new Intent(c, MainActivity.class),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        v.setOnClickPendingIntent(R.id.widgetRoot, open);

        // Signed out: never show the previous account's data
        if (FirebaseAuth.getInstance().getCurrentUser() == null) {
            v.setTextViewText(R.id.wDeadline, "Sign in to see your deadlines");
            v.setTextViewText(R.id.wDue, "");
            v.setTextViewText(R.id.wClass, "");
            return v;
        }

        long now = System.currentTimeMillis();
        List<Deadline> pending = AppDatabase.get(c).deadlineDao().getPending();
        Deadline next = null;
        int overdue = 0;
        for (Deadline d : pending) {
            if (d.dueAt < now) overdue++;
            else if (next == null || d.dueAt < next.dueAt) next = d;
        }

        if (next == null) {
            v.setTextViewText(R.id.wDeadline, "No upcoming deadlines");
            v.setTextViewText(R.id.wDue, overdue > 0 ? overdue + " overdue" : "You're all caught up");
        } else {
            String course = (next.course == null || next.course.isEmpty()) ? "" : next.course + "  \u2022  ";
            String due = new SimpleDateFormat("EEE, dd MMM  hh:mm a", Locale.getDefault())
                    .format(new Date(next.dueAt));
            String line = course + due + "  \u2022  in " + left(next.dueAt - now);
            if (overdue > 0) line += "  \u2022  " + overdue + " overdue";
            v.setTextViewText(R.id.wDeadline, next.title);
            v.setTextViewText(R.id.wDue, line);
        }

        v.setTextViewText(R.id.wClass, nextClassToday(c));
        return v;
    }

    private static String left(long ms) {
        if (ms >= DAY) return (ms / DAY) + "d " + ((ms % DAY) / HOUR) + "h";
        if (ms >= HOUR) return (ms / HOUR) + "h " + ((ms % HOUR) / 60_000L) + "m";
        return Math.max(1, ms / 60_000L) + "m";
    }

    private static String nextClassToday(Context c) {
        Calendar cal = Calendar.getInstance();
        int day = cal.get(Calendar.DAY_OF_WEEK);
        int nowMin = cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE);

        try (Cursor cur = AppDatabase.get(c).getOpenHelper().getReadableDatabase().query(
                "SELECT course, startMinutes FROM routine WHERE dayOfWeek = ? AND startMinutes >= ? "
                        + "ORDER BY startMinutes ASC LIMIT 1",
                new Object[]{day, nowMin})) {
            if (cur.moveToFirst()) {
                return "Next class: " + cur.getString(0) + " at " + TimeUtil.fmt(cur.getInt(1));
            }
        } catch (Exception ignored) { }
        return "No more classes today";
    }
}
