package com.example.campuscompanion.ui;

import android.os.Bundle;
import android.view.View;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.campuscompanion.auth.UserSession;
import com.example.campuscompanion.databinding.ActivityPollsBinding;
import com.example.campuscompanion.databinding.DialogPollBinding;
import com.example.campuscompanion.databinding.ItemPollBinding;
import com.example.campuscompanion.databinding.ItemPollOptionBinding;
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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Class polls. The class representative asks, everyone votes once (and can change or remove the vote
 * while the poll is open). Votes are stored as {uid: option index} on the poll.
 */
public class PollsActivity extends AppCompatActivity {

    private ActivityPollsBinding b;
    private ListenerRegistration registration;
    private String section;
    private String uid;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        b = ActivityPollsBinding.inflate(getLayoutInflater());
        setContentView(b.getRoot());
        section = UserSession.section(this);
        uid = FirebaseAuth.getInstance().getUid();

        boolean cr = UserSession.isCr(this);
        b.fabPoll.setVisibility(cr ? View.VISIBLE : View.GONE);
        b.fabPoll.setOnClickListener(v -> showAddDialog());
        b.tvSub.setText(cr ? "Ask your class a question" : "Vote on questions from your class representative");
    }

    private CollectionReference collection() {
        return FirebaseFirestore.getInstance()
                .collection("sections").document(section).collection("polls");
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (section == null || section.isEmpty()) return;
        registration = collection().orderBy("createdAt", Query.Direction.DESCENDING).limit(30)
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

    @SuppressWarnings("unchecked")
    private void render(List<DocumentSnapshot> docs) {
        b.pollList.removeAllViews();
        b.tvPollEmpty.setVisibility(docs.isEmpty() ? View.VISIBLE : View.GONE);
        boolean cr = UserSession.isCr(this);

        for (DocumentSnapshot d : docs) {
            List<String> options = (List<String>) d.get("options");
            if (options == null || options.size() < 2) continue;
            Map<String, Object> votes = (Map<String, Object>) d.get("votes");
            if (votes == null) votes = new HashMap<>();
            boolean closed = Boolean.TRUE.equals(d.getBoolean("closed"));

            int[] counts = new int[options.size()];
            int total = 0;
            int mine = -1;
            for (Map.Entry<String, Object> v : votes.entrySet()) {
                if (!(v.getValue() instanceof Number)) continue;
                int idx = ((Number) v.getValue()).intValue();
                if (idx < 0 || idx >= counts.length) continue;
                counts[idx]++;
                total++;
                if (v.getKey().equals(uid)) mine = idx;
            }

            ItemPollBinding p = ItemPollBinding.inflate(getLayoutInflater(), b.pollList, false);
            p.tvPollQuestion.setText(d.getString("question"));
            Timestamp ts = d.getTimestamp("createdAt", DocumentSnapshot.ServerTimestampBehavior.ESTIMATE);
            String author = d.getString("author");
            p.tvPollMeta.setText((closed ? "Closed  •  " : "")
                    + total + (total == 1 ? " vote" : " votes")
                    + (author == null || author.isEmpty() ? "" : "  •  " + author)
                    + (ts == null ? "" : "  •  " + TimeUtil.friendly(ts.toDate().getTime())));

            for (int i = 0; i < options.size(); i++) {
                ItemPollOptionBinding o = ItemPollOptionBinding.inflate(getLayoutInflater(), p.pollOptions, false);
                int pct = total == 0 ? 0 : Math.round(100f * counts[i] / total);
                boolean picked = i == mine;
                o.btnOption.setText((picked ? "✓  " : "") + options.get(i)
                        + (total == 0 && mine < 0 ? "" : "   " + pct + "%  (" + counts[i] + ")"));
                o.btnOption.setEnabled(!closed);
                o.optBar.setProgress(pct);
                o.optBar.setContentDescription(options.get(i) + ", " + pct + " percent");
                final int index = i;
                final boolean wasPicked = picked;
                o.btnOption.setOnClickListener(v -> vote(d, index, wasPicked));
                p.pollOptions.addView(o.getRoot());
            }

            if (cr) {
                p.pollCard.setOnLongClickListener(v -> {
                    String[] items = {closed ? "Reopen poll" : "Close poll", "Delete poll"};
                    new MaterialAlertDialogBuilder(this)
                            .setTitle(d.getString("question"))
                            .setItems(items, (dlg, which) -> {
                                if (which == 0) d.getReference().update("closed", !closed);
                                else d.getReference().delete();
                            })
                            .show();
                    return true;
                });
            }
            b.pollList.addView(p.getRoot());
        }
    }

    /** Tap an option to vote; tap your own option again to take the vote back. */
    private void vote(DocumentSnapshot poll, int index, boolean wasPicked) {
        if (uid == null) return;
        Object value = wasPicked ? FieldValue.delete() : (Object) index;
        poll.getReference().update("votes." + uid, value).addOnFailureListener(e ->
                Toast.makeText(this, "Could not save your vote: " + e.getMessage(), Toast.LENGTH_LONG).show());
    }

    private void showAddDialog() {
        DialogPollBinding d = DialogPollBinding.inflate(getLayoutInflater());
        androidx.appcompat.app.AlertDialog dialog = new MaterialAlertDialogBuilder(this)
                .setTitle("New poll")
                .setView(d.getRoot())
                .setPositiveButton("Post", null)
                .setNegativeButton("Cancel", null)
                .create();
        dialog.show();
        dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String q = text(d.etQuestion);
            d.tilQuestion.setError(null);
            d.tilOpt1.setError(null);
            d.tilOpt2.setError(null);
            List<String> opts = new ArrayList<>();
            for (String s : new String[]{text(d.etOpt1), text(d.etOpt2), text(d.etOpt3), text(d.etOpt4)}) {
                if (!s.isEmpty()) opts.add(s);
            }
            if (q.isEmpty()) { d.tilQuestion.setError("Enter the question"); return; }
            if (text(d.etOpt1).isEmpty()) { d.tilOpt1.setError("Enter an option"); return; }
            if (text(d.etOpt2).isEmpty()) { d.tilOpt2.setError("Enter a second option"); return; }

            Map<String, Object> doc = new HashMap<>();
            doc.put("question", q);
            doc.put("options", opts);
            doc.put("votes", new HashMap<String, Object>());
            doc.put("closed", false);
            doc.put("author", UserSession.name(this));
            doc.put("createdBy", uid);
            doc.put("createdAt", FieldValue.serverTimestamp());
            collection().add(doc).addOnFailureListener(e ->
                    Toast.makeText(this, "Could not post: " + e.getMessage(), Toast.LENGTH_LONG).show());
            dialog.dismiss();
        });
    }

    private static String text(com.google.android.material.textfield.TextInputEditText e) {
        return e.getText() == null ? "" : e.getText().toString().trim();
    }
}
