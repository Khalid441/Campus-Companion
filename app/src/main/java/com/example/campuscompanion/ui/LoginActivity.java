package com.example.campuscompanion.ui;

import android.content.Intent;
import android.os.Bundle;
import android.os.SystemClock;
import android.util.Patterns;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.splashscreen.SplashScreen;

import com.example.campuscompanion.MainActivity;
import com.example.campuscompanion.auth.UserSession;
import com.example.campuscompanion.databinding.ActivityLoginBinding;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;

public class LoginActivity extends AppCompatActivity {

    private ActivityLoginBinding b;
    private FirebaseAuth auth;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        // Must come before super.onCreate(): swaps the splash theme for the normal one
        SplashScreen splash = SplashScreen.installSplashScreen(this);
        super.onCreate(savedInstanceState);
        auth = FirebaseAuth.getInstance();

        // Already signed in on this phone: skip straight to the app
        if (auth.getCurrentUser() != null && UserSession.section(this) != null) {
            goMain();
            return;
        }

        // Logged out: keep the logo on screen for a moment before the login form
        final long start = SystemClock.uptimeMillis();
        splash.setKeepOnScreenCondition(() -> SystemClock.uptimeMillis() - start < 600);

        b = ActivityLoginBinding.inflate(getLayoutInflater());
        setContentView(b.getRoot());

        b.btnLogin.setOnClickListener(v -> login());
        b.tvRegister.setOnClickListener(v ->
                startActivity(new Intent(this, RegisterActivity.class)));
    }

    private void login() {
        String email = text(b.etEmail);
        String password = text(b.etPassword);

        b.tilEmail.setError(null);
        b.tilPassword.setError(null);
        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            b.tilEmail.setError("Enter a valid email");
            return;
        }
        if (password.length() < 6) {
            b.tilPassword.setError("At least 6 characters");
            return;
        }

        setLoading(true);
        auth.signInWithEmailAndPassword(email, password)
                .addOnSuccessListener(result -> loadProfile(result.getUser().getUid()))
                .addOnFailureListener(e -> fail("Login failed: " + e.getMessage()));
    }

    /** Reads name / section / role from users/{uid} and caches it locally. */
    private void loadProfile(String uid) {
        FirebaseFirestore.getInstance().collection("users").document(uid).get()
                .addOnSuccessListener(doc -> {
                    if (!doc.exists() || doc.getString("section") == null) {
                        auth.signOut();
                        fail("Profile not found. Please create an account first.");
                        return;
                    }
                    UserSession.save(this, doc.getString("name"),
                            doc.getString("section"), doc.getString("role"));
                    goMain();
                })
                .addOnFailureListener(e -> fail("Could not load profile: " + e.getMessage()));
    }

    private void fail(String message) {
        setLoading(false);
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
    }

    private void setLoading(boolean loading) {
        b.btnLogin.setEnabled(!loading);
        b.btnLogin.setText(loading ? "Signing in..." : "Log in");
    }

    private void goMain() {
        Intent i = new Intent(this, MainActivity.class);
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(i);
        finish();
    }

    private static String text(com.google.android.material.textfield.TextInputEditText et) {
        return et.getText() == null ? "" : et.getText().toString().trim();
    }
}