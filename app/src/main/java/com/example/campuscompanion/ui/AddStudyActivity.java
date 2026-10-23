package com.example.campuscompanion.ui;

import android.app.TimePickerDialog;
import android.content.Context;
import android.os.Bundle;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.campuscompanion.R;
import com.example.campuscompanion.data.local.AppDatabase;
import com.example.campuscompanion.data.local.StudyDao;
import com.example.campuscompanion.data.local.StudySession;
import com.example.campuscompanion.databinding.ActivityAddStudyBinding;
import com.example.campuscompanion.reminder.StudyReminderScheduler;
import com.example.campuscompanion.util.TimeUtil;
import com.google.android.material.chip.Chip;

/** Create or edit one study session. Pass EXTRA_ID to edit. */
public class AddStudyActivity extends AppCompatActivity {

    public static final String EXTRA_ID = "study_id";

    private static final int[] DAY_CHIPS = {
            R.id.chipSun, R.id.chipMon, R.id.chipTue, R.id.chipWed,
            R.id.chipThu, R.id.chipFri, R.id.chipSat};

    private ActivityAddStudyBinding b;
    private StudyDao dao;
    private int editId = -1;
    private int startMin = 10 * 60;
    private int endMin = 11 * 60;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        b = ActivityAddStudyBinding.inflate(getLayoutInflater());
        setContentView(b.getRoot());

        dao = AppDatabase.get(this).studyDao();
        editId = getIntent().getIntExtra(EXTRA_ID, -1);

        b.btnStart.setOnClickListener(v -> pickTime(true));
        b.btnEnd.setOnClickListener(v -> pickTime(false));
        b.btnEveryDay.setOnClickListener(v -> setDays(127));
        b.btnSunThu.setOnClickListener(v -> setDays(31));
        b.btnSave.setOnClickListener(v -> save());

        updateTimeButtons();

        if (editId >= 0) {
            b.tvTitle.setText("Edit study session");
            AppDatabase.executor.execute(() -> {
                StudySession s = dao.getById(editId);
                if (s != null) runOnUiThread(() -> fill(s));
            });
        }
    }

    private void fill(StudySession s) {
        b.etSubject.setText(s.subject);
        b.etTopic.setText(s.topic);
        setDays(s.daysMask);
        startMin = s.startMinutes;
        endMin = s.endMinutes;
        updateTimeButtons();
        selectReminder(s.reminderMinutes);
    }

    private void setDays(int mask) {
        for (int i = 0; i < 7; i++) {
            ((Chip) findViewById(DAY_CHIPS[i])).setChecked((mask & (1 << i)) != 0);
        }
    }

    private int readDays() {
        int mask = 0;
        for (int i = 0; i < 7; i++) {
            if (((Chip) findViewById(DAY_CHIPS[i])).isChecked()) mask |= 1 << i;
        }
        return mask;
    }

    private void selectReminder(int minutes) {
        for (int i = 0; i < b.chipsRemind.getChildCount(); i++) {
            Chip chip = (Chip) b.chipsRemind.getChildAt(i);
            if (Integer.parseInt(String.valueOf(chip.getTag())) == minutes) {
                chip.setChecked(true);
                return;
            }
        }
    }

    private int readReminder() {
        int id = b.chipsRemind.getCheckedChipId();
        Chip chip = id == -1 ? null : (Chip) findViewById(id);
        return chip == null ? 0 : Integer.parseInt(String.valueOf(chip.getTag()));
    }

    private void pickTime(boolean start) {
        int current = start ? startMin : endMin;
        new TimePickerDialog(this, (view, hour, minute) -> {
            if (start) {
                startMin = hour * 60 + minute;
                if (endMin <= startMin) endMin = Math.min(startMin + 60, 23 * 60 + 59);
            } else {
                endMin = hour * 60 + minute;
            }
            updateTimeButtons();
        }, current / 60, current % 60, false).show();
    }

    private void updateTimeButtons() {
        b.btnStart.setText("Start  " + TimeUtil.fmt(startMin));
        b.btnEnd.setText("End  " + TimeUtil.fmt(endMin));
    }

    private void save() {
        String subject = b.etSubject.getText() == null ? "" : b.etSubject.getText().toString().trim();
        String topic = b.etTopic.getText() == null ? "" : b.etTopic.getText().toString().trim();
        int days = readDays();

        b.tilSubject.setError(null);
        if (subject.isEmpty()) {
            b.tilSubject.setError("Enter a subject, e.g. CSE 2209");
            return;
        }
        if (days == 0) {
            Toast.makeText(this, "Pick at least one day", Toast.LENGTH_SHORT).show();
            return;
        }
        if (endMin <= startMin) {
            Toast.makeText(this, "End time must be after the start time", Toast.LENGTH_SHORT).show();
            return;
        }

        StudySession s = new StudySession();
        s.subject = subject;
        s.topic = topic;
        s.daysMask = days;
        s.startMinutes = startMin;
        s.endMinutes = endMin;
        s.reminderMinutes = readReminder();
        s.enabled = true;

        Context app = getApplicationContext();
        AppDatabase.executor.execute(() -> {
            if (editId >= 0) {
                s.id = editId;
                dao.update(s);
            } else {
                s.id = (int) dao.insert(s);
            }
            StudyReminderScheduler.schedule(app, s);
            runOnUiThread(this::finish);
        });
    }
}
