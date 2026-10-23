package com.example.campuscompanion.ui;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;

import androidx.appcompat.app.AppCompatActivity;

import com.example.campuscompanion.data.local.AppDatabase;
import com.example.campuscompanion.databinding.ActivityTimetableBinding;

/** Class timetable: university classes only, built from the routine entries in Room. */
public class TimetableActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ActivityTimetableBinding b = ActivityTimetableBinding.inflate(getLayoutInflater());
        setContentView(b.getRoot());

        b.tvStudyLink.setOnClickListener(v -> startActivity(new Intent(this, StudyActivity.class)));

        AppDatabase.get(this).routineDao().getAll().observe(this, list -> {
            b.timetable.setEntries(list);
            b.emptyText.setVisibility(list.isEmpty() ? View.VISIBLE : View.GONE);
        });
    }
}
