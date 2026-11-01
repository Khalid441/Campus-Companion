package com.example.campuscompanion.ui;

import android.content.Context;

import com.example.campuscompanion.util.StudyStats;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

/** Lets the student pick their own weekly revision goal (or switch it off). */
public final class GoalDialog {
    private GoalDialog() { }

    public static void show(Context c, Runnable onChanged) {
        int[] choices = StudyStats.GOAL_CHOICES;
        String[] labels = new String[choices.length];
        int current = -1;
        for (int i = 0; i < choices.length; i++) {
            labels[i] = choices[i] == 0 ? "No goal" : choices[i] + (choices[i] == 1 ? " revision per week" : " revisions per week");
            if (choices[i] == StudyStats.goal(c)) current = i;
        }
        new MaterialAlertDialogBuilder(c)
                .setTitle("Weekly revision goal")
                .setSingleChoiceItems(labels, current, (d, which) -> {
                    StudyStats.setGoal(c, choices[which]);
                    d.dismiss();
                    if (onChanged != null) onChanged.run();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }
}
