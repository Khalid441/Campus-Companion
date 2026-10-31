package com.example.campuscompanion.util;

import android.app.Activity;
import android.content.Intent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;

import com.example.campuscompanion.MainActivity;
import com.example.campuscompanion.R;
import com.example.campuscompanion.ui.AttendanceActivity;
import com.example.campuscompanion.ui.DeadlinesActivity;
import com.example.campuscompanion.ui.NoticesActivity;
import com.example.campuscompanion.ui.StudyActivity;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.navigation.NavigationBarView;

/**
 * Adds the bottom navigation bar (Home, Notices, Deadlines, Attendance, Study) under whatever
 * layout an activity already uses. Call it right after setContentView(). No layout files change.
 */
public final class BottomNav {
    private BottomNav() { }

    public static void attach(Activity activity, int selectedId) {
        ViewGroup content = activity.findViewById(android.R.id.content);
        if (content == null || content.getChildCount() == 0) return;

        View page = content.getChildAt(0);
        content.removeView(page);

        LinearLayout column = new LinearLayout(activity);
        column.setOrientation(LinearLayout.VERTICAL);
        column.addView(page, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        BottomNavigationView nav = new BottomNavigationView(activity);
        nav.inflateMenu(R.menu.bottom_nav);
        nav.setLabelVisibilityMode(NavigationBarView.LABEL_VISIBILITY_LABELED);
        nav.setSelectedItemId(selectedId);
        column.addView(nav, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        content.addView(column, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        nav.setOnItemSelectedListener(item -> {
            int id = item.getItemId();
            if (id == selectedId) return true;

            Class<?> target;
            if (id == R.id.nav_home) target = MainActivity.class;
            else if (id == R.id.nav_notices) target = NoticesActivity.class;
            else if (id == R.id.nav_deadlines) target = DeadlinesActivity.class;
            else if (id == R.id.nav_attendance) target = AttendanceActivity.class;
            else target = StudyActivity.class;

            Intent i = new Intent(activity, target);
            i.addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION);
            if (id == R.id.nav_home) {
                i.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            }
            activity.startActivity(i);
            // Tabs replace each other, so Back always returns to Home
            if (selectedId != R.id.nav_home) activity.finish();
            activity.overridePendingTransition(0, 0);
            return false;   // the new screen shows its own selected tab
        });
    }
}
