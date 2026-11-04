package com.example.campuscompanion.ui;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.campuscompanion.data.local.AppDatabase;
import com.example.campuscompanion.data.local.AttendanceCourse;
import com.example.campuscompanion.databinding.ActivityGpaBinding;
import com.example.campuscompanion.databinding.DialogGpaCourseBinding;
import com.example.campuscompanion.util.GpaCalc;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.firebase.auth.FirebaseAuth;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Semester planner on the KUET 4.0 scale: SGPA, new CGPA, and the grade needed for a target. */
public class GpaActivity extends AppCompatActivity {

    private ActivityGpaBinding b;
    private final List<GpaCalc.Course> courses = new ArrayList<>();
    private boolean loading = true;

    private SharedPreferences prefs() {
        String uid = FirebaseAuth.getInstance().getUid();
        return getSharedPreferences("gpa_" + (uid == null ? "guest" : uid), Context.MODE_PRIVATE);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        b = ActivityGpaBinding.inflate(getLayoutInflater());
        setContentView(b.getRoot());

        load();
        loading = false;

        TextWatcher watcher = new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int c, int d) { }
            @Override public void onTextChanged(CharSequence s, int a, int c, int d) { }
            @Override public void afterTextChanged(Editable s) { if (!loading) { save(); recalc(); } }
        };
        b.etPrevCgpa.addTextChangedListener(watcher);
        b.etPrevCredits.addTextChangedListener(watcher);
        b.etTarget.addTextChangedListener(watcher);

        b.btnGpaAdd.setOnClickListener(v -> showCourseDialog(-1));
        b.btnGpaImport.setOnClickListener(v -> importCourses());
        render();
    }

    // ---------------------------------------------------------------------
    // Saving
    // ---------------------------------------------------------------------

    private void load() {
        SharedPreferences p = prefs();
        b.etPrevCgpa.setText(p.getString("prevCgpa", ""));
        b.etPrevCredits.setText(p.getString("prevCredits", ""));
        b.etTarget.setText(p.getString("target", ""));
        try {
            JSONArray arr = new JSONArray(p.getString("courses", "[]"));
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.getJSONObject(i);
                courses.add(new GpaCalc.Course(o.optString("name"), o.optDouble("credits", 3), o.optInt("grade", -1)));
            }
        } catch (Exception ignored) { }
    }

    private void save() {
        JSONArray arr = new JSONArray();
        try {
            for (GpaCalc.Course c : courses) {
                arr.put(new JSONObject().put("name", c.name).put("credits", c.credits).put("grade", c.grade));
            }
        } catch (Exception ignored) { }
        prefs().edit()
                .putString("prevCgpa", text(b.etPrevCgpa))
                .putString("prevCredits", text(b.etPrevCredits))
                .putString("target", text(b.etTarget))
                .putString("courses", arr.toString())
                .apply();
    }

    private static String text(com.google.android.material.textfield.TextInputEditText e) {
        return e.getText() == null ? "" : e.getText().toString().trim();
    }

    private static double number(String s, double fallback) {
        try {
            return Double.parseDouble(s.trim().replace(',', '.'));
        } catch (Exception e) {
            return fallback;
        }
    }

    // ---------------------------------------------------------------------
    // Courses
    // ---------------------------------------------------------------------

    private void render() {
        b.gpaList.removeAllViews();
        b.tvGpaEmpty.setVisibility(courses.isEmpty() ? View.VISIBLE : View.GONE);
        for (int i = 0; i < courses.size(); i++) {
            GpaCalc.Course c = courses.get(i);
            TextView row = new TextView(this);
            row.setText(c.name + "\n" + fmt(c.credits) + " credits  •  " + GpaCalc.label(c.grade));
            row.setTextSize(15);
            int pad = (int) (12 * getResources().getDisplayMetrics().density);
            row.setPadding(pad, pad, pad, pad);
            android.util.TypedValue tv = new android.util.TypedValue();
            getTheme().resolveAttribute(android.R.attr.selectableItemBackground, tv, true);
            row.setBackgroundResource(tv.resourceId);
            final int index = i;
            row.setOnClickListener(v -> showCourseDialog(index));
            row.setOnLongClickListener(v -> {
                new MaterialAlertDialogBuilder(this)
                        .setTitle("Remove " + c.name + "?")
                        .setPositiveButton("Remove", (d, w) -> {
                            courses.remove(index);
                            save();
                            render();
                        })
                        .setNegativeButton("Cancel", null)
                        .show();
                return true;
            });
            b.gpaList.addView(row);
        }
        recalc();
    }

    private void showCourseDialog(int index) {
        DialogGpaCourseBinding d = DialogGpaCourseBinding.inflate(getLayoutInflater());
        List<String> grades = new ArrayList<>();
        grades.add("Not graded yet");
        for (int i = 0; i < GpaCalc.LETTERS.length; i++) {
            grades.add(GpaCalc.LETTERS[i] + "  (" + String.format(Locale.US, "%.2f", GpaCalc.POINTS[i])
                    + ", marks " + GpaCalc.MARKS[i] + ")");
        }
        d.spGGrade.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, grades));

        if (index >= 0) {
            GpaCalc.Course c = courses.get(index);
            d.etGName.setText(c.name);
            d.etGCredits.setText(fmt(c.credits));
            d.spGGrade.setSelection(c.grade + 1);
        } else {
            d.etGCredits.setText("3");
        }

        androidx.appcompat.app.AlertDialog dialog = new MaterialAlertDialogBuilder(this)
                .setTitle(index >= 0 ? "Edit course" : "Add course")
                .setView(d.getRoot())
                .setPositiveButton("Save", null)
                .setNegativeButton("Cancel", null)
                .create();
        dialog.show();
        dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String name = d.etGName.getText() == null ? "" : d.etGName.getText().toString().trim();
            double credits = number(d.etGCredits.getText() == null ? "" : d.etGCredits.getText().toString(), -1);
            d.tilGName.setError(null);
            d.tilGCredits.setError(null);
            if (name.isEmpty()) { d.tilGName.setError("Enter the course name"); return; }
            if (credits <= 0 || credits > 12) { d.tilGCredits.setError("Credits between 0.5 and 12"); return; }
            int grade = d.spGGrade.getSelectedItemPosition() - 1;
            if (index >= 0) {
                GpaCalc.Course c = courses.get(index);
                c.name = name;
                c.credits = credits;
                c.grade = grade;
            } else {
                courses.add(new GpaCalc.Course(name, credits, grade));
            }
            save();
            render();
            dialog.dismiss();
        });
    }

    /** Adds the attendance courses that are not already in the list (3 credits, not graded). */
    private void importCourses() {
        AppDatabase.executor.execute(() -> {
            List<AttendanceCourse> mine = AppDatabase.get(getApplicationContext()).attendanceDao().getCoursesNow();
            runOnUiThread(() -> {
                if (isFinishing() || isDestroyed()) return;
                int added = 0;
                for (AttendanceCourse a : mine) {
                    boolean have = false;
                    for (GpaCalc.Course c : courses) if (c.name.equalsIgnoreCase(a.title())) have = true;
                    if (!have) {
                        courses.add(new GpaCalc.Course(a.title(), 3, -1));
                        added++;
                    }
                }
                Toast.makeText(this, added == 0 ? "Nothing new to add" : added + " added (edit the credits)",
                        Toast.LENGTH_SHORT).show();
                save();
                render();
            });
        });
    }

    // ---------------------------------------------------------------------
    // Results
    // ---------------------------------------------------------------------

    private void recalc() {
        double prevCgpa = number(text(b.etPrevCgpa), 0);
        double prevCredits = number(text(b.etPrevCredits), 0);
        if (prevCgpa < 0 || prevCgpa > 4) prevCgpa = Math.max(0, Math.min(4, prevCgpa));

        double sgpa = GpaCalc.sgpa(courses);
        double cgpa = GpaCalc.cgpa(prevCgpa, prevCredits, courses);
        b.tvSgpa.setText(sgpa < 0 ? "SGPA: add a graded course" : "SGPA: " + String.format(Locale.US, "%.2f", sgpa));
        b.tvCgpa.setText(cgpa < 0 ? "CGPA: add your earlier CGPA and credits"
                : "CGPA: " + String.format(Locale.US, "%.2f", cgpa) + "  (" + fmt(prevCredits + GpaCalc.gradedCredits(courses)) + " credits)");

        String t = text(b.etTarget);
        if (t.isEmpty()) {
            b.tvGoalResult.setText("Type the CGPA you are aiming for and I will tell you the grade you need.");
            return;
        }
        double target = number(t, -1);
        if (target <= 0 || target > 4) {
            b.tvGoalResult.setText("Enter a target between 0.01 and 4.00.");
            return;
        }
        if (GpaCalc.ungradedCredits(courses) <= 0) {
            b.tvGoalResult.setText(cgpa < 0 ? "Add the courses still to come (leave their grade empty)."
                    : (cgpa + 1e-9 >= target ? "You are already at or above " + String.format(Locale.US, "%.2f", target) + "."
                    : "All courses are graded. Your CGPA is " + String.format(Locale.US, "%.2f", cgpa) + "."));
            return;
        }
        double need = GpaCalc.requiredAverage(prevCgpa, prevCredits, courses, target);
        if (need <= 0) {
            b.tvGoalResult.setText("You are already safe: even a pass in the remaining courses keeps you at or above "
                    + String.format(Locale.US, "%.2f", target) + ".");
        } else if (need > GpaCalc.POINTS[0] + 1e-9) {
            b.tvGoalResult.setText("Not reachable this semester: you would need an average above 4.00 ("
                    + String.format(Locale.US, "%.2f", need) + "). Try a lower target.");
        } else {
            int letter = GpaCalc.lowestLetterFor(need);
            b.tvGoalResult.setText("To reach " + String.format(Locale.US, "%.2f", target) + " you need an average of "
                    + String.format(Locale.US, "%.2f", need) + " in the remaining " + fmt(GpaCalc.ungradedCredits(courses))
                    + " credits: about " + GpaCalc.LETTERS[letter] + " in each (marks " + GpaCalc.MARKS[letter] + ").");
        }
    }

    private static String fmt(double v) {
        return v == Math.floor(v) ? String.valueOf((int) v) : String.format(Locale.US, "%.1f", v);
    }
}
