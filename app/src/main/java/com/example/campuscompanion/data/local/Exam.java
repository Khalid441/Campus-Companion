package com.example.campuscompanion.data.local;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

/** An exam the student added by hand (not linked to the attendance courses). */
@Entity(tableName = "exams")
public class Exam {
    @PrimaryKey(autoGenerate = true)
    public int id;

    public String subject;   // e.g. "CSE 2209 Theory of Computation"
    public long examDate;    // start of the exam day, epoch millis
    public String note;      // optional, e.g. "Room B-105, 10 AM"
}
