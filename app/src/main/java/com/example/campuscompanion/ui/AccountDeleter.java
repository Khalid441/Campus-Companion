package com.example.campuscompanion.ui;

import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.NetworkCapabilities;
import android.text.InputType;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.campuscompanion.auth.UserSession;
import com.example.campuscompanion.data.local.AppDatabase;
import com.example.campuscompanion.data.local.Deadline;
import com.example.campuscompanion.data.local.StudySession;
import com.example.campuscompanion.reminder.ReminderScheduler;
import com.example.campuscompanion.reminder.RevisionScheduler;
import com.example.campuscompanion.reminder.StudyReminderScheduler;
import com.example.campuscompanion.service.NextDeadlineService;
import com.example.campuscompanion.sync.BackupManager;
import com.example.campuscompanion.sync.NoticeManager;
import com.example.campuscompanion.sync.SyncManager;
import com.example.campuscompanion.widget.AttendanceWidget;
import com.example.campuscompanion.widget.NextDeadlineWidget;
import com.example.campuscompanion.widget.TodayWidget;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.firebase.auth.AuthCredential;
import com.google.firebase.auth.EmailAuthProvider;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.UserInfo;
import com.google.firebase.firestore.FirebaseFirestore;

/**
 * Permanent account deletion: cloud profile, backup and leaderboard entry, the sign-in itself, and
 * everything stored on this phone. Order matters: re-check the sign-in first, wipe the cloud data,
 * delete the sign-in last, then clean the phone.
 */
public final class AccountDeleter {
    private AccountDeleter() { }

    private static final long RECENT_MS = 4 * 60 * 1000L;

