package com.example.campuscompanion.ui;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.widget.Toast;

import androidx.core.content.FileProvider;

import com.example.campuscompanion.data.local.AppDatabase;
import com.example.campuscompanion.util.IcsBuilder;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;

/** "Add to calendar": builds a .ics file and opens the share sheet (Google Calendar, Outlook ...). */
public final class CalendarExport {
    private CalendarExport() { }

    public static void show(Activity activity) {
        final String[] items = {"Deadlines (not done yet)", "Exams", "Weekly class routine"};
        final boolean[] on = {true, true, true};
        new MaterialAlertDialogBuilder(activity)
                .setTitle("Add to calendar")
                .setMultiChoiceItems(items, on, (d, which, checked) -> on[which] = checked)
                .setPositiveButton("Export", (d, w) -> export(activity, on[0], on[1], on[2]))
                .setNegativeButton("Cancel", null)
                .show();
    }

    private static void export(Activity activity, boolean deadlines, boolean exams, boolean routine) {
        if (!deadlines && !exams && !routine) return;
        Context app = activity.getApplicationContext();
        AppDatabase.executor.execute(() -> {
            AppDatabase db = AppDatabase.get(app);
            IcsBuilder ics = new IcsBuilder();
            if (deadlines) ics.deadlines(db.deadlineDao().getAllNow());
            if (exams) ics.exams(db.examDao().getAllNow());
            if (routine) ics.routine(db.routineDao().getAllNow());

            if (ics.count() == 0) {
                activity.runOnUiThread(() -> Toast.makeText(activity,
                        "Nothing to export yet", Toast.LENGTH_SHORT).show());
                return;
            }
            try {
                File dir = new File(app.getCacheDir(), "exports");
                if (!dir.exists() && !dir.mkdirs()) throw new java.io.IOException("Cannot create folder");
                File file = new File(dir, "campus_companion.ics");
                try (FileOutputStream out = new FileOutputStream(file)) {
                    out.write(ics.build().getBytes(StandardCharsets.UTF_8));
                }
                Uri uri = FileProvider.getUriForFile(app, app.getPackageName() + ".fileprovider", file);
                Intent send = new Intent(Intent.ACTION_SEND)
                        .setType("text/calendar")
                        .putExtra(Intent.EXTRA_STREAM, uri)
                        .putExtra(Intent.EXTRA_SUBJECT, "Campus Companion calendar")
                        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                activity.runOnUiThread(() -> {
                    if (activity.isFinishing() || activity.isDestroyed()) return;
                    activity.startActivity(Intent.createChooser(send, "Add to calendar"));
                });
            } catch (Exception e) {
                activity.runOnUiThread(() -> Toast.makeText(activity,
                        "Could not create the calendar file: " + e.getMessage(), Toast.LENGTH_LONG).show());
            }
        });
    }
}
