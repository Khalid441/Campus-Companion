package com.example.campuscompanion.data.local;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

/** One topic of one course: planned, taught in class, and revised 0 to 3 times. */
@Entity(tableName = "course_topics")
public class Topic {
    @PrimaryKey(autoGenerate = true)
    public int id;

    public int courseId;
    public String title;
    public boolean taught;        // false = only planned (typed in by the student)
    public long taughtAt;         // start of the day it was taught, epoch millis
    public int revisedCount;      // 0..3
    public long nextRevisionAt;   // start of the day it is due, 0 = nothing scheduled
    public boolean remind;        // spaced revision switched on

    public Topic copy() {
        Topic t = new Topic();
        t.id = id;
        t.courseId = courseId;
        t.title = title;
        t.taught = taught;
        t.taughtAt = taughtAt;
        t.revisedCount = revisedCount;
        t.nextRevisionAt = nextRevisionAt;
        t.remind = remind;
        return t;
    }
}
