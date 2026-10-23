package com.example.campuscompanion.ui;

import android.content.Intent;
import android.os.Bundle;
import android.os.CancellationSignal;
import android.os.SystemClock;
import android.util.Patterns;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import java.util.Arrays;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.splashscreen.SplashScreen;
import androidx.credentials.Credential;
import androidx.credentials.CredentialManager;
import androidx.credentials.CredentialManagerCallback;
import androidx.credentials.CustomCredential;
import androidx.credentials.GetCredentialRequest;
import androidx.credentials.GetCredentialResponse;
import androidx.credentials.exceptions.GetCredentialCancellationException;
import androidx.credentials.exceptions.GetCredentialException;
import androidx.credentials.exceptions.NoCredentialException;

import com.example.campuscompanion.MainActivity;
import com.example.campuscompanion.R;
import com.example.campuscompanion.auth.UserSession;
import com.example.campuscompanion.databinding.ActivityLoginBinding;
import com.facebook.AccessToken;
import com.facebook.CallbackManager;
import com.facebook.FacebookCallback;
import com.facebook.FacebookException;
import com.facebook.login.LoginManager;
import com.facebook.login.LoginResult;
import com.google.android.libraries.identity.googleid.GetGoogleIdOption;
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential;
import com.google.firebase.auth.AuthCredential;
import com.google.firebase.auth.FacebookAuthProvider;
import com.google.firebase.auth.FirebaseAuthUserCollisionException;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.GoogleAuthProvider;
import com.google.firebase.firestore.FirebaseFirestore;

public class LoginActivity extends AppCompatActivity {

    private ActivityLoginBinding b;
    private FirebaseAuth auth;
    private CallbackManager facebookCallbacks;

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
        b.btnGoogle.setOnClickListener(v -> googleSignIn());
        b.btnFacebook.setOnClickListener(v -> facebookSignIn());
        b.tvForgot.setOnClickListener(v -> resetPassword());
        b.tvRegister.setOnClickListener(v ->
                startActivity(new Intent(this, RegisterActivity.class)));

