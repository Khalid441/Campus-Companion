package com.example.campuscompanion.ui;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.example.campuscompanion.adapter.DeadlineAdapter;
import com.example.campuscompanion.data.local.AppDatabase;
import com.example.campuscompanion.data.local.Deadline;
import com.example.campuscompanion.data.local.DeadlineDao;
import com.example.campuscompanion.databinding.ActivityDeadlinesBinding;
import com.example.campuscompanion.reminder.ReminderScheduler;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

public class DeadlinesActivity extends AppCompatActivity implements DeadlineAdapter.Listener {

    private ActivityDeadlinesBinding b;
    private DeadlineDao dao;
    private DeadlineAdapter adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        b = ActivityDeadlinesBinding.inflate(getLayoutInflater());
        setContentView(b.getRoot());

        dao = AppDatabase.get(this).deadlineDao();

        adapter = new DeadlineAdapter(this);
        b.recycler.setLayoutManager(new LinearLayoutManager(this));
        b.recycler.setAdapter(adapter);

        // LiveData: the list refreshes automatically whenever the table changes.
        dao.getAll().observe(this, list -> {
            adapter.submitList(list);
            b.emptyText.setVisibility(list.isEmpty() ? View.VISIBLE : View.GONE);
        });

        b.fabAdd.setOnClickListener(v ->
                startActivity(new Intent(this, AddDeadlineActivity.class)));
    }

    @Override
    public void onToggleDone(Deadline d, boolean done) {
        Context app = getApplicationContext();
        AppDatabase.executor.execute(() -> {
            dao.setDone(d.id, done);
            if (done) {
                ReminderScheduler.cancel(app, d.id);          // finished: no reminder needed
            } else {
                Deadline fresh = dao.getById(d.id);           // un-ticked: bring the reminder back
                if (fresh != null) ReminderScheduler.schedule(app, fresh);
            }
        });
    }

    @Override
    public void onLongPress(Deadline d) {
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
}
