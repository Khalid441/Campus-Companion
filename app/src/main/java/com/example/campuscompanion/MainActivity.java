package com.example.campuscompanion;

import android.content.Intent;
import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;

import com.example.campuscompanion.databinding.ActivityMainBinding;
import com.example.campuscompanion.ui.DeadlinesActivity;

public class MainActivity extends AppCompatActivity {

    private ActivityMainBinding b;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        b = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(b.getRoot());

        b.btnDeadlines.setOnClickListener(v ->
                startActivity(new Intent(this, DeadlinesActivity.class)));
    }
}
