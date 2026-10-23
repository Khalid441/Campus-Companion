package com.example.campuscompanion.ui;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.campuscompanion.adapter.StudyAdapter;
import com.example.campuscompanion.data.local.AppDatabase;
import com.example.campuscompanion.data.local.StudyDao;
import com.example.campuscompanion.data.local.StudySession;
import com.example.campuscompanion.databinding.ActivityStudyBinding;
import com.example.campuscompanion.reminder.StudyReminderScheduler;
import com.google.android.material.snackbar.Snackbar;

import java.util.List;

/** My study plan: the user's own study sessions (NOT university classes). */
public class StudyActivity extends AppCompatActivity implements StudyAdapter.Listener {

    private ActivityStudyBinding b;
    private StudyDao dao;
    private StudyAdapter adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        b = ActivityStudyBinding.inflate(getLayoutInflater());
        setContentView(b.getRoot());

        dao = AppDatabase.get(this).studyDao();

        adapter = new StudyAdapter(this);
        b.recycler.setLayoutManager(new LinearLayoutManager(this));
        b.recycler.setAdapter(adapter);

        dao.getAll().observe(this, list -> {
            adapter.submitList(list);
            b.emptyText.setVisibility(list.isEmpty() ? View.VISIBLE : View.GONE);
            b.tvTotal.setVisibility(list.isEmpty() ? View.GONE : View.VISIBLE);
            b.tvTotal.setText(plannedThisWeek(list));
        });

        b.fabAdd.setOnClickListener(v -> startActivity(new Intent(this, AddStudyActivity.class)));
        b.tvClassLink.setOnClickListener(v -> startActivity(new Intent(this, RoutineActivity.class)));

        setupSwipe();
    }

    private static String plannedThisWeek(List<StudySession> list) {
        int total = 0;
        for (StudySession s : list) {
            if (s.enabled) total += (s.endMinutes - s.startMinutes) * Integer.bitCount(s.daysMask);
        }
        return StudyAdapter.durationLabel(total) + " of study planned this week";
    }

    @Override
    public void onClick(StudySession s) {
        startActivity(new Intent(this, AddStudyActivity.class)
                .putExtra(AddStudyActivity.EXTRA_ID, s.id));
    }

    @Override
    public void onToggle(StudySession s, boolean enabled) {
        StudySession updated = s.copy();
        updated.enabled = enabled;
        Context app = getApplicationContext();
        AppDatabase.executor.execute(() -> {
            dao.update(updated);
            if (enabled) StudyReminderScheduler.schedule(app, updated);
            else StudyReminderScheduler.cancel(app, updated.id);
        });
    }

    /** Swipe a session away to delete it, with Undo. */
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
                StudySession s = adapter.getCurrentList().get(pos).copy();
                Context app = getApplicationContext();

                AppDatabase.executor.execute(() -> {
                    dao.delete(s);
                    StudyReminderScheduler.cancel(app, s.id);
                });
                Snackbar.make(b.getRoot(), "Deleted \"" + s.subject + "\"", Snackbar.LENGTH_LONG)
                        .setAnchorView(b.fabAdd)
                        .setAction("Undo", v -> AppDatabase.executor.execute(() -> {
                            dao.insert(s);                      // same id comes back
                            StudyReminderScheduler.schedule(app, s);
                        }))
                        .show();
            }
        }).attachToRecyclerView(b.recycler);
    }
}
