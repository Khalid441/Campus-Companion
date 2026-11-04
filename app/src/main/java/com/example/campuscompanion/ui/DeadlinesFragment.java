package com.example.campuscompanion.ui;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.campuscompanion.R;
import com.example.campuscompanion.adapter.DeadlineAdapter;
import com.example.campuscompanion.auth.UserSession;
import com.example.campuscompanion.data.local.AppDatabase;
import com.example.campuscompanion.data.local.Deadline;
import com.example.campuscompanion.data.local.DeadlineDao;
import com.example.campuscompanion.databinding.ActivityDeadlinesBinding;
import com.example.campuscompanion.reminder.ReminderScheduler;
import com.example.campuscompanion.widget.NextDeadlineWidget;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.snackbar.Snackbar;
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

public class DeadlinesFragment extends Fragment implements DeadlineAdapter.Listener {

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
                Context app = requireContext().getApplicationContext();
                AppDatabase.executor.execute(() -> {
                    boolean ok = writeCsv(app, uri, snapshot);
                    new Handler(Looper.getMainLooper()).post(() -> Toast.makeText(app,
                            ok ? "Exported " + snapshot.size() + " deadlines" : "Export failed",
                            Toast.LENGTH_LONG).show());
                });
            });

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        b = ActivityDeadlinesBinding.inflate(inflater, container, false);
        return b.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        dao = AppDatabase.get(requireContext()).deadlineDao();

        adapter = new DeadlineAdapter(this);
        b.recycler.setLayoutManager(new LinearLayoutManager(requireContext()));
        b.recycler.setAdapter(adapter);
        b.recycler.setLayoutAnimation(android.view.animation.AnimationUtils.loadLayoutAnimation(requireContext(), R.anim.layout_rise));

        dao.getAll().observe(getViewLifecycleOwner(), list -> {
            all = list;
            applyFilter();
        });

        b.etSearch.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int st, int c, int a) { }
            @Override public void onTextChanged(CharSequence s, int st, int b2, int c) { applyFilter(); }
            @Override public void afterTextChanged(Editable s) { }
        });

        b.fabAdd.setOnClickListener(v ->
                startActivity(new Intent(requireContext(), AddDeadlineActivity.class)));

        setupSwipe();

        b.btnExport.setOnClickListener(v -> new com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
                .setTitle("Export")
                .setItems(new String[]{"Add to calendar (.ics)", "Spreadsheet (.csv)"}, (dlg, which) -> {
                    if (which == 0) {
                        CalendarExport.show(requireActivity());
                    } else if (all.isEmpty()) {
                        Toast.makeText(requireContext(), "No deadlines to export", Toast.LENGTH_SHORT).show();
                    } else {
                        createCsv.launch("campus_deadlines.csv");
                    }
                })
                .show());
    }

    /** Swipe right = done / not done, swipe left = delete (both with Undo). */
    private void setupSwipe() {
        new ItemTouchHelper(new ItemTouchHelper.SimpleCallback(0,
                ItemTouchHelper.LEFT | ItemTouchHelper.RIGHT) {
            @Override
            public boolean onMove(@NonNull RecyclerView rv, @NonNull RecyclerView.ViewHolder vh,
                                  @NonNull RecyclerView.ViewHolder target) {
                return false;
            }

            @Override
            public void onSwiped(@NonNull RecyclerView.ViewHolder vh, int direction) {
                int pos = vh.getBindingAdapterPosition();
                if (pos == RecyclerView.NO_POSITION || pos >= adapter.getCurrentList().size()) return;
                Deadline d = adapter.getCurrentList().get(pos);
                Context app = requireContext().getApplicationContext();

                if (direction == ItemTouchHelper.RIGHT) {
                    boolean newDone = !d.done;
                    onToggleDone(d, newDone);
                    adapter.notifyItemChanged(pos);   // snap the card back; the list refreshes from Room
                    Snackbar.make(b.getRoot(), newDone ? "Marked as done" : "Marked as pending",
                                    Snackbar.LENGTH_LONG)
                            .setAnchorView(b.fabAdd)
                            .setAction("Undo", v -> onToggleDone(d, !newDone))
                            .show();
                } else {
                    if (d.isShared) {
                        adapter.notifyItemChanged(pos);
                        Toast.makeText(requireContext(),
                                "Shared deadlines can only be removed by your CR (long-press)",
                                Toast.LENGTH_LONG).show();
                        return;
                    }
                    AppDatabase.executor.execute(() -> {
                        dao.delete(d);
                        ReminderScheduler.cancel(app, d.id);
                    });
                    Snackbar.make(b.getRoot(), "Deleted \"" + d.title + "\"", Snackbar.LENGTH_LONG)
                            .setAnchorView(b.fabAdd)
                            .setAction("Undo", v -> AppDatabase.executor.execute(() -> {
                                dao.insert(d);                       // same id comes back
                                ReminderScheduler.schedule(app, d);
                            }))
                            .show();
                }
            }
        }).attachToRecyclerView(b.recycler);
    }

    @Override
    public void onResume() {
        super.onResume();
        ticker.postDelayed(tick, 60_000);
    }

    @Override
    public void onPause() {
        super.onPause();
        ticker.removeCallbacks(tick);
        NextDeadlineWidget.refresh(requireContext());   // keep the home screen widget up to date
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
            b.emptyText.setText(all.isEmpty() ? "\u2705\nNo deadlines yet\nTap + to add one." : "\uD83D\uDD0D\nNo matches");
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
        Context app = requireContext().getApplicationContext();
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
        Context app = requireContext().getApplicationContext();
        new MaterialAlertDialogBuilder(requireContext())
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
        String section = UserSession.section(requireContext());
        if (!UserSession.isCr(requireContext()) || section == null || d.remoteId == null) {
            Toast.makeText(requireContext(), "Only your class representative can remove shared deadlines",
                    Toast.LENGTH_LONG).show();
            return;
        }
        Context app = requireContext().getApplicationContext();
        new MaterialAlertDialogBuilder(requireContext())
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

    /** Runs on the main thread, and quietly does nothing if the screen is already gone. */
    private void runUi(Runnable r) {
        FragmentActivity a = getActivity();
        if (a != null) a.runOnUiThread(r);
    }
}
