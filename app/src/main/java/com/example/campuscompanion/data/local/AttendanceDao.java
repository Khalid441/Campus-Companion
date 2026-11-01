package com.example.campuscompanion.data.local;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Update;

import java.util.List;

@Dao
public interface AttendanceDao {
    // ---- courses ----
    @Insert
    long insertCourse(AttendanceCourse c);

    @Update
    void updateCourse(AttendanceCourse c);

    @Delete
    void deleteCourse(AttendanceCourse c);

    @Query("SELECT * FROM attendance_courses ORDER BY name COLLATE NOCASE ASC")
    LiveData<List<AttendanceCourse>> getCourses();

    // ---- records ----
    @Insert
    long insertRecord(AttendanceRecord r);

    @Update
    void updateRecord(AttendanceRecord r);

    @Delete
    void deleteRecord(AttendanceRecord r);

    // ---- background-thread helpers (alerts, backup) ----
    @Query("SELECT * FROM attendance_courses")
    List<AttendanceCourse> getCoursesNow();

    @Query("SELECT * FROM attendance_courses WHERE id = :id")
    AttendanceCourse courseNow(int id);

    @Query("SELECT * FROM attendance_records")
    List<AttendanceRecord> getRecordsNow();

    @Query("SELECT * FROM attendance_records WHERE courseId = :courseId")
    List<AttendanceRecord> recordsOfNow(int courseId);

    @Query("DELETE FROM attendance_records")
    void deleteAllRecords();

    @Query("DELETE FROM attendance_courses")
    void deleteAllCourses();

    @Query("UPDATE attendance_records SET topic = :topic WHERE id = :id")
    void setTopic(int id, String topic);

    @Query("DELETE FROM attendance_records WHERE courseId = :courseId")
    void deleteRecordsOf(int courseId);

    @Query("SELECT * FROM attendance_records ORDER BY date DESC, id DESC")
    LiveData<List<AttendanceRecord>> getAllRecords();
}
