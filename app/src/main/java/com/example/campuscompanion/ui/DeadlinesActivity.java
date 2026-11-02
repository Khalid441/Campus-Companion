package com.example.campuscompanion.ui;

import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;

import com.example.campuscompanion.MainActivity;
import com.example.campuscompanion.R;

/** Old entry point (notifications, widgets, links): opens the Deadlines tab of the main screen. */
public class DeadlinesActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        startActivity(MainActivity.tabIntent(this, R.id.nav_deadlines));
        finish();
        overridePendingTransition(0, 0);
    }
}
