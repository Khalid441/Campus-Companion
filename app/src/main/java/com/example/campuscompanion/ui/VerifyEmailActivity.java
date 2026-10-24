package com.example.campuscompanion.ui;

import android.content.Intent;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.campuscompanion.MainActivity;
import com.example.campuscompanion.auth.UserSession;
import com.google.android.material.button.MaterialButton;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.HashMap;
import java.util.Map;

/**
 * University-email accounts (type 1) wait here until the email link is tapped.
 * Only then is the profile created, because the Firestore rules require a verified email.
 */
public class VerifyEmailActivity extends AppCompatActivity {

    private MaterialButton btnVerified;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null || !UserSession.hasPending(this, user.getUid())) {
            goLogin();
            return;
        }

        int pad = (int) (24 * getResources().getDisplayMetrics().density);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER);
        root.setPadding(pad, pad, pad, pad);

        TextView title = new TextView(this);
        title.setText("Verify your email");
        title.setTextSize(24);
        title.setGravity(Gravity.CENTER);
        root.addView(title);

        TextView info = new TextView(this);
        info.setText("We sent a link to\n" + UserSession.pendingEmail(this)
                + "\n\nOpen it, then come back and tap the button below.");
        info.setGravity(Gravity.CENTER);
        info.setPadding(0, pad / 2, 0, pad);
        root.addView(info);

        btnVerified = new MaterialButton(this);
        btnVerified.setText("I have verified");
        btnVerified.setOnClickListener(v -> checkVerified());
        root.addView(btnVerified);

        MaterialButton resend = new MaterialButton(this, null,
                com.google.android.material.R.attr.materialButtonOutlinedStyle);
        resend.setText("Resend email");
        resend.setOnClickListener(v -> user.sendEmailVerification().addOnCompleteListener(t ->
                Toast.makeText(this, t.isSuccessful() ? "Email sent again"
                        : "Could not send: " + t.getException().getMessage(), Toast.LENGTH_LONG).show()));
        root.addView(resend);

        MaterialButton cancel = new MaterialButton(this, null,
                android.R.attr.borderlessButtonStyle);
        cancel.setText("Cancel");
        cancel.setOnClickListener(v -> {
            UserSession.clearPending(this);
            FirebaseAuth.getInstance().signOut();
            goLogin();
        });
        root.addView(cancel);

        setContentView(root);
    }

    private void checkVerified() {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) { goLogin(); return; }
        btnVerified.setEnabled(false);

        user.reload().addOnCompleteListener(t -> {
            FirebaseUser u = FirebaseAuth.getInstance().getCurrentUser();
            if (!t.isSuccessful() || u == null || !u.isEmailVerified()) {
                btnVerified.setEnabled(true);
                Toast.makeText(this, "Not verified yet. Open the link in the email first.",
                        Toast.LENGTH_LONG).show();
                return;
            }
            // Refresh the sign-in token so it carries email_verified = true
            u.getIdToken(true).addOnCompleteListener(tt -> createProfile(u));
        });
    }

    private void createProfile(FirebaseUser user) {
        final String name = UserSession.pendingName(this);
        final String university = UserSession.pendingUniversity(this);
        final String section = UserSession.pendingSection(this);

        Map<String, Object> profile = new HashMap<>();
        profile.put("name", name);
        profile.put("email", UserSession.pendingEmail(this));
        profile.put("university", university);
        profile.put("section", section);
        profile.put("groupId", UserSession.groupId(university, section));
        profile.put("role", "student"); // CR role is granted by hand in the console

        FirebaseFirestore.getInstance().collection("users").document(user.getUid()).set(profile)
                .addOnSuccessListener(v -> {
                    UserSession.clearPending(this);
                    UserSession.save(this, name, university, section, "student");
                    Intent i = new Intent(this, MainActivity.class);
                    i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                    startActivity(i);
                    finish();
                })
                .addOnFailureListener(e -> {
                    btnVerified.setEnabled(true);
                    Toast.makeText(this, "Could not create profile: " + e.getMessage(),
                            Toast.LENGTH_LONG).show();
                });
    }

    private void goLogin() {
        Intent i = new Intent(this, LoginActivity.class);
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(i);
        finish();
    }
}
