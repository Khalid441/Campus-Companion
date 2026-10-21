package com.example.campuscompanion.ui;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.example.campuscompanion.adapter.DeadlineAdapter;
import com.example.campuscompanion.auth.UserSession;
import com.example.campuscompanion.data.local.AppDatabase;
import com.example.campuscompanion.data.local.Deadline;
import com.example.campuscompanion.data.local.DeadlineDao;
import com.example.campuscompanion.databinding.ActivityDeadlinesBinding;
import com.example.campuscompanion.reminder.ReminderScheduler;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.firebase.firestore.FirebaseFirestore;

import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class DeadlinesActivity extends AppCompatActivity implements DeadlineAdapter.Listener {

    private ActivityDeadlinesBinding b;
    private DeadlineDao dao;
    private DeadlineAdapter adapter;
    private List<Deadline> all = new ArrayList<>();

    // Keeps the countdown rings fresh while the screen is open
    private final Handler ticker = new Handler(Looper.getMainLooper());
    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            if (adapter != null) adapter.notifyItemRangeChanged(0, adapter.getItemCount());
            ticker.postDelayed(this, 60_000);
        }
    };

    // Lets the user choose where to save the CSV (no storage permission needed)
    @SuppressWarnings("deprecation")
    private final ActivityResultLauncher<String> createCsv = registerForActivityResult(
            new ActivityResultContracts.CreateDocument(), uri -> {
                if (uri == null) return;
                List<Deadline> snapshot = new ArrayList<>(all);
                Context app = getApplicationContext();
                AppDatabase.executor.execute(() -> {
                    boolean ok = writeCsv(app, uri, snapshot);
                    new Handler(Looper.getMainLooper()).post(() -> Toast.makeText(app,
                            ok ? "Exported " + snapshot.size() + " deadlines" : "Export failed",
                            Toast.LENGTH_LONG).show());
                });
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        b = ActivityDeadlinesBinding.inflate(getLayoutInflater());
        setContentView(b.getRoot());

        dao = AppDatabase.get(this).deadlineDao();

        adapter = new DeadlineAdapter(this);
        b.recycler.setLayoutManager(new LinearLayoutManager(this));
        b.recycler.setAdapter(adapter);

        dao.getAll().observe(this, list -> {
            all = list;
            applyFilter();
        });

        b.etSearch.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int st, int c, int a) { }
            @Override public void onTextChanged(CharSequence s, int st, int b2, int c) { applyFilter(); }
            @Override public void afterTextChanged(Editable s) { }
        });

        b.fabAdd.setOnClickListener(v ->
                startActivity(new Intent(this, AddDeadlineActivity.class)));

        b.btnExport.setOnClickListener(v -> {
            if (all.isEmpty()) {
                Toast.makeText(this, "No deadlines to export", Toast.LENGTH_SHORT).show();
            } else {
                createCsv.launch("campus_deadlines.csv");
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        ticker.postDelayed(tick, 60_000);
    }

    @Override
    protected void onPause() {
        super.onPause();
        ticker.removeCallbacks(tick);
    }

    private static boolean writeCsv(Context app, Uri uri, List<Deadline> list) {
        SimpleDateFormat f = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault());
        long now = System.currentTimeMillis();
        try (OutputStream os = app.getContentResolver().openOutputStream(uri);
             Writer w = new OutputStreamWriter(os, StandardCharsets.UTF_8)) {
            w.write('\uFEFF'); // BOM so Excel shows non-English text correctly
            w.write("Title,Course,Priority,Due,Status,Source,Note\r\n");
            for (Deadline d : list) {
                String status = d.done ? "Done" : (d.dueAt < now ? "Overdue" : "Pending");
                w.write(csv(d.title) + "," + csv(d.course) + "," + csv(d.priority) + ","
                        + csv(f.format(new Date(d.dueAt))) + "," + status + ","
                        + (d.isShared ? "Shared by CR" : "Personal") + "," + csv(d.note) + "\r\n");
            }
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private static String csv(String s) {
        if (s == null) return "";
        if (s.contains(",") || s.contains("\"") || s.contains("\n") || s.contains("\r")) {
            return "\"" + s.replace("\"", "\"\"") + "\"";
        }
        return s;
    }

    private void applyFilter() {
        String q = b.etSearch.getText() == null ? ""
                : b.etSearch.getText().toString().trim().toLowerCase(Locale.getDefault());
        List<Deadline> out = new ArrayList<>();
        for (Deadline d : all) {
            if (q.isEmpty() || has(d.title, q) || has(d.course, q) || has(d.note, q)) out.add(d);
        }
        adapter.submitList(out);
        if (out.isEmpty()) {
            b.emptyText.setText(all.isEmpty() ? "No deadlines yet.\nTap + to add one." : "No matches");
            b.emptyText.setVisibility(View.VISIBLE);
        } else {
            b.emptyText.setVisibility(View.GONE);
        }
    }

    private static boolean has(String field, String q) {
        return field != null && field.toLowerCase(Locale.getDefault()).contains(q);
    }

    @Override
    public void onToggleDone(Deadline d, boolean done) {
        Context app = getApplicationContext();
        AppDatabase.executor.execute(() -> {
            dao.setDone(d.id, done);
            if (done) {
                ReminderScheduler.cancel(app, d.id);
            } else {
                Deadline fresh = dao.getById(d.id);
                if (fresh != null) ReminderScheduler.schedule(app, fresh);
            }
        });
    }

    @Override
    public void onLongPress(Deadline d) {
        if (d.isShared) {
            deleteShared(d);
            return;
        }
        Context app = getApplicationContext();
        new MaterialAlertDialogBuilder(this)
                .setTitle("Delete deadline?")
                .setMessage(d.title)
                .setPositiveButton("Delete", (dlg, which) ->
                        AppDatabase.executor.execute(() -> {
                            dao.delete(d);
                            ReminderScheduler.cancel(app, d.id);
                        }))
                .setNegativeButton("Cancel", null)
                .show();
    }

    /** Shared deadlines belong to the section: only the CR can remove them (for everyone). */
    private void deleteShared(Deadline d) {
        String section = UserSession.section(this);
        if (!UserSession.isCr(this) || section == null || d.remoteId == null) {
            Toast.makeText(this, "Only your class representative can remove shared deadlines",
                    Toast.LENGTH_LONG).show();
            return;
        }
        Context app = getApplicationContext();
        new MaterialAlertDialogBuilder(this)
                .setTitle("Remove for the whole section?")
                .setMessage(d.title)
                .setPositiveButton("Remove", (dlg, which) ->
                        FirebaseFirestore.getInstance()
                                .collection("sections").document(section)
                                .collection("deadlines").document(d.remoteId).delete()
                                .addOnFailureListener(e -> Toast.makeText(app,
                                        "Could not remove: " + e.getMessage(), Toast.LENGTH_LONG).show()))
                .setNegativeButton("Cancel", null)
                .show();
    }
}
