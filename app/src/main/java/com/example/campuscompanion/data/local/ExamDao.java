package com.example.campuscompanion.data.local;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Update;

import java.util.List;

@Dao
public interface ExamDao {
    @Insert
    long insert(Exam e);

    @Update
    void update(Exam e);

    @Delete
    void delete(Exam e);

    @Query("SELECT * FROM exams ORDER BY examDate ASC")
    LiveData<List<Exam>> getAll();

    @Query("SELECT * FROM exams ORDER BY examDate ASC")
    List<Exam> getAllNow();

    @Query("DELETE FROM exams")
    void deleteAll();
}
