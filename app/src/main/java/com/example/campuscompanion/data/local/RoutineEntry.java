package com.example.campuscompanion.data.local;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "routine")
public class RoutineEntry {
    @PrimaryKey(autoGenerate = true)
    public int id;

    public String course;
    public String room;
    public String teacher;
    public int dayOfWeek;       // Calendar.SUNDAY..SATURDAY (1..7)
    public int startMinutes;    // minutes from midnight
    public int endMinutes;
}
