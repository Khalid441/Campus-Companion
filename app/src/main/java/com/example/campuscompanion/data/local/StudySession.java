package com.example.campuscompanion.data.local;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

/** One personal study session (separate from university classes in the "routine" table). */
@Entity(tableName = "study_sessions")
public class StudySession {
    @PrimaryKey(autoGenerate = true)
    public int id;

    public String subject;       // e.g. "CSE 2209"
    public String topic;         // optional note
    public int daysMask;         // bit 0 = Sunday ... bit 6 = Saturday
    public int startMinutes;     // minutes from midnight
    public int endMinutes;
    public int reminderMinutes;  // -1 = no reminder, 0 = at start, else minutes before
    public boolean enabled = true;

    public StudySession copy() {
        StudySession s = new StudySession();
        s.id = id;
        s.subject = subject;
        s.topic = topic;
        s.daysMask = daysMask;
        s.startMinutes = startMinutes;
        s.endMinutes = endMinutes;
        s.reminderMinutes = reminderMinutes;
        s.enabled = enabled;
        return s;
    }
}
