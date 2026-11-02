package com.example.campuscompanion.ui;

import android.app.DatePickerDialog;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;

import androidx.appcompat.app.AppCompatActivity;

import com.example.campuscompanion.R;
import com.example.campuscompanion.data.local.AppDatabase;
import com.example.campuscompanion.data.local.Exam;
import com.example.campuscompanion.data.local.ExamDao;
import com.example.campuscompanion.databinding.ActivityExamsBinding;
import com.example.campuscompanion.databinding.DialogExamBinding;
import com.example.campuscompanion.databinding.ItemExamBinding;
import com.example.campuscompanion.util.DateUtil;
import com.example.campuscompanion.util.ExamUtil;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/** The student's own exam list. Exams are typed in by hand and are separate from attendance. */
public class ExamsActivity extends AppCompatActivity {

    private static final SimpleDateFormat DAY = new SimpleDateFormat("EEE, d MMM yyyy", Locale.getDefault());

    private ActivityExamsBinding b;
    private ExamDao dao;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        b = ActivityExamsBinding.inflate(getLayoutInflater());
        setContentView(b.getRoot());

        dao = AppDatabase.get(this).examDao();
        dao.getAll().observe(this, this::render);
        b.fabAdd.setOnClickListener(v -> showDialog(null));
    }

    private void render(List<Exam> exams) {
        b.list.removeAllViews();
        b.emptyText.setVisibility(exams.isEmpty() ? View.VISIBLE : View.GONE);

        // upcoming first (nearest first), then the ones that have passed
        for (Exam e : exams) if (ExamUtil.daysLeft(e.examDate) >= 0) b.list.addView(row(e));
        for (Exam e : exams) if (ExamUtil.daysLeft(e.examDate) < 0) b.list.addView(row(e));
    }

    private View row(Exam e) {
        ItemExamBinding r = ItemExamBinding.inflate(LayoutInflater.from(this), b.list, false);
        int days = ExamUtil.daysLeft(e.examDate);
        boolean passed = days < 0;

        r.tvExamSubject.setText(e.subject);
        r.tvExamDate.setText(DAY.format(new Date(e.examDate))
                + (e.note == null || e.note.isEmpty() ? "" : "  •  " + e.note));
        r.tvExamLeft.setText(passed ? "Passed" : ExamUtil.label(days));
        r.examCard.setStrokeColor(getColor(passed ? R.color.home_stats : R.color.home_deadline));
        r.examCard.setAlpha(passed ? 0.6f : 1f);

        r.examCard.setOnClickListener(v -> showDialog(e));
        r.examCard.setOnLongClickListener(v -> {
            new MaterialAlertDialogBuilder(this)
                    .setTitle("Delete this exam?")
                    .setMessage(e.subject)
                    .setPositiveButton("Delete", (d, w) -> {
                        AppDatabase.executor.execute(() -> dao.delete(e));
                        com.google.android.material.snackbar.Snackbar.make(b.getRoot(), "Exam deleted", com.google.android.material.snackbar.Snackbar.LENGTH_LONG)
                                .setAnchorView(b.fabAdd)
                                .setAction("Undo", u -> AppDatabase.executor.execute(() -> dao.insert(e)))
                                .show();
                    })
                    .setNegativeButton("Cancel", null)
                    .show();
            return true;
        });
        return r.getRoot();
    }

    /** Add (existing == null) or edit an exam. */
    private void showDialog(Exam existing) {
        DialogExamBinding d = DialogExamBinding.inflate(getLayoutInflater());
        final long[] date = {existing == null ? 0 : existing.examDate};
        if (existing != null) {
            d.etExamSubject.setText(existing.subject);
            d.etExamNote.setText(existing.note);
            d.etExamDate.setText(DAY.format(new Date(date[0])));
        }

        d.etExamDate.setOnClickListener(v -> {
            Calendar start = Calendar.getInstance();
            if (date[0] > 0) start.setTimeInMillis(date[0]);
            new DatePickerDialog(this, (pk, y, m, day) -> {
                Calendar chosen = Calendar.getInstance();
                chosen.set(y, m, day);
                date[0] = DateUtil.startOfDay(chosen.getTimeInMillis());
                d.etExamDate.setText(DAY.format(new Date(date[0])));
                d.tilExamDate.setError(null);
            }, start.get(Calendar.YEAR), start.get(Calendar.MONTH), start.get(Calendar.DAY_OF_MONTH)).show();
        });

        androidx.appcompat.app.AlertDialog dialog = new MaterialAlertDialogBuilder(this)
                .setTitle(existing == null ? "Add exam" : "Edit exam")
                .setView(d.getRoot())
                .setPositiveButton("Save", null)
                .setNegativeButton("Cancel", null)
                .create();
        dialog.show();

        dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String subject = d.etExamSubject.getText() == null ? "" : d.etExamSubject.getText().toString().trim();
            String note = d.etExamNote.getText() == null ? "" : d.etExamNote.getText().toString().trim();
            d.tilExamSubject.setError(null);
            d.tilExamDate.setError(null);
            if (subject.isEmpty()) { d.tilExamSubject.setError("Enter the subject"); return; }
            if (date[0] <= 0) { d.tilExamDate.setError("Pick the exam date"); return; }

            Exam e = new Exam();
            e.subject = subject;
            e.examDate = date[0];
            e.note = note;
            if (existing == null) {
                AppDatabase.executor.execute(() -> dao.insert(e));
            } else {
                e.id = existing.id;
                AppDatabase.executor.execute(() -> dao.update(e));
            }
            dialog.dismiss();
        });
    }
}
