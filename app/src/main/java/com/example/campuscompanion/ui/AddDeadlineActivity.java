package com.example.campuscompanion.ui;

import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.content.Context;
import android.os.Bundle;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.campuscompanion.auth.UserSession;
import com.example.campuscompanion.data.local.AppDatabase;
import com.example.campuscompanion.data.local.Deadline;
import com.example.campuscompanion.databinding.ActivityAddDeadlineBinding;
import com.example.campuscompanion.reminder.ReminderScheduler;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * One form, two uses:
 *  - normal: saves a personal deadline in Room and schedules its reminder
 *  - EXTRA_POST_TO_SECTION (class representative): posts it to Firestore for the whole section
 */
public class AddDeadlineActivity extends AppCompatActivity {

    public static final String EXTRA_POST_TO_SECTION = "post_to_section";

    private static final String[] PRIORITY_LABELS = {"High", "Medium", "Low"};
    private static final String[] PRIORITY_VALUES = {Deadline.HIGH, Deadline.MEDIUM, Deadline.LOW};
    private static final String[] REMIND_LABELS = {"1 day before", "1 hour before", "At due time", "No reminder"};
    private static final int[] REMIND_MINUTES = {1440, 60, 0, -1};

    private final SimpleDateFormat dateFmt = new SimpleDateFormat("EEE, dd MMM yyyy", Locale.getDefault());
    private final SimpleDateFormat timeFmt = new SimpleDateFormat("hh:mm a", Locale.getDefault());
    private final Calendar cal = Calendar.getInstance();

    private ActivityAddDeadlineBinding b;
    private boolean toSection;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        b = ActivityAddDeadlineBinding.inflate(getLayoutInflater());
        setContentView(b.getRoot());

        toSection = getIntent().getBooleanExtra(EXTRA_POST_TO_SECTION, false) && UserSession.isCr(this);
        if (toSection) {
            b.tvHeading.setText("Post to section");
            b.tvRemindLabel.setVisibility(View.GONE);   // each student gets their own default reminder
            b.spRemind.setVisibility(View.GONE);
            b.btnSave.setText("Post to section");
        }

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

        String course = b.etCourse.getText() == null ? "" : b.etCourse.getText().toString().trim();
        String note = b.etNote.getText() == null ? "" : b.etNote.getText().toString().trim();
        String priority = PRIORITY_VALUES[b.spPriority.getSelectedItemPosition()];
        long dueAt = cal.getTimeInMillis();

        if (toSection) {
            postToSection(title, course, note, priority, dueAt);
        } else {
            savePersonal(title, course, note, priority, dueAt);
        }
    }

    private void savePersonal(String title, String course, String note, String priority, long dueAt) {
        Deadline d = new Deadline();
        d.title = title;
        d.course = course;
        d.note = note;
        d.priority = priority;
        d.dueAt = dueAt;
        d.remindBeforeMinutes = REMIND_MINUTES[b.spRemind.getSelectedItemPosition()];

        Context app = getApplicationContext();
        AppDatabase.executor.execute(() -> {
            long newId = AppDatabase.get(app).deadlineDao().insert(d);
            d.id = (int) newId;
            ReminderScheduler.schedule(app, d);
            runOnUiThread(() -> {
                Toast.makeText(this, "Deadline saved", Toast.LENGTH_SHORT).show();
                finish();
            });
        });
    }

    private void postToSection(String title, String course, String note, String priority, long dueAt) {
        String section = UserSession.section(this);
        if (section == null) {
            Toast.makeText(this, "No section found. Please log in again.", Toast.LENGTH_LONG).show();
            return;
        }

        Map<String, Object> doc = new HashMap<>();
        doc.put("title", title);
        doc.put("course", course);
        doc.put("note", note);
        doc.put("priority", priority);
        doc.put("dueAt", dueAt);
        doc.put("createdBy", FirebaseAuth.getInstance().getUid());
        doc.put("createdAt", FieldValue.serverTimestamp());

        Context app = getApplicationContext();
        FirebaseFirestore.getInstance()
                .collection("sections").document(section).collection("deadlines")
                .add(doc)
                .addOnFailureListener(e -> Toast.makeText(app,
                        "Post failed: " + e.getMessage(), Toast.LENGTH_LONG).show());

        // Firestore queues the write offline, so don't block the screen waiting for the server
        Toast.makeText(this, "Posting to section...", Toast.LENGTH_SHORT).show();
        finish();
    }
}
