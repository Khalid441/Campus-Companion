package com.example.campuscompanion.ui;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AppCompatActivity;

import com.example.campuscompanion.MainActivity;
import com.example.campuscompanion.auth.UserSession;
import com.example.campuscompanion.databinding.ActivityCompleteProfileBinding;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.HashMap;
import java.util.Map;

/** Shown after the first Google sign-in: asks for the name and section, then creates the profile. */
public class CompleteProfileActivity extends AppCompatActivity {

    public static final String EXTRA_NAME = "name";
    public static final String EXTRA_EMAIL = "email";

    private ActivityCompleteProfileBinding b;
    private String email = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        b = ActivityCompleteProfileBinding.inflate(getLayoutInflater());
        setContentView(b.getRoot());

        String name = getIntent().getStringExtra(EXTRA_NAME);
        String mail = getIntent().getStringExtra(EXTRA_EMAIL);
        email = mail == null ? "" : mail;
        if (name != null) b.etName.setText(name);
        b.tvEmailInfo.setText(email.isEmpty()
                ? "Tell us a little about you"
                : "Signed in as " + email + "\nTell us a little about you");

        b.btnContinue.setOnClickListener(v -> save());

        // Going back cancels the sign-up: sign out so the login screen starts clean
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                FirebaseAuth.getInstance().signOut();
                finish();
            }
        });
    }

    private void save() {
        String name = text(b.etName);
        String section = UserSession.normalizeSection(text(b.etSection));

        b.tilName.setError(null);
        b.tilSection.setError(null);
        if (name.isEmpty()) {
            b.tilName.setError("Enter your name");
            return;
        }
        if (section.isEmpty()) {
            b.tilSection.setError("Enter your section, e.g. CSE-105-B");
            return;
        }

        String uid = FirebaseAuth.getInstance().getUid();
        if (uid == null) {
            Toast.makeText(this, "Session expired. Please sign in again.", Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        setLoading(true);
        Map<String, Object> profile = new HashMap<>();
        profile.put("name", name);
        profile.put("section", section);
        profile.put("role", "student"); // CR role is granted by hand in the console
        profile.put("email", email);

        FirebaseFirestore.getInstance().collection("users").document(uid).set(profile)
                .addOnSuccessListener(v -> {
                    UserSession.save(this, name, section, "student");
                    Intent i = new Intent(this, MainActivity.class);
                    i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                    startActivity(i);
                    finish();
                })
                .addOnFailureListener(e -> {
                    setLoading(false);
                    Toast.makeText(this, "Could not save profile: " + e.getMessage(),
                            Toast.LENGTH_LONG).show();
                });
    }

    private void setLoading(boolean loading) {
        b.btnContinue.setEnabled(!loading);
        b.btnContinue.setText(loading ? "Saving..." : "Continue");
    }

    private static String text(com.google.android.material.textfield.TextInputEditText et) {
        return et.getText() == null ? "" : et.getText().toString().trim();
    }
}
