package com.example.campuscompanion.data.local;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Update;

import java.util.List;

@Dao
public interface TopicDao {
    @Insert
    long insert(Topic t);

    @Update
    void update(Topic t);

    @Delete
    void delete(Topic t);

    @Query("DELETE FROM course_topics WHERE courseId = :courseId")
    void deleteOf(int courseId);

    @Query("SELECT * FROM course_topics ORDER BY taughtAt DESC, id DESC")
    LiveData<List<Topic>> getAll();

    @Query("SELECT * FROM course_topics WHERE courseId = :courseId ORDER BY taught ASC, taughtAt DESC, id DESC")
    LiveData<List<Topic>> getForCourse(int courseId);

    /** Same topic typed twice for one course is merged (background thread only). */
    @Query("SELECT * FROM course_topics WHERE courseId = :courseId AND title = :title COLLATE NOCASE LIMIT 1")
    Topic findByTitle(int courseId, String title);

    /** Topics whose revision day has come (background thread only). */
    @Query("SELECT * FROM course_topics WHERE remind = 1 AND taught = 1 AND revisedCount < 3 "
            + "AND nextRevisionAt > 0 AND nextRevisionAt <= :endOfToday ORDER BY nextRevisionAt ASC")
    List<Topic> getDueNow(long endOfToday);
}
