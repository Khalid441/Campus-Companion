package com.example.campuscompanion.data.local;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Update;

import java.util.List;

@Dao
public interface StudyDao {
    @Insert
    long insert(StudySession s);

    @Update
    void update(StudySession s);

    @Delete
    void delete(StudySession s);

    @Query("SELECT * FROM study_sessions ORDER BY startMinutes ASC")
    LiveData<List<StudySession>> getAll();

    @Query("SELECT * FROM study_sessions")
    List<StudySession> getAllNow();

    @Query("SELECT * FROM study_sessions WHERE id = :id")
    StudySession getById(int id);
}
