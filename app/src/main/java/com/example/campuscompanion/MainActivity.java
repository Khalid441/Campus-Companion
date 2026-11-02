package com.example.campuscompanion;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;

import androidx.activity.OnBackPressedCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;

import com.example.campuscompanion.auth.UserSession;
import com.example.campuscompanion.data.local.AppDatabase;
import com.example.campuscompanion.data.local.StudySession;
import com.example.campuscompanion.databinding.ActivityHostBinding;
import com.example.campuscompanion.notification.NotificationHelper;
import com.example.campuscompanion.reminder.ReminderScheduler;
import com.example.campuscompanion.reminder.RevisionScheduler;
import com.example.campuscompanion.reminder.StudyReminderScheduler;
import com.example.campuscompanion.sync.NoticeManager;
import com.example.campuscompanion.sync.SyncManager;
import com.example.campuscompanion.ui.AttendanceFragment;
import com.example.campuscompanion.ui.DeadlinesFragment;
import com.example.campuscompanion.ui.HomeFragment;
import com.example.campuscompanion.ui.LoginActivity;
import com.example.campuscompanion.ui.NoticesFragment;
import com.example.campuscompanion.ui.StudyFragment;
import com.google.firebase.auth.FirebaseAuth;

/**
 * The one screen that owns the bottom bar. The five tabs are fragments that are shown and hidden,
 * so the bar never gets rebuilt when you switch (like Messenger).
 */
public class MainActivity extends AppCompatActivity {

    /** Open a specific tab (R.id.nav_*) when launching this screen. */
    public static final String EXTRA_TAB = "tab";
    private static final String STATE_TAB = "currentTab";

    private ActivityHostBinding b;
    private int currentTab = R.id.nav_home;

    private final ActivityResultLauncher<String> notificationPermission =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), granted -> { });

    /** Intent that opens this screen on the given tab. */
    public static Intent tabIntent(Context c, int navId) {
        return new Intent(c, MainActivity.class)
                .putExtra(EXTRA_TAB, navId)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Not signed in: back to the login screen
        if (FirebaseAuth.getInstance().getCurrentUser() == null || !UserSession.hasProfile(this)) {
            Intent i = new Intent(this, LoginActivity.class);
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(i);
            finish();
            return;
        }

        b = ActivityHostBinding.inflate(getLayoutInflater());
        setContentView(b.getRoot());

        NotificationHelper.createChannels(this);
        if (Build.VERSION.SDK_INT >= 33 && !NotificationHelper.canNotify(this)) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS);
        }

        // Real-time sync of shared deadlines + notices, and background fallback
        SyncManager.start(this, UserSession.section(this));
        SyncManager.schedulePeriodic(this);
        NoticeManager.start(this, UserSession.section(this));

        // Re-arm this account's reminders (alarms are cancelled on logout)
        Context appCtx = getApplicationContext();
        AppDatabase.executor.execute(() -> {
            for (com.example.campuscompanion.data.local.Deadline d
                    : AppDatabase.get(appCtx).deadlineDao().getPending()) {
                ReminderScheduler.schedule(appCtx, d);
            }
            for (StudySession s : AppDatabase.get(appCtx).studyDao().getAllNow()) {
                StudyReminderScheduler.schedule(appCtx, s);
            }
        });
        RevisionScheduler.ensure(this);

        if (savedInstanceState != null) {
            currentTab = savedInstanceState.getInt(STATE_TAB, R.id.nav_home);
        }
        int wanted = getIntent().getIntExtra(EXTRA_TAB, 0);
        if (savedInstanceState == null && wanted != 0) currentTab = wanted;

        b.bottomNav.setOnItemSelectedListener(item -> {
            showTab(item.getItemId(), true);
            return true;
        });
        b.bottomNav.setSelectedItemId(currentTab);
        showTab(currentTab, false);

        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                // Tabs replace each other, so Back goes to Home first, then leaves
                if (currentTab != R.id.nav_home) {
                    b.bottomNav.setSelectedItemId(R.id.nav_home);
                } else {
                    finish();
                }
            }
        });
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        int wanted = intent.getIntExtra(EXTRA_TAB, 0);
        if (b != null && wanted != 0) b.bottomNav.setSelectedItemId(wanted);
    }

    @Override
    protected void onSaveInstanceState(Bundle out) {
        super.onSaveInstanceState(out);
        out.putInt(STATE_TAB, currentTab);
    }

    /** Switch tab from code (e.g. a Home card). */
    public void selectTab(int navId) {
        if (b != null) b.bottomNav.setSelectedItemId(navId);
    }

    private static String tagOf(int navId) {
        return "tab_" + navId;
    }

    private Fragment create(int navId) {
        if (navId == R.id.nav_notices) return new NoticesFragment();
        if (navId == R.id.nav_deadlines) return new DeadlinesFragment();
        if (navId == R.id.nav_attendance) return new AttendanceFragment();
        if (navId == R.id.nav_study) return new StudyFragment();
        return new HomeFragment();
    }

    /** Shows one tab and hides the rest; tabs are created the first time they are opened. */
    private void showTab(int navId, boolean animate) {
        FragmentManager fm = getSupportFragmentManager();
        Fragment target = fm.findFragmentByTag(tagOf(navId));
        FragmentTransaction tx = fm.beginTransaction().setReorderingAllowed(true);
        if (animate) tx.setCustomAnimations(R.anim.tab_fade_in, 0);

        for (Fragment f : fm.getFragments()) {
            if (f != target && !f.isHidden()) tx.hide(f);
        }
        if (target == null) {
            tx.add(R.id.tabContainer, create(navId), tagOf(navId));
        } else if (target.isHidden()) {
            tx.show(target);
        }
        tx.commit();
        currentTab = navId;
    }
}
