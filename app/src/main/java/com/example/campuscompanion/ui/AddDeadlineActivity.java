package com.example.campuscompanion.ui;

import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.content.Context;
import android.os.Bundle;
import android.widget.ArrayAdapter;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.campuscompanion.data.local.AppDatabase;
import com.example.campuscompanion.data.local.Deadline;
import com.example.campuscompanion.databinding.ActivityAddDeadlineBinding;
import com.example.campuscompanion.reminder.ReminderScheduler;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Locale;

public class AddDeadlineActivity extends AppCompatActivity {

    private static final String[] PRIORITY_LABELS = {"High", "Medium", "Low"};
    private static final String[] PRIORITY_VALUES = {Deadline.HIGH, Deadline.MEDIUM, Deadline.LOW};
    private static final String[] REMIND_LABELS = {"1 day before", "1 hour before", "At due time", "No reminder"};
    private static final int[] REMIND_MINUTES = {1440, 60, 0, -1};

    private final SimpleDateFormat dateFmt = new SimpleDateFormat("EEE, dd MMM yyyy", Locale.getDefault());
    private final SimpleDateFormat timeFmt = new SimpleDateFormat("hh:mm a", Locale.getDefault());
    private final Calendar cal = Calendar.getInstance();

    private ActivityAddDeadlineBinding b;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        b = ActivityAddDeadlineBinding.inflate(getLayoutInflater());
        setContentView(b.getRoot());

        // Default: tomorrow, same time
        cal.add(Calendar.DAY_OF_MONTH, 1);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);

        b.spPriority.setAdapter(new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_dropdown_item, PRIORITY_LABELS));
        b.spPriority.setSelection(1);
        b.spRemind.setAdapter(new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_dropdown_item, REMIND_LABELS));

        refreshButtons();

        b.btnDate.setOnClickListener(v -> new DatePickerDialog(this, (view, y, m, d) -> {
            cal.set(y, m, d);
            refreshButtons();
        }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show());

        b.btnTime.setOnClickListener(v -> new TimePickerDialog(this, (view, h, min) -> {
            cal.set(Calendar.HOUR_OF_DAY, h);
            cal.set(Calendar.MINUTE, min);
            refreshButtons();
        }, cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE), false).show());

        b.btnSave.setOnClickListener(v -> save());
    }

    private void refreshButtons() {
        b.btnDate.setText(dateFmt.format(cal.getTime()));
        b.btnTime.setText(timeFmt.format(cal.getTime()));
    }

    private void save() {
        String title = b.etTitle.getText() == null ? "" : b.etTitle.getText().toString().trim();
        if (title.isEmpty()) {
            b.tilTitle.setError("Title is required");
            return;
        }
        b.tilTitle.setError(null);

        Deadline d = new Deadline();
        d.title = title;
        d.course = b.etCourse.getText() == null ? "" : b.etCourse.getText().toString().trim();
        d.note = b.etNote.getText() == null ? "" : b.etNote.getText().toString().trim();
        d.priority = PRIORITY_VALUES[b.spPriority.getSelectedItemPosition()];
        d.dueAt = cal.getTimeInMillis();
        d.remindBeforeMinutes = REMIND_MINUTES[b.spRemind.getSelectedItemPosition()];

        Context app = getApplicationContext();
        AppDatabase.executor.execute(() -> {
            long newId = AppDatabase.get(app).deadlineDao().insert(d);
            d.id = (int) newId;
            ReminderScheduler.schedule(app, d); // alarm uses the new id as its request code

            runOnUiThread(() -> {
                Toast.makeText(this, "Deadline saved", Toast.LENGTH_SHORT).show();
                finish();
            });
        });
    }
}