        animateIn(b.loginContent);
    }

    /** Fade and slide the form elements in one after another. */
    private void animateIn(ViewGroup group) {
        float shift = 24 * getResources().getDisplayMetrics().density;
        for (int i = 0; i < group.getChildCount(); i++) {
            View v = group.getChildAt(i);
            v.setAlpha(0f);
            v.setTranslationY(shift);
            v.animate().alpha(1f).translationY(0f)
                    .setStartDelay(70L * i).setDuration(350).start();
        }
    }

    // ------------------------------------------------------------------
    // Email and password
    // ------------------------------------------------------------------

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
                .addOnSuccessListener(result -> loadProfile(result.getUser(), false))
                .addOnFailureListener(e -> fail("Login failed: " + e.getMessage()));
    }

    private void resetPassword() {
        String email = text(b.etEmail);
        b.tilEmail.setError(null);
        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            b.tilEmail.setError("Enter your email first");
            return;
        }
        setLoading(true);
        auth.sendPasswordResetEmail(email)
                .addOnSuccessListener(v -> {
                    setLoading(false);
                    Toast.makeText(this, "Reset link sent to " + email + " (check spam too)",
                            Toast.LENGTH_LONG).show();
                })
                .addOnFailureListener(e -> fail("Could not send reset email: " + e.getMessage()));
    }

    // ------------------------------------------------------------------
    // Google (Credential Manager -> Firebase)
    // ------------------------------------------------------------------

    private void googleSignIn() {
        setLoading(true);

        GetGoogleIdOption option = new GetGoogleIdOption.Builder()
                .setFilterByAuthorizedAccounts(false)     // show every Google account on the phone
                .setServerClientId(getString(R.string.default_web_client_id))
                .build();
        GetCredentialRequest request = new GetCredentialRequest.Builder()
                .addCredentialOption(option)
                .build();

        CredentialManager.create(this).getCredentialAsync(
                this, request, new CancellationSignal(), ContextCompat.getMainExecutor(this),
                new CredentialManagerCallback<GetCredentialResponse, GetCredentialException>() {
                    @Override
                    public void onResult(GetCredentialResponse response) {
                        handleGoogleCredential(response.getCredential());
                    }

                    @Override
                    public void onError(GetCredentialException e) {
                        setLoading(false);
                        if (e instanceof GetCredentialCancellationException) return;   // user closed it
                        if (e instanceof NoCredentialException) {
                            Toast.makeText(LoginActivity.this,
                                    "No Google account found on this phone. Add one in Settings.",
                                    Toast.LENGTH_LONG).show();
                        } else {
                            Toast.makeText(LoginActivity.this,
                                    "Google sign-in failed: " + e.getMessage(),
                                    Toast.LENGTH_LONG).show();
                        }
                    }
                });
    }

    private void handleGoogleCredential(Credential credential) {
        try {
            if (credential instanceof CustomCredential
                    && GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
                    .equals(credential.getType())) {
                GoogleIdTokenCredential google =
                        GoogleIdTokenCredential.createFrom(((CustomCredential) credential).getData());
                AuthCredential firebaseCredential =
                        GoogleAuthProvider.getCredential(google.getIdToken(), null);
                auth.signInWithCredential(firebaseCredential)
                        .addOnSuccessListener(result -> loadProfile(result.getUser(), true))
                        .addOnFailureListener(e -> fail("Google sign-in failed: " + e.getMessage()));
                return;
            }
            fail("Unsupported sign-in method");
        } catch (Exception e) {
            fail("Google sign-in failed: " + e.getMessage());
        }
    }

    // ------------------------------------------------------------------
    // Facebook (Facebook SDK -> Firebase)
    // ------------------------------------------------------------------

    private void facebookSignIn() {
        setLoading(true);
        facebookCallbacks = CallbackManager.Factory.create();
        LoginManager.getInstance().registerCallback(facebookCallbacks,
                new FacebookCallback<LoginResult>() {
                    @Override
                    public void onSuccess(LoginResult result) {
                        firebaseAuthWithFacebook(result.getAccessToken());
                    }

                    @Override
                    public void onCancel() {
                        setLoading(false);   // user closed the Facebook screen
                    }

                    @Override
                    public void onError(FacebookException e) {
                        fail("Facebook sign-in failed: " + e.getMessage());
                    }
                });
        LoginManager.getInstance().logInWithReadPermissions(this,
                Arrays.asList("email", "public_profile"));
    }

    private void firebaseAuthWithFacebook(AccessToken token) {
        AuthCredential credential = FacebookAuthProvider.getCredential(token.getToken());
        auth.signInWithCredential(credential)
                .addOnSuccessListener(result -> loadProfile(result.getUser(), true))
                .addOnFailureListener(e -> {
                    LoginManager.getInstance().logOut();
                    if (e instanceof FirebaseAuthUserCollisionException) {
                        fail("This email is already registered with another sign-in method. "
                                + "Please use that one.");
                    } else {
                        fail("Facebook sign-in failed: " + e.getMessage());
                    }
                });
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (facebookCallbacks != null) {
            facebookCallbacks.onActivityResult(requestCode, resultCode, data);
        }
    }

    // ------------------------------------------------------------------
    // Profile
    // ------------------------------------------------------------------

    /** Reads name / section / role from users/{uid} and caches it locally. */
    private void loadProfile(FirebaseUser user, boolean viaSocial) {
        if (user == null) {
            fail("Sign-in failed. Please try again.");
            return;
        }
        FirebaseFirestore.getInstance().collection("users").document(user.getUid()).get()
                .addOnSuccessListener(doc -> {
                    if (!doc.exists() || doc.getString("section") == null) {
                        if (viaSocial) {
                            // First time with Google or Facebook: ask for the section, then create the profile
                            setLoading(false);
                            startActivity(new Intent(this, CompleteProfileActivity.class)
                                    .putExtra(CompleteProfileActivity.EXTRA_NAME, user.getDisplayName())
                                    .putExtra(CompleteProfileActivity.EXTRA_EMAIL, user.getEmail()));
                        } else {
                            auth.signOut();
                            fail("Profile not found. Please create an account first.");
                        }
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
        b.progress.setVisibility(loading ? View.VISIBLE : View.INVISIBLE);
        b.btnLogin.setEnabled(!loading);
        b.btnGoogle.setEnabled(!loading);
        b.btnFacebook.setEnabled(!loading);
        b.btnLogin.setText(loading ? "Please wait..." : "Log in");
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
