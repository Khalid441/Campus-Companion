package com.example.campuscompanion.ui;

import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.campuscompanion.auth.UserSession;
import com.example.campuscompanion.databinding.ActivityResourcesBinding;
import com.example.campuscompanion.databinding.DialogResourceBinding;
import com.example.campuscompanion.databinding.ItemResourceBinding;
import com.example.campuscompanion.util.TimeUtil;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.CollectionReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.Query;

import java.util.HashMap;
import java.util.Map;

/** Links shared by the class representative (slides, folders, past papers). Everyone can open them. */
public class ResourcesActivity extends AppCompatActivity {

    private ActivityResourcesBinding b;
    private ListenerRegistration registration;
    private String section;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        b = ActivityResourcesBinding.inflate(getLayoutInflater());
        setContentView(b.getRoot());
        section = UserSession.section(this);

        boolean cr = UserSession.isCr(this);
        b.fabRes.setVisibility(cr ? View.VISIBLE : View.GONE);
        b.fabRes.setOnClickListener(v -> showAddDialog());
        b.tvSub.setText(cr ? "Share links with your whole class" : "Links shared by your class representative");
    }

    private CollectionReference collection() {
        return FirebaseFirestore.getInstance()
                .collection("sections").document(section).collection("resources");
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (section == null || section.isEmpty()) return;
        registration = collection().orderBy("createdAt", Query.Direction.DESCENDING).limit(100)
                .addSnapshotListener((snap, error) -> {
                    if (error != null || snap == null || isFinishing() || isDestroyed()) return;
                    render(snap.getDocuments());
                });
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (registration != null) registration.remove();
        registration = null;
    }

    private void render(java.util.List<DocumentSnapshot> docs) {
        b.resList.removeAllViews();
        b.tvResEmpty.setVisibility(docs.isEmpty() ? View.VISIBLE : View.GONE);
        boolean cr = UserSession.isCr(this);
        for (DocumentSnapshot d : docs) {
            ItemResourceBinding r = ItemResourceBinding.inflate(getLayoutInflater(), b.resList, false);
            String title = d.getString("title");
            String url = d.getString("url");
            String note = d.getString("note");
            Timestamp ts = d.getTimestamp("createdAt", DocumentSnapshot.ServerTimestampBehavior.ESTIMATE);

            r.tvResTitle.setText(title == null ? "(untitled)" : title);
            r.tvResNote.setText(note == null ? "" : note);
            r.tvResNote.setVisibility(note == null || note.isEmpty() ? View.GONE : View.VISIBLE);
            r.tvResUrl.setText(url == null ? "" : url);
            String author = d.getString("author");
            r.tvResMeta.setText((author == null ? "" : author + "  •  ")
                    + (ts == null ? "" : TimeUtil.friendly(ts.toDate().getTime())));
            r.resCard.setOnClickListener(v -> open(url));
            if (cr) {
                r.resCard.setOnLongClickListener(v -> {
                    new MaterialAlertDialogBuilder(this)
                            .setTitle("Remove this resource?")
                            .setMessage(title)
                            .setPositiveButton("Remove", (dlg, w) -> d.getReference().delete())
                            .setNegativeButton("Cancel", null)
                            .show();
                    return true;
                });
            }
            b.resList.addView(r.getRoot());
        }
    }

    private void open(String url) {
        if (url == null || url.isEmpty()) return;
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
        } catch (ActivityNotFoundException e) {
            Toast.makeText(this, "No app can open this link", Toast.LENGTH_SHORT).show();
        }
    }

    private void showAddDialog() {
        DialogResourceBinding d = DialogResourceBinding.inflate(getLayoutInflater());
        androidx.appcompat.app.AlertDialog dialog = new MaterialAlertDialogBuilder(this)
                .setTitle("Share a resource")
                .setView(d.getRoot())
                .setPositiveButton("Share", null)
                .setNegativeButton("Cancel", null)
                .create();
        dialog.show();
        dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String title = text(d.etResTitle);
            String url = text(d.etResUrl);
            String note = text(d.etResNote);
            d.tilResTitle.setError(null);
            d.tilResUrl.setError(null);
            if (title.isEmpty()) { d.tilResTitle.setError("Enter a title"); return; }
            if (url.isEmpty()) { d.tilResUrl.setError("Paste the link"); return; }
            if (!url.matches("(?i)^[a-z][a-z0-9+.-]*://.*")) url = "https://" + url;
            if (!url.toLowerCase().startsWith("http://") && !url.toLowerCase().startsWith("https://")) {
                d.tilResUrl.setError("Only web links (http or https)");
                return;
            }
            if (!url.contains(".")) { d.tilResUrl.setError("That does not look like a link"); return; }

            Map<String, Object> doc = new HashMap<>();
            doc.put("title", title);
            doc.put("url", url);
            doc.put("note", note);
            doc.put("author", UserSession.name(this));
            doc.put("createdBy", FirebaseAuth.getInstance().getUid());
            doc.put("createdAt", FieldValue.serverTimestamp());
            collection().add(doc).addOnFailureListener(e ->
                    Toast.makeText(this, "Could not share: " + e.getMessage(), Toast.LENGTH_LONG).show());
            dialog.dismiss();
        });
    }

    private static String text(com.google.android.material.textfield.TextInputEditText e) {
        return e.getText() == null ? "" : e.getText().toString().trim();
    }
}
