package com.example.campuscompanion.data.local;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "deadlines")
public class Deadline {
    public static final String HIGH = "HIGH";
    public static final String MEDIUM = "MEDIUM";
    public static final String LOW = "LOW";

    @PrimaryKey(autoGenerate = true)
    public int id;

    public String title;
    public String course;
    public String note;
    public String priority = MEDIUM;
    public long dueAt;                 // epoch millis
    public int remindBeforeMinutes;    // -1 = no reminder
    public boolean done;

    // For the Firebase "shared by CR" feature (later steps)
    public boolean isShared;
    public String remoteId;
}
