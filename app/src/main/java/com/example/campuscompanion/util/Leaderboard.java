package com.example.campuscompanion.util;

import android.content.Context;

import com.example.campuscompanion.auth.UserSession;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.CollectionReference;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.HashMap;
import java.util.Map;

/**
 * Optional anonymous class leaderboard. Only an alias (like "Brave Falcon"), the level and the XP
 * are stored. It is off until the student switches it on.
 */
public final class Leaderboard {
    private Leaderboard() { }

    public static CollectionReference collection(Context c) {
        String section = UserSession.section(c);
        if (section == null || section.isEmpty()) return null;
        return FirebaseFirestore.getInstance().collection("sections").document(section).collection("leaderboard");
    }

    /** Uploads the student's XP when they are opted in and it changed. */
    public static void uploadIfOn(Context c, Gamification.Snapshot s) {
        if (!Gamification.leaderboardOn(c)) return;
        String uid = FirebaseAuth.getInstance().getUid();
        CollectionReference col = collection(c);
        if (uid == null || col == null || Gamification.lastUploadedXp(c) == s.xp) return;

        Map<String, Object> doc = new HashMap<>();
        doc.put("xp", s.xp);
        doc.put("level", s.level);
        doc.put("alias", LevelMath.alias(uid));
        doc.put("updatedAt", FieldValue.serverTimestamp());
        final Context app = c.getApplicationContext();
        final int xp = s.xp;
        col.document(uid).set(doc).addOnSuccessListener(v -> Gamification.setLastUploadedXp(app, xp));
    }

    /** Takes the student off the board. */
    public static void remove(Context c) {
        String uid = FirebaseAuth.getInstance().getUid();
        CollectionReference col = collection(c);
        if (uid == null || col == null) return;
        col.document(uid).delete();
        Gamification.setLastUploadedXp(c, -1);
    }
}
