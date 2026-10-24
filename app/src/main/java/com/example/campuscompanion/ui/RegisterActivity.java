package com.example.campuscompanion.ui;

import android.content.Intent;
import android.os.Bundle;
import android.util.Patterns;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.campuscompanion.MainActivity;
import com.example.campuscompanion.auth.UserSession;
import com.example.campuscompanion.databinding.ActivityRegisterBinding;
import com.example.campuscompanion.model.University;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Two kinds of university:
 *  - type 1 (university has email domains): sign up with the university email, which must be verified
 *  - type 2 (no domains): sign up with any email + the class code the CR shares
 */
public class RegisterActivity extends AppCompatActivity {

    private ActivityRegisterBinding b;
    private FirebaseAuth auth;

    private final List<University> universities = new ArrayList<>();
    private University selected;
    private String selectedSection;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        b = ActivityRegisterBinding.inflate(getLayoutInflater());
        setContentView(b.getRoot());
        auth = FirebaseAuth.getInstance();

        b.btnRegister.setOnClickListener(v -> register());
        b.tvLogin.setOnClickListener(v -> finish());

        b.tilSection.setEnabled(false);
        b.actUniversity.setOnItemClickListener((p, v, pos, id) -> onUniversityChosen(universities.get(pos)));
        b.actSection.setOnItemClickListener((p, v, pos, id) ->
                selectedSection = String.valueOf(p.getItemAtPosition(pos)));
        // When the user leaves the email box, pick the university from the email domain
        b.etEmail.setOnFocusChangeListener((v, hasFocus) -> { if (!hasFocus) autoPickFromEmail(); });

        loadUniversities();
    }

    private void loadUniversities() {
        FirebaseFirestore.getInstance().collection("universities").get()
                .addOnSuccessListener(snap -> {
                    universities.clear();
                    for (DocumentSnapshot d : snap.getDocuments()) universities.add(University.from(d));
                    b.actUniversity.setAdapter(new ArrayAdapter<>(this,
                            android.R.layout.simple_list_item_1, universities));
                    autoPickFromEmail();
                })
                .addOnFailureListener(e -> Toast.makeText(this,
                        "Could not load universities: " + e.getMessage(), Toast.LENGTH_LONG).show());
    }

    private static String domainOf(String email) {
        int at = email.lastIndexOf('@');
        return at < 0 ? "" : email.substring(at + 1).trim().toLowerCase(Locale.ROOT);
    }

    /** If the email's domain belongs to a university, select it automatically. */
    private void autoPickFromEmail() {
        String domain = domainOf(text(b.etEmail));
        if (domain.isEmpty()) return;
        for (University u : universities) {
            if (u.domains.contains(domain)) {
                b.actUniversity.setText(u.name, false);
                onUniversityChosen(u);
                return;
            }
        }
    }

    private void onUniversityChosen(University u) {
        selected = u;
        selectedSection = null;
        b.actSection.setText("", false);
        b.actSection.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_list_item_1,
                new ArrayList<>(u.sections)));
        b.tilSection.setEnabled(true);
        if (u.typesOwnSection()) {
            // type 1 with free sections: type it (existing sections are shown as suggestions)
            b.actSection.setInputType(android.text.InputType.TYPE_CLASS_TEXT
                    | android.text.InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS);
            b.tilSection.setHint("Section (e.g. CSE-105-B)");
        } else {
            b.actSection.setInputType(android.text.InputType.TYPE_NULL);
            b.tilSection.setHint("Section");
        }

        b.tvUniHint.setVisibility(View.VISIBLE);
        if (u.usesEmailDomain()) {
            b.tilCode.setVisibility(View.GONE);
            b.tvUniHint.setText("Use your " + u.name + " email (" + domainList(u)
                    + "). We will send a verification link.");
        } else {
            b.tilCode.setVisibility(View.VISIBLE);
            b.tvUniHint.setText("Ask your class representative for the class code.");
        }
    }

    private static String domainList(University u) {
        StringBuilder sb = new StringBuilder();
        for (String d : u.domains) {
            if (sb.length() > 0) sb.append(", ");
            sb.append('@').append(d);
        }
        return sb.toString();
    }

    private void register() {
        final String name = text(b.etName);
        final String email = text(b.etEmail);
        final String password = text(b.etPassword);
        final String code = text(b.etCode).toUpperCase(Locale.ROOT);

        b.tilName.setError(null);
        b.tilUniversity.setError(null);
        b.tilSection.setError(null);
        b.tilEmail.setError(null);
        b.tilCode.setError(null);
        b.tilPassword.setError(null);

        if (name.isEmpty()) { b.tilName.setError("Enter your name"); return; }
        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) { b.tilEmail.setError("Enter a valid email"); return; }
        autoPickFromEmail();
        if (selected == null) { b.tilUniversity.setError("Choose your university"); return; }
        final University uni = selected;
        final String section = uni.typesOwnSection()
                ? UserSession.normalizeSection(text(b.actSection)) : selectedSection;
        if (section == null || section.isEmpty() || section.length() > 30) {
            b.tilSection.setError(uni.typesOwnSection()
                    ? "Enter your section, e.g. CSE-105-B" : "Choose your section");
            return;
        }
        final boolean emailBased = uni.usesEmailDomain();

        if (emailBased && !uni.domains.contains(domainOf(email))) {
            b.tilEmail.setError("Use your " + uni.name + " email (" + domainList(uni) + ")");
            return;
        }
        if (!emailBased && code.isEmpty()) { b.tilCode.setError("Enter the class code"); return; }
        if (password.length() < 6) { b.tilPassword.setError("At least 6 characters"); return; }

        setLoading(true);
        auth.createUserWithEmailAndPassword(email, password)
                .addOnSuccessListener(result -> {
                    FirebaseUser user = result.getUser();
                    if (user == null) { fail("Sign up failed"); return; }
                    if (emailBased) {
                        // Profile is created after the email is verified (the rules require it)
                        user.sendEmailVerification();
                        UserSession.savePending(this, user.getUid(), name, uni.code, section, email);
                        startActivity(new Intent(this, VerifyEmailActivity.class));
                        finish();
                    } else {
                        createProfile(user, name, email, uni.code, section, code);
                    }
                })
                .addOnFailureListener(e -> fail("Sign up failed: " + e.getMessage()));
    }

    /** Type 2: the rules accept this only if the class code matches the section's code. */
    private void createProfile(FirebaseUser user, String name, String email,
                               String university, String section, String code) {
        Map<String, Object> profile = new HashMap<>();
        profile.put("name", name);
        profile.put("email", email);
        profile.put("university", university);
        profile.put("section", section);
        profile.put("groupId", UserSession.groupId(university, section));
        profile.put("role", "student"); // CR role is granted by hand in the console
        profile.put("joinCode", code);

        FirebaseFirestore.getInstance().collection("users").document(user.getUid()).set(profile)
                .addOnSuccessListener(v -> {
                    UserSession.save(this, name, university, section, "student");
                    Intent i = new Intent(this, MainActivity.class);
                    i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                    startActivity(i);
                    finish();
                })
                .addOnFailureListener(e -> {
                    // Remove the half-created account so the student can try again
                    user.delete();
                    auth.signOut();
                    fail("Wrong class code, or this section is not set up yet.");
                });
    }

    private void fail(String message) {
        setLoading(false);
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
    }

    private void setLoading(boolean loading) {
        b.btnRegister.setEnabled(!loading);
        b.btnRegister.setText(loading ? "Creating account..." : "Create account");
    }

    private static String text(android.widget.TextView et) {
        return et.getText() == null ? "" : et.getText().toString().trim();
    }
}
