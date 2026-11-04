package com.example.campuscompanion.ui;

import android.os.Bundle;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.campuscompanion.R;
import com.example.campuscompanion.data.local.AppDatabase;
import com.example.campuscompanion.databinding.ActivityAchievementsBinding;
import com.example.campuscompanion.databinding.ItemBadgeBinding;
import com.example.campuscompanion.util.Gamification;
import com.example.campuscompanion.util.LevelMath;
import com.example.campuscompanion.util.Leaderboard;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.CollectionReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.Query;

import java.util.List;

/** Level, XP, badges and the optional anonymous class leaderboard. */
public class AchievementsActivity extends AppCompatActivity {

    private ActivityAchievementsBinding b;
    private ListenerRegistration registration;
    private Gamification.Snapshot last;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        b = ActivityAchievementsBinding.inflate(getLayoutInflater());
        setContentView(b.getRoot());

        b.swLeaderboard.setChecked(Gamification.leaderboardOn(this));
        b.swLeaderboard.setOnCheckedChangeListener((button, on) -> {
            Gamification.setLeaderboard(this, on);
            if (on) {
                Gamification.setLastUploadedXp(this, -1);   // force an upload
                if (last != null) Leaderboard.uploadIfOn(this, last);
            } else {
                Leaderboard.remove(this);
            }
            listenToBoard();
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        AppDatabase.executor.execute(() -> {
            Gamification.Snapshot s = Gamification.compute(getApplicationContext());
            Leaderboard.uploadIfOn(getApplicationContext(), s);
            runOnUiThread(() -> {
                if (isFinishing() || isDestroyed()) return;
                last = s;
                show(s);
                for (Gamification.Badge nb : s.newlyUnlocked) {
                    Toast.makeText(this, nb.emoji + " New badge: " + nb.name, Toast.LENGTH_LONG).show();
                }
            });
        });
        listenToBoard();
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (registration != null) registration.remove();
        registration = null;
    }

    private void show(Gamification.Snapshot s) {
        b.tvLevel.setText("⭐ Level " + s.level + "  •  " + LevelMath.title(s.level));
        b.tvXp.setText(s.xp + " XP  •  " + (s.xpNextLevel - s.xp) + " to level " + (s.level + 1));
        int span = Math.max(1, s.xpNextLevel - s.xpThisLevel);
        b.levelBar.setProgress(Math.min(100, (s.xp - s.xpThisLevel) * 100 / span));
        b.levelBar.setContentDescription("Level progress " + (s.xp - s.xpThisLevel) * 100 / span + " percent");

        b.tvBadgesTitle.setText("Badges  (" + s.unlocked.size() + " of " + Gamification.BADGES.length + ")");
        b.badgeGrid.removeAllViews();
        LinearLayout row = null;
        for (int i = 0; i < Gamification.BADGES.length; i++) {
            if (i % 3 == 0) {
                row = new LinearLayout(this);
                row.setOrientation(LinearLayout.HORIZONTAL);
                b.badgeGrid.addView(row, new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
            }
            Gamification.Badge badge = Gamification.BADGES[i];
            boolean on = s.unlocked.contains(badge.id);
            ItemBadgeBinding c = ItemBadgeBinding.inflate(getLayoutInflater(), row, false);
            c.tvBadgeEmoji.setText(on ? badge.emoji : "🔒");
            c.tvBadgeName.setText(badge.name);
            c.tvBadgeDesc.setText(badge.description);
            c.badgeCard.setAlpha(on ? 1f : 0.45f);
            c.badgeCard.setContentDescription(badge.name + ", " + (on ? "unlocked" : "locked") + ". " + badge.description);
            row.addView(c.getRoot());
        }
        // Fill the last row so cards keep the same width
        int rest = Gamification.BADGES.length % 3;
        if (rest != 0 && row != null) {
            for (int k = rest; k < 3; k++) {
                View filler = new View(this);
                row.addView(filler, new LinearLayout.LayoutParams(0, 1, 1f));
            }
        }
    }

    private void listenToBoard() {
        if (registration != null) registration.remove();
        registration = null;
        b.lbList.removeAllViews();
        if (!Gamification.leaderboardOn(this)) {
            b.tvLbEmpty.setVisibility(View.VISIBLE);
            b.tvLbEmpty.setText("Switch it on to see how your class is doing.");
            return;
        }
        CollectionReference col = Leaderboard.collection(this);
        if (col == null) return;
        String me = FirebaseAuth.getInstance().getUid();
        registration = col.orderBy("xp", Query.Direction.DESCENDING).limit(20)
                .addSnapshotListener((snap, error) -> {
                    if (error != null || snap == null || isFinishing() || isDestroyed()) return;
                    renderBoard(snap.getDocuments(), me);
                });
    }

    private void renderBoard(List<DocumentSnapshot> docs, String me) {
        b.lbList.removeAllViews();
        b.tvLbEmpty.setVisibility(docs.isEmpty() ? View.VISIBLE : View.GONE);
        b.tvLbEmpty.setText("Nobody is on the board yet. You could be first!");
        int rank = 0;
        for (DocumentSnapshot d : docs) {
            rank++;
            boolean mine = d.getId().equals(me);
            Long xp = d.getLong("xp");
            Long level = d.getLong("level");
            String alias = d.getString("alias");
            String medal = rank == 1 ? "🥇" : rank == 2 ? "🥈" : rank == 3 ? "🥉" : rank + ".";

            TextView t = new TextView(this);
            t.setText(medal + "  " + (mine ? "You (" + alias + ")" : alias)
                    + "   •  Lv " + (level == null ? 1 : level) + "  •  " + (xp == null ? 0 : xp) + " XP");
            t.setTextSize(15);
            if (mine) t.setTypeface(null, android.graphics.Typeface.BOLD);
            int pad = (int) (10 * getResources().getDisplayMetrics().density);
            t.setPadding(pad, pad, pad, pad);
            if (mine) t.setBackgroundColor(0x22FFC107);
            b.lbList.addView(t);
        }
    }
}
