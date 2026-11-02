package com.example.campuscompanion.ui;

import android.content.Context;
import android.os.Bundle;
import android.view.View;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AppCompatActivity;

import com.example.campuscompanion.CampusApp;
import com.example.campuscompanion.databinding.ActivityOnboardingBinding;
import com.example.campuscompanion.util.SampleData;
import com.google.firebase.auth.FirebaseAuth;

/** Three short intro pages, shown once per account right after the first sign-in. */
public class OnboardingActivity extends AppCompatActivity {

    private static final String[][] PAGES = {
            {"\uD83C\uDF93", "Your whole term in one place",
                    "Deadlines, class routine, study plan and notices from your CR, all on your phone and all working offline."},
            {"\uD83D\uDCCA", "Attendance that does the maths",
                    "Mark each class, set your own minimum, and see how many classes you can still miss. "
                            + "Note what was taught and it schedules revision for you."},
            {"\uD83D\uDD0D", "Find anything, fast",
                    "Search across everything, plan your exams day by day, and focus with a study timer."},
    };

    private ActivityOnboardingBinding b;
    private int index = 0;

    private static String key() {
        String uid = FirebaseAuth.getInstance().getUid();
        return "onboarded_" + (uid == null ? "guest" : uid);
    }

    public static boolean isDone(Context c) {
        return c.getSharedPreferences(CampusApp.PREFS, Context.MODE_PRIVATE).getBoolean(key(), false);
    }

    private void markDone() {
        getSharedPreferences(CampusApp.PREFS, Context.MODE_PRIVATE).edit().putBoolean(key(), true).apply();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        b = ActivityOnboardingBinding.inflate(getLayoutInflater());
        setContentView(b.getRoot());
        if (savedInstanceState != null) index = savedInstanceState.getInt("index", 0);

        b.btnSkip.setOnClickListener(v -> finishTour());
        b.btnNext.setOnClickListener(v -> {
            if (index < PAGES.length - 1) {
                index++;
                show();
            } else {
                finishTour();
            }
        });
        b.btnSample.setOnClickListener(v -> {
            b.btnSample.setEnabled(false);
            SampleData.load(this, () -> runOnUiThread(() -> {
                Toast.makeText(this, "Sample data added. Remove it any time in Settings.", Toast.LENGTH_LONG).show();
                finishTour();
            }));
        });
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override public void handleOnBackPressed() {
                if (index > 0) { index--; show(); } else finishTour();
            }
        });
        show();
    }

    @Override
    protected void onSaveInstanceState(Bundle out) {
        super.onSaveInstanceState(out);
        out.putInt("index", index);
    }

    private void show() {
        b.page.setAlpha(0f);
        b.tvEmoji.setText(PAGES[index][0]);
        b.tvTitle.setText(PAGES[index][1]);
        b.tvText.setText(PAGES[index][2]);
        b.page.animate().alpha(1f).setDuration(200).start();

        StringBuilder dots = new StringBuilder();
        for (int i = 0; i < PAGES.length; i++) dots.append(i == index ? "\u25CF " : "\u25CB ");
        b.tvDots.setText(dots.toString().trim());
        boolean last = index == PAGES.length - 1;
        b.btnNext.setText(last ? "Get started" : "Next");
        b.btnSample.setVisibility(last ? View.VISIBLE : View.GONE);
        b.btnSkip.setVisibility(last ? View.INVISIBLE : View.VISIBLE);
    }

    private void finishTour() {
        markDone();
        finish();
    }
}