    public static void start(AppCompatActivity act) {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) {
            Toast.makeText(act, "You are not signed in.", Toast.LENGTH_SHORT).show();
            return;
        }
        String cr = UserSession.isCr(act)
                ? "\n\nYou are the class representative: the class keeps its notices and deadlines, "
                + "but nobody can post to it until a new CR is set up." : "";
        new MaterialAlertDialogBuilder(act)
                .setTitle("Delete your account?")
                .setMessage("This permanently deletes:\n"
                        + "• your login and profile\n"
                        + "• your cloud backup\n"
                        + "• your leaderboard entry\n"
                        + "• all data on this phone (deadlines, routine, attendance, topics, exams, stats)\n\n"
                        + "Things you posted to your class (notices, shared deadlines) stay for your classmates. "
                        + "This cannot be undone." + cr)
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Continue", (d, w) -> confirm(act, user))
                .show();
    }

    private static boolean usesPassword(FirebaseUser user) {
        for (UserInfo p : user.getProviderData()) {
            if (EmailAuthProvider.PROVIDER_ID.equals(p.getProviderId())) return true;
        }
        return false;
    }

    private static void confirm(AppCompatActivity act, FirebaseUser user) {
        final boolean password = usesPassword(user);
        int pad = (int) (20 * act.getResources().getDisplayMetrics().density);
        LinearLayout box = new LinearLayout(act);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(pad, pad / 2, pad, 0);

        EditText word = new EditText(act);
        word.setHint("Type DELETE to confirm");
        word.setSingleLine(true);
        box.addView(word);

        EditText pw = new EditText(act);
        pw.setHint("Your password");
        pw.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        if (password) box.addView(pw);

        new MaterialAlertDialogBuilder(act)
                .setTitle("Final confirmation")
                .setView(box)
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Delete forever", (d, w) -> {
                    if (!"DELETE".equals(word.getText().toString().trim())) {
                        Toast.makeText(act, "Type DELETE exactly to confirm.", Toast.LENGTH_LONG).show();
                        return;
                    }
                    if (!online(act)) {
                        Toast.makeText(act, "Connect to the internet first, so your cloud data can be deleted too.",
                                Toast.LENGTH_LONG).show();
                        return;
                    }
                    if (password) {
                        String p = pw.getText().toString();
                        if (p.isEmpty() || user.getEmail() == null) {
                            Toast.makeText(act, "Enter your password.", Toast.LENGTH_LONG).show();
                            return;
                        }
                        AuthCredential cred = EmailAuthProvider.getCredential(user.getEmail(), p);
                        user.reauthenticate(cred)
                                .addOnSuccessListener(r -> run(act, user))
                                .addOnFailureListener(e -> Toast.makeText(act,
                                        "That password is not right.", Toast.LENGTH_LONG).show());
                    } else {
                        long last = user.getMetadata() == null ? 0 : user.getMetadata().getLastSignInTimestamp();
                        if (System.currentTimeMillis() - last > RECENT_MS) {
                            new MaterialAlertDialogBuilder(act)
                                    .setTitle("Sign in again first")
                                    .setMessage("For your security, Google and Facebook accounts must have signed in "
                                            + "a few minutes ago. Log out, sign in again, then come straight back "
                                            + "to Settings and delete your account.")
                                    .setPositiveButton("OK", null)
                                    .show();
                        } else {
                            run(act, user);
                        }
                    }
                })
                .show();
    }

    private static boolean online(Context c) {
        try {
            ConnectivityManager cm = (ConnectivityManager) c.getSystemService(Context.CONNECTIVITY_SERVICE);
            NetworkCapabilities nc = cm.getNetworkCapabilities(cm.getActiveNetwork());
            return nc != null && nc.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET);
        } catch (Exception e) {
            return true;
        }
    }

    private static void run(AppCompatActivity act, FirebaseUser user) {
        Toast.makeText(act, "Deleting your account…", Toast.LENGTH_LONG).show();
        final String uid = user.getUid();
        final String group = UserSession.section(act);
        final Context app = act.getApplicationContext();
        FirebaseFirestore fs = FirebaseFirestore.getInstance();

        fs.collection("users").document(uid).collection("backup").document("latest").delete()
                .continueWithTask(t -> {
                    if (group == null) return com.google.android.gms.tasks.Tasks.forResult(null);
                    return fs.collection("sections").document(group).collection("leaderboard").document(uid).delete();
                })
                .continueWithTask(t -> fs.collection("users").document(uid).delete())
                .addOnSuccessListener(x -> user.delete()
                        .addOnSuccessListener(y -> wipePhone(app, uid, act))
                        .addOnFailureListener(e -> {
                            Toast.makeText(act, "Your cloud data is deleted, but the sign-in could not be removed. "
                                    + "Log out, sign in again and delete once more.", Toast.LENGTH_LONG).show();
                        }))
                .addOnFailureListener(e -> Toast.makeText(act,
                        "Could not delete your cloud data. Nothing was removed. Check your connection and try again.",
                        Toast.LENGTH_LONG).show());
    }

    private static void wipePhone(Context app, String uid, AppCompatActivity act) {
        AppDatabase.executor.execute(() -> {
            AppDatabase db = AppDatabase.get(app);
            try {
                for (Deadline d : db.deadlineDao().getPending()) ReminderScheduler.cancel(app, d.id);
                for (StudySession s : db.studyDao().getAllNow()) StudyReminderScheduler.cancel(app, s.id);
            } catch (Exception ignored) { }
            db.attendanceDao().deleteAllRecords();
            db.topicDao().deleteAll();
            db.attendanceDao().deleteAllCourses();
            db.routineDao().deleteAll();
            db.studyDao().deleteAll();
            db.deadlineDao().deleteAll();
            db.examDao().deleteAll();

            for (String prefix : new String[]{"study_stats_", "focus_stats_", "gamify_", "gpa_", "planner_"}) {
                app.deleteSharedPreferences(prefix + uid);
            }
            app.deleteSharedPreferences("db_owner");

            act.runOnUiThread(() -> {
                app.stopService(new Intent(app, NextDeadlineService.class));
                RevisionScheduler.cancel(app);
                BackupManager.setEnabled(app, false);
                SyncManager.stop(app);
                NoticeManager.stop();
                FirebaseAuth.getInstance().signOut();
                UserSession.clear(app);
                NextDeadlineWidget.refresh(app);
                TodayWidget.refresh(app);
                AttendanceWidget.refresh(app);
                Toast.makeText(app, "Your account was deleted.", Toast.LENGTH_LONG).show();
                Intent i = new Intent(app, LoginActivity.class);
                i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                app.startActivity(i);
                act.finish();
            });
        });
    }
}
