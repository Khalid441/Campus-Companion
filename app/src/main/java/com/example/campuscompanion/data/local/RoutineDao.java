package com.example.campuscompanion.data.local;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Update;

import java.util.List;

@Dao
public interface RoutineDao {
    @Insert
    long insert(RoutineEntry e);

    @Update
    void update(RoutineEntry e);

    @Delete
    void delete(RoutineEntry e);

    @Query("SELECT * FROM routine ORDER BY dayOfWeek ASC, startMinutes ASC")
    LiveData<List<RoutineEntry>> getAll();

    @Query("SELECT * FROM routine WHERE dayOfWeek = :day ORDER BY startMinutes ASC")
    LiveData<List<RoutineEntry>> getForDay(int day);
}
