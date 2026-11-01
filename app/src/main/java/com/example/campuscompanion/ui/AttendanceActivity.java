package com.example.campuscompanion.ui;

import android.app.DatePickerDialog;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.example.campuscompanion.R;
import com.example.campuscompanion.adapter.AttendanceAdapter;
import com.example.campuscompanion.data.local.AppDatabase;
import com.example.campuscompanion.data.local.AttendanceCourse;
import com.example.campuscompanion.data.local.AttendanceDao;
import com.example.campuscompanion.data.local.AttendanceRecord;
import com.example.campuscompanion.databinding.ActivityAttendanceBinding;
import com.example.campuscompanion.databinding.DialogAttendanceCourseBinding;
import com.example.campuscompanion.util.AttendanceMath;
import com.example.campuscompanion.util.AttendanceStats;
import com.example.campuscompanion.util.BottomNav;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.snackbar.Snackbar;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Attendance tracker. The student adds courses and marks each class by hand.
 * Everything is stored on this phone in the Room database (no internet needed).
 */
public class AttendanceActivity extends AppCompatActivity implements AttendanceAdapter.Listener {

    private static final String[] STATUS_NAMES = {"Present", "Absent", "Cancelled"};
    private static final SimpleDateFormat DAY =
            new SimpleDateFormat("EEE, dd MMM yyyy", Locale.getDefault());

    private ActivityAttendanceBinding b;
    private AttendanceDao dao;
    private AttendanceAdapter adapter;

    private List<AttendanceCourse> courses = new ArrayList<>();
    private List<AttendanceRecord> records = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        b = ActivityAttendanceBinding.inflate(getLayoutInflater());
        setContentView(b.getRoot());
        BottomNav.attach(this, R.id.nav_attendance);

        dao = AppDatabase.get(this).attendanceDao();

        adapter = new AttendanceAdapter(this);
        b.recycler.setLayoutManager(new LinearLayoutManager(this));
        b.recycler.setAdapter(adapter);

        dao.getCourses().observe(this, list -> {
            courses = list;
            refresh();
        });
        dao.getAllRecords().observe(this, list -> {
            records = list;
            refresh();
        });

