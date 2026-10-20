package com.example.campuscompanion.ui;

import android.content.Intent;
import android.os.Bundle;
import android.util.Patterns;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.campuscompanion.MainActivity;
import com.example.campuscompanion.auth.UserSession;
import com.example.campuscompanion.databinding.ActivityRegisterBinding;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.HashMap;
import java.util.Map;

public class RegisterActivity extends AppCompatActivity {

    private ActivityRegisterBinding b;
    private FirebaseAuth auth;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        b = ActivityRegisterBinding.inflate(getLayoutInflater());
        setContentView(b.getRoot());
        auth = FirebaseAuth.getInstance();

        b.btnRegister.setOnClickListener(v -> register());
        b.tvLogin.setOnClickListener(v -> finish());
    }

    private void register() {
        String name = text(b.etName);
        String sectionRaw = text(b.etSection);
        String email = text(b.etEmail);
        String password = text(b.etPassword);

        b.tilName.setError(null);
        b.tilSection.setError(null);
        b.tilEmail.setError(null);
        b.tilPassword.setError(null);

        if (name.isEmpty()) { b.tilName.setError("Enter your name"); return; }
        String section = UserSession.normalizeSection(sectionRaw);
        if (section.isEmpty()) { b.tilSection.setError("Enter your section, e.g. CSE-2-A"); return; }
        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) { b.tilEmail.setError("Enter a valid email"); return; }
        if (password.length() < 6) { b.tilPassword.setError("At least 6 characters"); return; }

        setLoading(true);
        auth.createUserWithEmailAndPassword(email, password)
                .addOnSuccessListener(result -> {
                    String uid = result.getUser().getUid();
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
                            .addOnFailureListener(e -> fail("Could not save profile: " + e.getMessage()));
                })
                .addOnFailureListener(e -> fail("Sign up failed: " + e.getMessage()));
    }

    private void fail(String message) {
        setLoading(false);
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
    }

    private void setLoading(boolean loading) {
        b.btnRegister.setEnabled(!loading);
        b.btnRegister.setText(loading ? "Creating account..." : "Create account");
    }

    private static String text(com.google.android.material.textfield.TextInputEditText et) {
        return et.getText() == null ? "" : et.getText().toString().trim();
    }
}
