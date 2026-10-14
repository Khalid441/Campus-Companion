package com.example.campuscompanion.data.local;

import android.content.Context;

import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Database(entities = {Deadline.class, RoutineEntry.class}, version = 1, exportSchema = false)
public abstract class AppDatabase extends RoomDatabase {

    public abstract DeadlineDao deadlineDao();
    public abstract RoutineDao routineDao();

    // Room forbids writes on the main thread, so all writes go through this executor.
    public static final ExecutorService executor = Executors.newSingleThreadExecutor();

    private static volatile AppDatabase INSTANCE;

    public static AppDatabase get(Context context) {
        if (INSTANCE == null) {
            synchronized (AppDatabase.class) {
                if (INSTANCE == null) {
                    INSTANCE = Room.databaseBuilder(
                            context.getApplicationContext(),
                            AppDatabase.class,
                            "campus_companion.db").build();
                }
            }
        }
        return INSTANCE;
    }
}