        b.fabAdd.setOnClickListener(v -> showCourseDialog(null));
        b.tvRecapLink.setOnClickListener(v -> startActivity(new Intent(this, RecapActivity.class)));
        b.tvExport.setOnClickListener(v -> sharePdf());
    }

    /** Recalculates every card and the two chips at the top. */
    private void refresh() {
        Map<Integer, int[]> counts = AttendanceStats.counts(records);

        List<AttendanceAdapter.Row> rows = new ArrayList<>();
        for (AttendanceCourse c : courses) {
            rows.add(new AttendanceAdapter.Row(c, AttendanceStats.forCourse(c, counts), lastTopic(c)));
        }
        adapter.submitList(rows);
        b.emptyText.setVisibility(courses.isEmpty() ? View.VISIBLE : View.GONE);

        int overall = AttendanceStats.overallPercent(counts);
        b.tvOverall.setVisibility(overall < 0 ? View.GONE : View.VISIBLE);
        b.tvOverall.setText("Overall " + overall + "%");

        int streak = AttendanceStats.streak(records);
        b.tvStreak.setVisibility(streak < 2 ? View.GONE : View.VISIBLE);
        b.tvStreak.setText("🔥 " + streak + " in a row");
    }

    /** Builds the attendance report as a PDF and opens the share sheet. */
    private void sharePdf() {
        if (courses.isEmpty()) {
            Snackbar.make(b.getRoot(), "Add a course first", Snackbar.LENGTH_SHORT)
                    .setAnchorView(b.fabAdd).show();
            return;
        }
        b.tvExport.setEnabled(false);
        final List<AttendanceCourse> cs = new ArrayList<>(courses);
        final List<AttendanceRecord> rs = new ArrayList<>(records);
        Context app = getApplicationContext();
        AppDatabase.executor.execute(() -> {
            try {
                java.io.File pdf = com.example.campuscompanion.util.AttendancePdf.create(
                        app,
                        com.example.campuscompanion.auth.UserSession.name(app),
                        com.example.campuscompanion.auth.UserSession.label(app),
                        cs, rs, AppDatabase.get(app).topicDao().getAllNow());
                android.net.Uri uri = androidx.core.content.FileProvider.getUriForFile(
                        app, app.getPackageName() + ".fileprovider", pdf);
                runOnUiThread(() -> {
                    b.tvExport.setEnabled(true);
                    if (isFinishing() || isDestroyed()) return;
                    Intent send = new Intent(Intent.ACTION_SEND)
                            .setType("application/pdf")
                            .putExtra(Intent.EXTRA_STREAM, uri)
                            .putExtra(Intent.EXTRA_SUBJECT, "My attendance report")
                            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    startActivity(Intent.createChooser(send, "Share attendance report"));
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    b.tvExport.setEnabled(true);
                    if (!isFinishing() && !isDestroyed()) {
                        Snackbar.make(b.getRoot(), "Could not create the PDF: " + e.getMessage(),
                                Snackbar.LENGTH_LONG).setAnchorView(b.fabAdd).show();
                    }
                });
            }
        });
    }

    /** The newest class note of a course (records are newest first), or null. */
    private String lastTopic(AttendanceCourse c) {
        for (AttendanceRecord r : records) {
            if (r.courseId == c.id && r.topic != null && !r.topic.trim().isEmpty()) return r.topic;
        }
        return null;
    }

    // ---------------------------------------------------------------------
    // Marking classes
    // ---------------------------------------------------------------------

    @Override
    public void onMark(AttendanceCourse course, int status) {
        addRecord(course, status, com.example.campuscompanion.util.DateUtil.startOfDay(System.currentTimeMillis()));
    }

    /** Saves one class (Present opens the class-note sheet first), then offers Undo. */
    private void addRecord(AttendanceCourse course, int status, long date) {
        AttendanceActions.mark(this, course, status, date, r ->
                Snackbar.make(b.getRoot(), STATUS_NAMES[status] + " saved for " + course.name
                                + " (" + DAY.format(new Date(date)) + ")", Snackbar.LENGTH_LONG)
                        .setAnchorView(b.fabAdd)
                        .setAction("Undo", v -> AppDatabase.executor.execute(() -> dao.deleteRecord(r)))
                        .show());
    }

    @Override
    public void onPickDate(AttendanceCourse course) {
        Calendar now = Calendar.getInstance();
        DatePickerDialog dialog = new DatePickerDialog(this, (picker, y, m, d) -> {
            Calendar chosen = Calendar.getInstance();
            chosen.set(y, m, d);
            long date = com.example.campuscompanion.util.DateUtil.startOfDay(chosen.getTimeInMillis());
            new MaterialAlertDialogBuilder(this)
                    .setTitle(course.name + "  •  " + DAY.format(new Date(date)))
                    .setItems(STATUS_NAMES, (dlg, which) -> addRecord(course, which, date))
                    .setNegativeButton("Cancel", null)
                    .show();
        }, now.get(Calendar.YEAR), now.get(Calendar.MONTH), now.get(Calendar.DAY_OF_MONTH));
        dialog.getDatePicker().setMaxDate(System.currentTimeMillis());   // no future classes
        dialog.show();
    }

    // ---------------------------------------------------------------------
    // Course menu, history, add / edit / delete
    // ---------------------------------------------------------------------

    @Override
    public void onOpenMenu(AttendanceCourse course) {
        String[] items = {"Topics and notes", "History", "Edit course", "Delete course"};
        new MaterialAlertDialogBuilder(this)
                .setTitle(course.title())
                .setItems(items, (d, which) -> {
                    if (which == 0) openTopics(course);
                    else if (which == 1) showHistory(course);
                    else if (which == 2) showCourseDialog(course);
                    else confirmDelete(course);
                })
                .show();
    }

    @Override
    public void onOpenTopics(AttendanceCourse course) {
        openTopics(course);
    }

    private void openTopics(AttendanceCourse course) {
        startActivity(new Intent(this, TopicsActivity.class)
                .putExtra(TopicsActivity.EXTRA_COURSE_ID, course.id)
                .putExtra(TopicsActivity.EXTRA_COURSE_NAME, course.title()));
    }

    private void showHistory(AttendanceCourse course) {
        List<AttendanceRecord> mine = new ArrayList<>();
        for (AttendanceRecord r : records) if (r.courseId == course.id) mine.add(r);

        if (mine.isEmpty()) {
            new MaterialAlertDialogBuilder(this)
                    .setTitle(course.title())
                    .setMessage("No classes marked yet.")
                    .setPositiveButton("OK", null)
                    .show();
            return;
        }
        String[] lines = new String[mine.size()];
        for (int i = 0; i < lines.length; i++) {
            AttendanceRecord r = mine.get(i);
            lines[i] = DAY.format(new Date(r.date)) + "   \u2022   " + STATUS_NAMES[r.status]
                    + (r.topic == null || r.topic.isEmpty() ? "" : "\n" + r.topic);
        }
        new MaterialAlertDialogBuilder(this)
                .setTitle(course.title() + ": tap a class to change it")
                .setItems(lines, (d, which) -> editRecord(mine.get(which)))
                .setNegativeButton("Close", null)
                .show();
    }

    private void editRecord(AttendanceRecord r) {
        String[] options = {"Change to Present", "Change to Absent", "Change to Cancelled", "Delete this entry"};
        new MaterialAlertDialogBuilder(this)
                .setTitle(DAY.format(new Date(r.date)))
                .setItems(options, (d, which) -> AppDatabase.executor.execute(() -> {
                    if (which == 3) {
                        dao.deleteRecord(r);
                    } else {
                        AttendanceRecord updated = new AttendanceRecord();
                        updated.id = r.id;
                        updated.courseId = r.courseId;
                        updated.date = r.date;
                        updated.status = which;
                        updated.topic = r.topic;
                        dao.updateRecord(updated);
                    }
                }))
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void confirmDelete(AttendanceCourse course) {
        Context app = getApplicationContext();
        new MaterialAlertDialogBuilder(this)
                .setTitle("Delete " + course.name + "?")
                .setMessage("This also deletes all its marked classes.")
                .setPositiveButton("Delete", (d, w) -> AppDatabase.executor.execute(() ->
                        AppDatabase.get(app).runInTransaction(() -> {
                            dao.deleteRecordsOf(course.id);
                            AppDatabase.get(app).topicDao().deleteOf(course.id);
                            dao.deleteCourse(course);
                        })))
                .setNegativeButton("Cancel", null)
                .show();
    }

    /** Add a course (existing == null) or edit one. */
    private void showCourseDialog(AttendanceCourse existing) {
        DialogAttendanceCourseBinding d = DialogAttendanceCourseBinding.inflate(getLayoutInflater());
        if (existing != null) {
            d.etName.setText(existing.name);
            d.etCode.setText(existing.code);
            d.etMin.setText(String.valueOf(existing.minPercent));
            if (existing.totalPlanned > 0) d.etTotal.setText(String.valueOf(existing.totalPlanned));
        }

        final long[] exam = {existing == null ? 0 : existing.examDate};
        d.etExam.setText(exam[0] > 0 ? DAY.format(new Date(exam[0])) : "");
        d.etExam.setOnClickListener(v -> {
            Calendar start = Calendar.getInstance();
            if (exam[0] > 0) start.setTimeInMillis(exam[0]);
            DatePickerDialog picker = new DatePickerDialog(this, (pk, y, m, day) -> {
                Calendar chosen = Calendar.getInstance();
                chosen.set(y, m, day);
                exam[0] = com.example.campuscompanion.util.DateUtil.startOfDay(chosen.getTimeInMillis());
                d.etExam.setText(DAY.format(new Date(exam[0])));
            }, start.get(Calendar.YEAR), start.get(Calendar.MONTH), start.get(Calendar.DAY_OF_MONTH));
            picker.getDatePicker().setMinDate(System.currentTimeMillis() - 1000);
            picker.setButton(DatePickerDialog.BUTTON_NEUTRAL, "Clear", (di, w) -> {
                exam[0] = 0;
                d.etExam.setText("");
            });
            picker.show();
        });

        androidx.appcompat.app.AlertDialog dialog = new MaterialAlertDialogBuilder(this)
                .setTitle(existing == null ? "Add course" : "Edit course")
                .setView(d.getRoot())
                .setPositiveButton("Save", null)   // set below so errors keep the dialog open
                .setNegativeButton("Cancel", null)
                .create();
        dialog.show();

        dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String name = text(d.etName);
            String code = text(d.etCode);
            int min = parseInt(text(d.etMin));
            int total = parseInt(text(d.etTotal));

            d.tilName.setError(null);
            d.tilMin.setError(null);
            if (name.isEmpty()) { d.tilName.setError("Enter the course name"); return; }
            if (min < 1 || min > 100) { d.tilMin.setError("Enter a number from 1 to 100"); return; }

            AttendanceCourse c = new AttendanceCourse();
            c.name = name;
            c.code = code;
            c.minPercent = min;
            c.totalPlanned = Math.max(0, total);
            c.examDate = exam[0];

            if (existing == null) {
                c.createdAt = System.currentTimeMillis();
                AppDatabase.executor.execute(() -> dao.insertCourse(c));
            } else {
                c.id = existing.id;
                c.createdAt = existing.createdAt;
                AppDatabase.executor.execute(() -> dao.updateCourse(c));
            }
            dialog.dismiss();
        });
    }

    private static String text(android.widget.TextView t) {
        return t.getText() == null ? "" : t.getText().toString().trim();
    }

    private static int parseInt(String s) {
        try {
            return Integer.parseInt(s);
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
