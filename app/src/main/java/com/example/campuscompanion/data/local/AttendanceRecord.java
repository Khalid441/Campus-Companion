package com.example.campuscompanion.data.local;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

/** One class of one course: attended, missed, or cancelled. One lab (3 periods) is one record. */
@Entity(tableName = "attendance_records")
public class AttendanceRecord {
    public static final int PRESENT = 0;
    public static final int ABSENT = 1;
    public static final int CANCELLED = 2;

    @PrimaryKey(autoGenerate = true)
    public int id;

    public int courseId;
    public long date;     // start of that day (local time), epoch millis
    public int status;    // PRESENT, ABSENT or CANCELLED
    public String topic;  // what was taught that day (optional)
}
