package com.example.campuscompanion.ui;

import android.content.Context;
import android.text.InputType;
import android.widget.FrameLayout;

import com.example.campuscompanion.util.StudyStats;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

/** The student types their own weekly revision goal (0 turns it off). */
public final class GoalDialog {
    private GoalDialog() { }

    public static void show(Context c, Runnable onChanged) {
        int pad = (int) (24 * c.getResources().getDisplayMetrics().density);

        TextInputLayout til = new TextInputLayout(c);
        til.setHint("Revisions per week (0 = no goal)");
        TextInputEditText et = new TextInputEditText(til.getContext());
        et.setInputType(InputType.TYPE_CLASS_NUMBER);
        et.setMaxLines(1);
        et.setText(String.valueOf(StudyStats.goal(c)));
        et.setSelectAllOnFocus(true);
        til.addView(et);

        FrameLayout holder = new FrameLayout(c);
        holder.setPadding(pad, pad / 2, pad, 0);
        holder.addView(til);

        androidx.appcompat.app.AlertDialog dialog = new MaterialAlertDialogBuilder(c)
                .setTitle("Weekly revision goal")
                .setMessage("Choose any number that suits you.")
                .setView(holder)
                .setPositiveButton("Save", null)
                .setNegativeButton("Cancel", null)
                .create();
        dialog.show();

        dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String text = et.getText() == null ? "" : et.getText().toString().trim();
            int goal;
            try {
                goal = Integer.parseInt(text);
            } catch (NumberFormatException e) {
                til.setError("Enter a number");
                return;
            }
            if (goal < 0 || goal > 200) {
                til.setError("Use 0 to 200");
                return;
            }
            StudyStats.setGoal(c, goal);
            dialog.dismiss();
            if (onChanged != null) onChanged.run();
        });
    }
}
