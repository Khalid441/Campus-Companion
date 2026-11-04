package com.example.campuscompanion.ui;

import android.content.Context;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.CheckBox;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.FileProvider;

import com.example.campuscompanion.R;
import com.example.campuscompanion.data.local.AppDatabase;
import com.example.campuscompanion.data.local.Deadline;
import com.example.campuscompanion.data.local.RoutineEntry;
import com.example.campuscompanion.databinding.ActivityScanBinding;
import com.example.campuscompanion.reminder.ReminderScheduler;
import com.example.campuscompanion.util.ScanParser;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.latin.TextRecognizerOptions;

import java.io.File;
import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/** Photograph a routine or a notice; on-device OCR proposes entries the student confirms. */
public class ScanActivity extends AppCompatActivity {

    private static final String[] DAY = {"", "Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat"};

    private ActivityScanBinding b;
    private Uri cameraUri;
    private final List<CheckBox> boxes = new ArrayList<>();
    private final List<Object> items = new ArrayList<>();

    private final ActivityResultLauncher<Uri> takePicture =
            registerForActivityResult(new ActivityResultContracts.TakePicture(), ok -> {
                if (ok && cameraUri != null) recognise(cameraUri);
            });
    private final ActivityResultLauncher<String> pickImage =
            registerForActivityResult(new ActivityResultContracts.GetContent(), uri -> {
                if (uri != null) recognise(uri);
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        b = ActivityScanBinding.inflate(getLayoutInflater());
        setContentView(b.getRoot());

        b.btnCamera.setOnClickListener(v -> {
            try {
                File dir = new File(getCacheDir(), "exports");
                if (!dir.exists()) dir.mkdirs();
                File f = new File(dir, "scan.jpg");
                cameraUri = FileProvider.getUriForFile(this, getPackageName() + ".fileprovider", f);
                takePicture.launch(cameraUri);
            } catch (Exception e) {
                Toast.makeText(this, "Camera not available. Try \"Choose image\".", Toast.LENGTH_LONG).show();
            }
        });
        b.btnGallery.setOnClickListener(v -> pickImage.launch("image/*"));
        b.btnAddAll.setOnClickListener(v -> addSelected());
    }

    private boolean routineMode() {
        return b.toggleMode.getCheckedButtonId() == R.id.btnModeRoutine;
    }

    private void recognise(Uri uri) {
        b.progress.setVisibility(View.VISIBLE);
        b.tvStatus.setText("Reading the image…");
        b.results.removeAllViews();
        b.btnAddAll.setVisibility(View.GONE);
        b.tvRaw.setText("");
        try {
            InputImage image = InputImage.fromFilePath(this, uri);
            TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
                    .process(image)
                    .addOnSuccessListener(r -> onText(r.getText()))
                    .addOnFailureListener(e -> {
                        b.progress.setVisibility(View.GONE);
                        b.tvStatus.setText("Could not read that image. Try a sharper, well-lit photo.");
                    });
        } catch (Exception e) {
            b.progress.setVisibility(View.GONE);
            b.tvStatus.setText("Could not open that image.");
        }
    }

    private void onText(String text) {
        if (isFinishing() || isDestroyed()) return;
        b.progress.setVisibility(View.GONE);
        b.tvRaw.setText(text.isEmpty() ? "" : "Recognised text:\n" + text);
        boxes.clear();
        items.clear();
        b.results.removeAllViews();
        DateFormat df = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT, Locale.getDefault());

        if (routineMode()) {
            for (ScanParser.Slot s : ScanParser.routine(text)) {
                RoutineEntry e = new RoutineEntry();
                e.course = s.course.isEmpty() ? "Class" : s.course;
                e.room = s.room;
                e.teacher = "";
                e.dayOfWeek = s.day;
                e.startMinutes = s.start;
                e.endMinutes = s.end;
                items.add(e);
                addRow(DAY[s.day] + "  " + hm(s.start) + "–" + hm(s.end) + "  •  " + e.course
                        + (s.room.isEmpty() ? "" : "  •  " + s.room));
            }
        } else {
            for (ScanParser.Due d : ScanParser.deadlines(text, System.currentTimeMillis())) {
                Deadline dl = new Deadline();
                dl.title = d.title;
                dl.course = "";
                dl.note = "";
                dl.dueAt = d.millis;
                dl.remindBeforeMinutes = 60;
                items.add(dl);
                addRow(d.title + "\n" + df.format(new Date(d.millis)));
            }
        }
        if (items.isEmpty()) {
            b.tvStatus.setText(text.isEmpty() ? "No text found." : routineMode()
                    ? "Found text, but no day + time lines. Grid timetables are hard to read; try one day per line or use Deadlines mode."
                    : "Found text, but no dates in it.");
            b.btnAddAll.setVisibility(View.GONE);
        } else {
            b.tvStatus.setText("Found " + items.size() + ". Untick anything wrong, then add.");
            b.btnAddAll.setVisibility(View.VISIBLE);
        }
    }

    private void addRow(String label) {
        CheckBox cb = new CheckBox(this);
        cb.setText(label);
        cb.setChecked(true);
        b.results.addView(cb);
        boxes.add(cb);
    }

    private static String hm(int m) {
        return String.format(Locale.US, "%d:%02d", m / 60, m % 60);
    }

    private void addSelected() {
        final List<Object> chosen = new ArrayList<>();
        for (int i = 0; i < boxes.size(); i++) if (boxes.get(i).isChecked()) chosen.add(items.get(i));
        if (chosen.isEmpty()) {
            Toast.makeText(this, "Nothing selected", Toast.LENGTH_SHORT).show();
            return;
        }
        Context app = getApplicationContext();
        b.btnAddAll.setEnabled(false);
        AppDatabase.executor.execute(() -> {
            AppDatabase db = AppDatabase.get(app);
            for (Object o : chosen) {
                if (o instanceof RoutineEntry) {
                    db.routineDao().insert((RoutineEntry) o);
                } else {
                    Deadline d = (Deadline) o;
                    d.id = (int) db.deadlineDao().insert(d);
                    ReminderScheduler.schedule(app, d);
                }
            }
            runOnUiThread(() -> {
                Toast.makeText(this, "Added " + chosen.size(), Toast.LENGTH_SHORT).show();
                finish();
            });
        });
    }
}
