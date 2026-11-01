package com.example.campuscompanion.data.local;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

/** A course the student tracks attendance for. Everything here is typed in by the student. */
@Entity(tableName = "attendance_courses")
public class AttendanceCourse {
    @PrimaryKey(autoGenerate = true)
    public int id;

    public String name;        // e.g. "Algorithm Analysis and Design"
    public String code;        // optional, e.g. "CSE 2201"
    public int minPercent;     // the student's own minimum attendance, 1..100
    public int totalPlanned;   // classes expected this term; 0 = not set
    public long createdAt;     // epoch millis
    public long examDate;      // start of the exam day, epoch millis; 0 = not set

    public String title() {
        boolean hasCode = code != null && !code.trim().isEmpty();
        return hasCode ? code.trim() + "  " + name : name;
    }
}
