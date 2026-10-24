package com.example.campuscompanion.ui;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AppCompatActivity;

import com.example.campuscompanion.MainActivity;
import com.example.campuscompanion.auth.UserSession;
import com.example.campuscompanion.databinding.ActivityCompleteProfileBinding;
import com.example.campuscompanion.model.University;
import com.facebook.login.LoginManager;
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
 * Shown after the first Google / Facebook sign-in: asks for name, university and section.
 *  - type 1 university (has email domains): only a Google account with that university email works
 *    (a Facebook email is not proof of a university mailbox)
 *  - type 2 university (no domains): needs the class code from the CR
 */
public class CompleteProfileActivity extends AppCompatActivity {

    public static final String EXTRA_NAME = "name";
    public static final String EXTRA_EMAIL = "email";

    private ActivityCompleteProfileBinding b;
    private String email = "";
    /** "google.com", "facebook.com", ... : the provider used for THIS sign-in (same value the rules see). */
    private String provider = "";

    private final List<University> universities = new ArrayList<>();
    private University selected;
    private String selectedSection;

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

        b.tilSection.setEnabled(false);
        b.actUniversity.setOnItemClickListener((p, v, pos, id) -> onUniversityChosen(universities.get(pos)));
        b.actSection.setOnItemClickListener((p, v, pos, id) ->
                selectedSection = String.valueOf(p.getItemAtPosition(pos)));
        b.btnContinue.setOnClickListener(v -> save());

        // Going back cancels the sign-up: sign out so the login screen starts clean
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                signOutAll();
                finish();
            }
        });

        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) {
            finish();
            return;
        }
        user.getIdToken(false).addOnSuccessListener(r -> {
            provider = r.getSignInProvider() == null ? "" : r.getSignInProvider();
            autoPickFromEmail();
        });
        loadUniversities();
    }

    private void signOutAll() {
        FirebaseAuth.getInstance().signOut();
        LoginManager.getInstance().logOut();
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

    /** Google accounts with a university email pick that university automatically. */
    private boolean emailIsTrusted() {
        return "google.com".equals(provider) && !email.isEmpty();
    }

    private String emailDomain() {
        int at = email.lastIndexOf('@');
        return at < 0 ? "" : email.substring(at + 1).toLowerCase(Locale.ROOT);
    }

    private void autoPickFromEmail() {
        if (!emailIsTrusted() || selected != null) return;
        for (University u : universities) {
            if (u.domains.contains(emailDomain())) {
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
            b.tvUniHint.setText(emailIsTrusted() && u.domains.contains(emailDomain())
                    ? "Your " + u.name + " email is confirmed."
                    : "This university needs a verified university email. Sign in with Google using your "
                    + u.name + " email, or create an account with it using email sign-up.");
        } else {
            b.tilCode.setVisibility(View.VISIBLE);
            b.tvUniHint.setText("Ask your class representative for the class code.");
        }
    }

    private void save() {
        String name = text(b.etName);
        String code = text(b.etCode).toUpperCase(Locale.ROOT);

        b.tilName.setError(null);
        b.tilUniversity.setError(null);
        b.tilSection.setError(null);
        b.tilCode.setError(null);
        if (name.isEmpty()) { b.tilName.setError("Enter your name"); return; }
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
        if (uni.usesEmailDomain()) {
            if (!emailIsTrusted() || !uni.domains.contains(emailDomain())) {
                b.tilUniversity.setError("Needs a verified " + uni.name + " email (use Google or email sign-up)");
                return;
            }
        } else if (code.isEmpty()) {
            b.tilCode.setError("Enter the class code");
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
        profile.put("email", email);
        profile.put("university", uni.code);
        profile.put("section", section);
        profile.put("groupId", UserSession.groupId(uni.code, section));
        profile.put("role", "student"); // CR role is granted by hand in the console
        if (!uni.usesEmailDomain()) profile.put("joinCode", code);

        FirebaseFirestore.getInstance().collection("users").document(uid).set(profile)
                .addOnSuccessListener(v -> {
                    UserSession.save(this, name, uni.code, section, "student");
                    Intent i = new Intent(this, MainActivity.class);
                    i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                    startActivity(i);
                    finish();
                })
                .addOnFailureListener(e -> {
                    setLoading(false);
                    Toast.makeText(this, uni.usesEmailDomain()
                                    ? "Could not save profile: " + e.getMessage()
                                    : "Wrong class code, or this section is not set up yet.",
                            Toast.LENGTH_LONG).show();
                });
    }

    private void setLoading(boolean loading) {
        b.btnContinue.setEnabled(!loading);
        b.btnContinue.setText(loading ? "Saving..." : "Continue");
    }

    private static String text(android.widget.TextView et) {
        return et.getText() == null ? "" : et.getText().toString().trim();
    }
}
