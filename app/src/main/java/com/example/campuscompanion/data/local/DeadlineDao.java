package com.example.campuscompanion.data.local;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Update;

import java.util.List;

@Dao
public interface DeadlineDao {
    @Insert
    long insert(Deadline d);

    @Update
    void update(Deadline d);

    @Delete
    void delete(Deadline d);

    @Query("UPDATE deadlines SET done = :done WHERE id = :id")
    void setDone(int id, boolean done);

    @Query("SELECT * FROM deadlines ORDER BY done ASC, dueAt ASC")
    LiveData<List<Deadline>> getAll();

    @Query("SELECT * FROM deadlines WHERE id = :id")
    Deadline getById(int id);

    // Used by BootReceiver to re-schedule alarms (plain list, background thread only)
    @Query("SELECT * FROM deadlines WHERE done = 0")
    List<Deadline> getPending();

    // Used by the foreground service: the next not-finished deadline
    @Query("SELECT * FROM deadlines WHERE done = 0 ORDER BY dueAt ASC LIMIT 1")
    LiveData<Deadline> getNextPending();
}
