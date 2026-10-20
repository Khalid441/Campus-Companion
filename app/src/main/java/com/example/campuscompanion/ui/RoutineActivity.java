package com.example.campuscompanion.ui;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.example.campuscompanion.adapter.RoutineAdapter;
import com.example.campuscompanion.data.local.AppDatabase;
import com.example.campuscompanion.data.local.RoutineDao;
import com.example.campuscompanion.data.local.RoutineEntry;
import com.example.campuscompanion.databinding.ActivityRoutineBinding;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

public class RoutineActivity extends AppCompatActivity implements RoutineAdapter.Listener {

    private ActivityRoutineBinding b;
    private RoutineDao dao;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        b = ActivityRoutineBinding.inflate(getLayoutInflater());
        setContentView(b.getRoot());

        dao = AppDatabase.get(this).routineDao();

        RoutineAdapter adapter = new RoutineAdapter(this);
        b.recycler.setLayoutManager(new LinearLayoutManager(this));
        b.recycler.setAdapter(adapter);

        dao.getAll().observe(this, list -> {
            adapter.submitList(list);
            b.emptyText.setVisibility(list.isEmpty() ? View.VISIBLE : View.GONE);
        });

        b.fabAdd.setOnClickListener(v ->
                startActivity(new Intent(this, AddRoutineActivity.class)));
    }

    @Override
    public void onLongPress(RoutineEntry e) {
        new MaterialAlertDialogBuilder(this)
                .setTitle("Delete class?")
                .setMessage(e.course)
                .setPositiveButton("Delete", (d, w) -> AppDatabase.executor.execute(() -> dao.delete(e)))
                .setNegativeButton("Cancel", null)
                .show();
    }
}
