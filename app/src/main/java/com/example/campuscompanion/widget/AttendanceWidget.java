package com.example.campuscompanion.widget;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import android.widget.RemoteViews;
import android.widget.Toast;

import com.example.campuscompanion.MainActivity;
import com.example.campuscompanion.R;
import com.example.campuscompanion.data.local.AppDatabase;
import com.example.campuscompanion.data.local.AttendanceCourse;
import com.example.campuscompanion.data.local.AttendanceRecord;
import com.example.campuscompanion.util.AttendanceMath;
import com.example.campuscompanion.util.AttendanceStats;
import com.example.campuscompanion.util.DateUtil;
import com.google.firebase.auth.FirebaseAuth;

import java.util.List;
import java.util.Map;

/** Home screen widget: the course that needs attention, with a one-tap "Present today". */
public class AttendanceWidget extends AppWidgetProvider {

    private static final String ACTION_PRESENT = "com.example.campuscompanion.widget.MARK_PRESENT";
    private static final String EXTRA_COURSE = "course_id";

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

    @Override
    public void onReceive(Context context, Intent intent) {
        if (!ACTION_PRESENT.equals(intent.getAction())) {
            super.onReceive(context, intent);
            return;
        }
        if (FirebaseAuth.getInstance().getCurrentUser() == null) return;   // signed out: do nothing
        int courseId = intent.getIntExtra(EXTRA_COURSE, -1);
        PendingResult result = goAsync();
        Context app = context.getApplicationContext();
        AppDatabase.executor.execute(() -> {
            String message;
            try {
                AppDatabase db = AppDatabase.get(app);
                AttendanceCourse c = db.attendanceDao().courseNow(courseId);
                long today = DateUtil.startOfDay(System.currentTimeMillis());
                boolean already = false;
                if (c != null) {
                    for (AttendanceRecord r : db.attendanceDao().recordsOfNow(courseId)) {
                        if (r.date == today) { already = true; break; }
                    }
                }
                if (c == null) {
                    message = "Course not found";
                } else if (already) {
                    message = "Already marked today for " + c.name;
                } else {
                    AttendanceRecord r = new AttendanceRecord();
                    r.courseId = courseId;
                    r.date = today;
                    r.status = AttendanceRecord.PRESENT;
                    db.attendanceDao().insertRecord(r);
                    message = "Marked present: " + c.name;
                }
            } finally {
                result.finish();
            }
            final String shown = message;
            new Handler(Looper.getMainLooper()).post(() ->
                    Toast.makeText(app, shown, Toast.LENGTH_SHORT).show());
        });
    }

    /** Ask every placed widget to redraw. */
    public static void refresh(Context context) {
        Context app = context.getApplicationContext();
        AppWidgetManager manager = AppWidgetManager.getInstance(app);
        int[] ids = manager.getAppWidgetIds(new ComponentName(app, AttendanceWidget.class));
        if (ids == null || ids.length == 0) return;
        app.sendBroadcast(new Intent(app, AttendanceWidget.class)
                .setAction(AppWidgetManager.ACTION_APPWIDGET_UPDATE)
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids));
    }

    private static RemoteViews build(Context c) {
        RemoteViews v = new RemoteViews(c.getPackageName(), R.layout.widget_attendance);
        v.setOnClickPendingIntent(R.id.attRoot, PendingIntent.getActivity(c, 2,
                MainActivity.tabIntent(c, R.id.nav_attendance),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE));

        if (FirebaseAuth.getInstance().getCurrentUser() == null) {
            v.setTextViewText(R.id.wAttCourse, "Sign in to see attendance");
            v.setTextViewText(R.id.wAttDetail, "");
            v.setViewVisibility(R.id.wAttPresent, android.view.View.GONE);
            return v;
        }

        AppDatabase db = AppDatabase.get(c);
        List<AttendanceCourse> courses = db.attendanceDao().getCoursesNow();
        if (courses.isEmpty()) {
            v.setTextViewText(R.id.wAttCourse, "No courses yet");
            v.setTextViewText(R.id.wAttDetail, "Open the app and add your first course");
            v.setViewVisibility(R.id.wAttPresent, android.view.View.GONE);
            return v;
        }

        Map<Integer, int[]> counts = AttendanceStats.counts(db.attendanceDao().getRecordsNow());
        AttendanceCourse pick = null;
        AttendanceMath.Result pickR = null;
        for (AttendanceCourse course : courses) {
            AttendanceMath.Result r = AttendanceStats.forCourse(course, counts);
            if (pick == null
                    || (r.held > 0 && (pickR.held == 0 || r.percent - r.minPercent < pickR.percent - pickR.minPercent))) {
                pick = course;
                pickR = r;
            }
        }
        v.setTextViewText(R.id.wAttCourse, pick.title() + (pickR.held == 0 ? "" : "  " + pickR.percent + "%"));
        v.setTextViewText(R.id.wAttDetail, pickR.advice);
        v.setViewVisibility(R.id.wAttPresent, android.view.View.VISIBLE);

        Intent mark = new Intent(c, AttendanceWidget.class)
                .setAction(ACTION_PRESENT)
                .putExtra(EXTRA_COURSE, pick.id);
        v.setOnClickPendingIntent(R.id.wAttPresent, PendingIntent.getBroadcast(c, pick.id, mark,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE));
        return v;
    }
}
