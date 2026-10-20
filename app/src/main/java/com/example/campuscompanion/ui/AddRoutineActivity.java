package com.example.campuscompanion.ui;

import android.app.TimePickerDialog;
import android.content.Context;
import android.os.Bundle;
import android.widget.ArrayAdapter;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.campuscompanion.data.local.AppDatabase;
import com.example.campuscompanion.data.local.RoutineEntry;
import com.example.campuscompanion.databinding.ActivityAddRoutineBinding;
import com.example.campuscompanion.util.TimeUtil;

import java.util.Calendar;

public class AddRoutineActivity extends AppCompatActivity {

    private ActivityAddRoutineBinding b;
    private int startMinutes = 9 * 60;   // 9:00 AM
    private int endMinutes = 10 * 60;    // 10:00 AM

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        b = ActivityAddRoutineBinding.inflate(getLayoutInflater());
        setContentView(b.getRoot());

        b.spDay.setAdapter(new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_dropdown_item, TimeUtil.dayNames()));
        // Pre-select today
        b.spDay.setSelection(Calendar.getInstance().get(Calendar.DAY_OF_WEEK) - 1);

        refreshButtons();

        b.btnStart.setOnClickListener(v -> new TimePickerDialog(this, (view, h, m) -> {
            startMinutes = h * 60 + m;
            refreshButtons();
        }, startMinutes / 60, startMinutes % 60, false).show());

        b.btnEnd.setOnClickListener(v -> new TimePickerDialog(this, (view, h, m) -> {
            endMinutes = h * 60 + m;
            refreshButtons();
        }, endMinutes / 60, endMinutes % 60, false).show());

        b.btnSave.setOnClickListener(v -> save());
    }

    private void refreshButtons() {
        b.btnStart.setText("Starts " + TimeUtil.fmt(startMinutes));
        b.btnEnd.setText("Ends " + TimeUtil.fmt(endMinutes));
    }

    private void save() {
        String course = b.etCourse.getText() == null ? "" : b.etCourse.getText().toString().trim();
        if (course.isEmpty()) {
            b.tilCourse.setError("Course is required");
            return;
        }
        b.tilCourse.setError(null);
        if (endMinutes <= startMinutes) {
            Toast.makeText(this, "End time must be after start time", Toast.LENGTH_SHORT).show();
            return;
        }

        RoutineEntry e = new RoutineEntry();
        e.course = course;
        e.room = b.etRoom.getText() == null ? "" : b.etRoom.getText().toString().trim();
        e.teacher = b.etTeacher.getText() == null ? "" : b.etTeacher.getText().toString().trim();
        e.dayOfWeek = b.spDay.getSelectedItemPosition() + 1; // Calendar.SUNDAY = 1
        e.startMinutes = startMinutes;
        e.endMinutes = endMinutes;

        Context app = getApplicationContext();
        AppDatabase.executor.execute(() -> {
            AppDatabase.get(app).routineDao().insert(e);
            runOnUiThread(this::finish);
        });
    }
}
