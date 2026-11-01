package com.example.campuscompanion.widget;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.widget.RemoteViews;

import com.example.campuscompanion.R;
import com.example.campuscompanion.data.local.AppDatabase;
import com.example.campuscompanion.data.local.Exam;
import com.example.campuscompanion.data.local.RoutineEntry;
import com.example.campuscompanion.data.local.Topic;
import com.example.campuscompanion.ui.DayActivity;
import com.example.campuscompanion.util.DateUtil;
import com.example.campuscompanion.util.ExamUtil;
import com.example.campuscompanion.util.TimeUtil;
import com.google.firebase.auth.FirebaseAuth;

import java.util.List;

/** Home screen widget: today's classes, topics to revise and the nearest exam. */
public class TodayWidget extends AppWidgetProvider {

    @Override
    public void onUpdate(Context context, AppWidgetManager manager, int[] appWidgetIds) {
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

    /** Ask every placed widget to redraw. */
    public static void refresh(Context context) {
        Context app = context.getApplicationContext();
        AppWidgetManager manager = AppWidgetManager.getInstance(app);
        int[] ids = manager.getAppWidgetIds(new ComponentName(app, TodayWidget.class));
        if (ids == null || ids.length == 0) return;
        Intent i = new Intent(app, TodayWidget.class)
                .setAction(AppWidgetManager.ACTION_APPWIDGET_UPDATE)
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids);
        app.sendBroadcast(i);
    }

    private static RemoteViews build(Context c) {
        RemoteViews v = new RemoteViews(c.getPackageName(), R.layout.widget_today);
        PendingIntent open = PendingIntent.getActivity(c, 1, new Intent(c, DayActivity.class),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        v.setOnClickPendingIntent(R.id.todayRoot, open);

        // Signed out: never show the previous account's data
        if (FirebaseAuth.getInstance().getCurrentUser() == null) {
            v.setTextViewText(R.id.wTodayClasses, "Sign in to see your day");
            v.setTextViewText(R.id.wTodayRevise, "");
            v.setTextViewText(R.id.wTodayExam, "");
            return v;
        }

        AppDatabase db = AppDatabase.get(c);
        long now = System.currentTimeMillis();
        int nowMin = DateUtil.minutesOfDay(now);

        // ---- classes that have not finished yet ----
        StringBuilder sb = new StringBuilder();
        int shown = 0, total = 0;
        for (RoutineEntry e : db.routineDao().getForDayNow(DateUtil.dayOfWeek(now))) {
            total++;
            if (e.endMinutes <= nowMin) continue;
            if (shown < 3) {
                if (sb.length() > 0) sb.append("\n");
                sb.append(e.startMinutes <= nowMin ? "Now  " : TimeUtil.fmt(e.startMinutes) + "  ")
                        .append(e.course);
                if (e.room != null && !e.room.isEmpty()) sb.append("  (").append(e.room).append(")");
            }
            shown++;
        }
        if (shown > 3) sb.append("\n+ ").append(shown - 3).append(" more");
        v.setTextViewText(R.id.wTodayClasses,
                total == 0 ? "No classes today" : shown == 0 ? "No more classes today" : sb.toString());

        // ---- revision ----
        List<Topic> due = db.topicDao().getDueNow(DateUtil.endOfDay(now));
        v.setTextViewText(R.id.wTodayRevise, due.isEmpty() ? "No revision due"
                : due.size() + (due.size() == 1 ? " topic" : " topics") + " to revise");

        // ---- nearest exam ----
        Exam best = null;
        int bestDays = Integer.MAX_VALUE;
        for (Exam x : db.examDao().getAllNow()) {
            int d = ExamUtil.daysLeft(x.examDate);
            if (d >= 0 && d < bestDays) {
                best = x;
                bestDays = d;
            }
        }
        v.setTextViewText(R.id.wTodayExam, best == null ? ""
                : "Exam: " + best.subject + " \u2022 " + ExamUtil.label(bestDays));
        return v;
    }
}
