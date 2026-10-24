package com.example.campuscompanion.ui;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.example.campuscompanion.adapter.NoticeAdapter;
import com.example.campuscompanion.auth.UserSession;
import com.example.campuscompanion.databinding.ActivityNoticesBinding;
import com.example.campuscompanion.model.Notice;
import com.example.campuscompanion.sync.NoticeManager;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.Query;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Notices of the section. Everyone reads them; only the class representative can post/delete. */
public class NoticesActivity extends AppCompatActivity implements NoticeAdapter.Listener {

    private ActivityNoticesBinding b;
    private NoticeAdapter adapter;
    private ListenerRegistration registration;
    private String section;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        b = ActivityNoticesBinding.inflate(getLayoutInflater());
        setContentView(b.getRoot());

        section = UserSession.section(this);

        adapter = new NoticeAdapter(this);
        b.recycler.setLayoutManager(new LinearLayoutManager(this));
        b.recycler.setAdapter(adapter);

        b.fabPost.setVisibility(UserSession.isCr(this) ? View.VISIBLE : View.GONE);
        b.fabPost.setOnClickListener(v -> showPostDialog());

        b.btnClassCode.setVisibility(UserSession.isCr(this) ? View.VISIBLE : View.GONE);
        b.btnClassCode.setOnClickListener(v -> showClassCodeDialog());
    }

    // ---- class code (join code for universities without student emails) ----

    private static final String CODE_CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";

    private static String newCode() {
        java.security.SecureRandom r = new java.security.SecureRandom();
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 6; i++) sb.append(CODE_CHARS.charAt(r.nextInt(CODE_CHARS.length())));
        return sb.toString();
    }

    /** CR: shows the class code with Share and New code buttons. */
    private void showClassCodeDialog() {
        if (section == null) return;
        FirebaseFirestore.getInstance().collection("sections").document(section).get()
                .addOnSuccessListener(doc -> {
                    String code = doc.getString("joinCode");
                    if (code == null || code.isEmpty()) {
                        new MaterialAlertDialogBuilder(this)
                                .setTitle("Class code")
                                .setMessage("This class does not use a class code. Students join with their university email.")
                                .setPositiveButton("OK", null)
                                .show();
                        return;
                    }
                    new MaterialAlertDialogBuilder(this)
                            .setTitle("Class code")
                            .setMessage(code + "\n\nShare it only with your classmates.")
                            .setPositiveButton("Share", (d, w) -> shareCode(code))
                            .setNeutralButton("New code", (d, w) -> resetCode())
                            .setNegativeButton("Close", null)
                            .show();
                })
                .addOnFailureListener(e -> Toast.makeText(this,
                        "Could not load code: " + e.getMessage(), Toast.LENGTH_LONG).show());
    }

    private void shareCode(String code) {
        String uni = UserSession.university(this);
        String sec = UserSession.sectionName(this);
        String text = "Join Campus Companion.\nUniversity: " + uni
                + "\nSection: " + (sec == null ? "" : sec.replace('_', ' '))
                + "\nClass code: " + code;
        Intent send = new Intent(Intent.ACTION_SEND);
        send.setType("text/plain");
        send.putExtra(Intent.EXTRA_TEXT, text);
        startActivity(Intent.createChooser(send, "Share class code"));
    }

    /** Old code stops working for new sign-ups; people already in the class stay in. */
    private void resetCode() {
        String code = newCode();
        Map<String, Object> data = new HashMap<>();
        data.put("joinCode", code);
        FirebaseFirestore.getInstance().collection("sections").document(section)
                .set(data, com.google.firebase.firestore.SetOptions.merge())
                .addOnSuccessListener(v -> shareCode(code))
                .addOnFailureListener(e -> Toast.makeText(this,
                        "Could not change code: " + e.getMessage(), Toast.LENGTH_LONG).show());
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (section == null) return;
        registration = FirebaseFirestore.getInstance()
                .collection("sections").document(section).collection("notices")
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .limit(50)
                .addSnapshotListener((snap, error) -> {
                    if (error != null || snap == null) return;
                    List<Notice> list = new ArrayList<>();
                    for (DocumentSnapshot d : snap.getDocuments()) list.add(Notice.from(d));
                    adapter.submitList(list);
                    b.emptyText.setVisibility(list.isEmpty() ? View.VISIBLE : View.GONE);
                    if (!list.isEmpty()) NoticeManager.markSeen(this, list.get(0).createdAt);
                });
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (registration != null) registration.remove();
        registration = null;
    }

    private void showPostDialog() {
        EditText input = new EditText(this);
        input.setHint("Write a notice for your section");
        input.setMinLines(3);
        input.setGravity(Gravity.TOP | Gravity.START);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE
                | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);

        FrameLayout box = new FrameLayout(this);
        int pad = (int) (20 * getResources().getDisplayMetrics().density);
        box.setPadding(pad, pad / 2, pad, 0);
        box.addView(input);

        new MaterialAlertDialogBuilder(this)
                .setTitle("Post notice to section")
                .setView(box)
                .setPositiveButton("Post", (d, w) -> post(input.getText().toString().trim()))
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void post(String message) {
        if (message.isEmpty()) {
            Toast.makeText(this, "Notice is empty", Toast.LENGTH_SHORT).show();
            return;
        }
        Map<String, Object> doc = new HashMap<>();
        doc.put("message", message);
        doc.put("author", UserSession.name(this));
        doc.put("createdBy", FirebaseAuth.getInstance().getUid());
        doc.put("createdAt", FieldValue.serverTimestamp());

        Context app = getApplicationContext();
        FirebaseFirestore.getInstance()
                .collection("sections").document(section).collection("notices")
                .add(doc)
                .addOnFailureListener(e -> Toast.makeText(app,
                        "Post failed: " + e.getMessage(), Toast.LENGTH_LONG).show());
    }

    @Override
    public void onLongPress(Notice n) {
        if (!UserSession.isCr(this) || n.id == null) return;
        Context app = getApplicationContext();
        new MaterialAlertDialogBuilder(this)
                .setTitle("Delete notice for everyone?")
                .setMessage(n.message)
                .setPositiveButton("Delete", (d, w) ->
                        FirebaseFirestore.getInstance()
                                .collection("sections").document(section)
                                .collection("notices").document(n.id).delete()
                                .addOnFailureListener(e -> Toast.makeText(app,
                                        "Could not delete: " + e.getMessage(), Toast.LENGTH_LONG).show()))
                .setNegativeButton("Cancel", null)
                .show();
    }
}
