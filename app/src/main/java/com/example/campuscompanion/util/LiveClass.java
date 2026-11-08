package com.example.campuscompanion.util;

import android.content.Context;

import androidx.annotation.Nullable;

import com.example.campuscompanion.R;
import com.example.campuscompanion.auth.UserSession;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ServerValue;
import com.google.firebase.database.ValueEventListener;

import java.util.HashMap;
import java.util.Map;

/**
 * Firebase Realtime Database features for the class:
 *  - presence: how many classmates have the app open right now (uses .info/connected + onDisconnect)
 *  - live status: the CR posts "Class cancelled / Room changed / Running late" and every phone updates instantly.
 * Nothing here is stored locally; it is live data only.
 */
public final class LiveClass {
    private LiveClass() { }

    public static final String CANCELLED = "CANCELLED", ROOM = "ROOM", LATE = "LATE", NOTE = "NOTE", CLEAR = "CLEAR";
    private static final long FRESH_MS = 24L * 60 * 60 * 1000;

    public static final class Status {
        public String type = CLEAR, message = "", byName = "";
        public long at;

        public boolean active() {
            return !CLEAR.equals(type) && at > 0 && System.currentTimeMillis() - at < FRESH_MS;
        }
    }

    public interface Count { void onCount(int online); }
    public interface StatusListener { void onStatus(@Nullable Status status); }

    private static FirebaseDatabase db(Context c) {
        String url = c.getString(R.string.rtdb_url).trim();
        if (url.isEmpty()) return null;
        return FirebaseDatabase.getInstance(url);
    }

    /** True when the Realtime Database URL has been filled in and the user has a class. */
    public static boolean available(Context c) {
        return !c.getString(R.string.rtdb_url).trim().isEmpty()
                && FirebaseAuth.getInstance().getUid() != null && UserSession.section(c) != null;
    }

    /**
     * Marks this phone online in the class and reports how many classmates are online.
     * Returns a Runnable that marks it offline again and stops listening.
     */
    public static Runnable watchPresence(Context c, Count cb) {
        try {
            FirebaseDatabase db = db(c);
            String uid = FirebaseAuth.getInstance().getUid();
            String group = UserSession.section(c);
            if (db == null || uid == null || group == null) return () -> { };

            DatabaseReference me = db.getReference("presence").child(group).child(uid);
            DatabaseReference all = db.getReference("presence").child(group);
            DatabaseReference connected = db.getReference(".info/connected");

            ValueEventListener conn = new ValueEventListener() {
                @Override public void onDataChange(DataSnapshot s) {
                    if (Boolean.TRUE.equals(s.getValue(Boolean.class))) {
                        me.onDisconnect().removeValue();   // the server removes us if the connection drops
                        me.setValue(Boolean.TRUE);
                    }
                }
                @Override public void onCancelled(DatabaseError e) { }
            };
            ValueEventListener count = new ValueEventListener() {
                @Override public void onDataChange(DataSnapshot s) { cb.onCount((int) s.getChildrenCount()); }
                @Override public void onCancelled(DatabaseError e) { }
            };
            connected.addValueEventListener(conn);
            all.addValueEventListener(count);
            return () -> {
                connected.removeEventListener(conn);
                all.removeEventListener(count);
                me.removeValue();
            };
        } catch (Exception e) {
            return () -> { };
        }
    }

    /** Listens to the class's live status. Returns a Runnable that stops listening. */
    public static Runnable watchStatus(Context c, StatusListener cb) {
        try {
            FirebaseDatabase db = db(c);
            String group = UserSession.section(c);
            if (db == null || group == null) return () -> { };
            DatabaseReference ref = db.getReference("status").child(group);
            ValueEventListener l = new ValueEventListener() {
                @Override public void onDataChange(DataSnapshot s) {
                    if (!s.exists()) { cb.onStatus(null); return; }
                    Status st = new Status();
                    st.type = str(s, "type", CLEAR);
                    st.message = str(s, "message", "");
                    st.byName = str(s, "byName", "");
                    Long at = s.child("at").getValue(Long.class);
                    st.at = at == null ? 0 : at;
                    cb.onStatus(st);
                }
                @Override public void onCancelled(DatabaseError e) { }
            };
            ref.addValueEventListener(l);
            return () -> ref.removeEventListener(l);
        } catch (Exception e) {
            return () -> { };
        }
    }

    private static String str(DataSnapshot s, String key, String def) {
        String v = s.child(key).getValue(String.class);
        return v == null ? def : v;
    }

    /** CR only (checked in the UI): publish a live update to the whole class. */
    public static void post(Context c, String type, String message, Runnable ok, Runnable fail) {
        try {
            FirebaseDatabase db = db(c);
            String uid = FirebaseAuth.getInstance().getUid();
            String group = UserSession.section(c);
            if (db == null || uid == null || group == null) { fail.run(); return; }
            Map<String, Object> m = new HashMap<>();
            m.put("type", type);
            m.put("message", message.length() > 140 ? message.substring(0, 140) : message);
            m.put("by", uid);
            m.put("byName", UserSession.name(c));
            m.put("at", ServerValue.TIMESTAMP);
            db.getReference("status").child(group).setValue(m)
                    .addOnSuccessListener(x -> ok.run())
                    .addOnFailureListener(e -> fail.run());
        } catch (Exception e) {
            fail.run();
        }
    }

    public static String icon(String type) {
        switch (type) {
            case CANCELLED: return "❌";
            case ROOM: return "🚪";
            case LATE: return "⏰";
            default: return "📢";
        }
    }

    public static String label(String type) {
        switch (type) {
            case CANCELLED: return "Class cancelled";
            case ROOM: return "Room changed";
            case LATE: return "Running late";
            default: return "Announcement";
        }
    }

    public static String ago(long at) {
        long m = Math.max(0, (System.currentTimeMillis() - at) / 60000);
        if (m < 1) return "just now";
        if (m < 60) return m + " min ago";
        return (m / 60) + " h ago";
    }
}
