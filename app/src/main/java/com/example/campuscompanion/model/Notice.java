package com.example.campuscompanion.model;

import com.google.firebase.Timestamp;
import com.google.firebase.firestore.DocumentSnapshot;

/** A notice posted by the class representative for the whole section. */
public class Notice {
    public String id;
    public String message;
    public String author;
    public String authorUid;
    public long createdAt;   // epoch millis

    public static Notice from(DocumentSnapshot d) {
        Notice n = new Notice();
        n.id = d.getId();
        n.message = d.getString("message");
        n.author = d.getString("author");
        n.authorUid = d.getString("createdBy");
        // A just-posted notice has no server time yet: use the local estimate
        Timestamp ts = d.getTimestamp("createdAt", DocumentSnapshot.ServerTimestampBehavior.ESTIMATE);
        n.createdAt = ts == null ? System.currentTimeMillis() : ts.toDate().getTime();
        return n;
    }
}
