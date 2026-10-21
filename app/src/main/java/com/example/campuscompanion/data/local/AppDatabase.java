package com.example.campuscompanion.data.local;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;

import com.google.firebase.auth.FirebaseAuth;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Database(entities = {Deadline.class, RoutineEntry.class}, version = 1, exportSchema = false)
public abstract class AppDatabase extends RoomDatabase {

    public abstract DeadlineDao deadlineDao();
    public abstract RoutineDao routineDao();

    // Room forbids writes on the main thread, so all writes go through this executor.
    public static final ExecutorService executor = Executors.newSingleThreadExecutor();

    private static volatile AppDatabase INSTANCE;
    private static volatile String INSTANCE_UID;

    /** Returns the database that belongs to the currently signed-in account. */
    public static AppDatabase get(Context context) {
        Context app = context.getApplicationContext();
        String uid = currentUid(app);

        if (INSTANCE == null || !uid.equals(INSTANCE_UID)) {
            synchronized (AppDatabase.class) {
                if (INSTANCE == null || !uid.equals(INSTANCE_UID)) {
                    INSTANCE = Room.databaseBuilder(
                            app,
                            AppDatabase.class,
                            "campus_companion_" + uid + ".db").build();
                    INSTANCE_UID = uid;
                }
            }
        }
        return INSTANCE;
    }

    /**
     * The signed-in user's id. The last id is remembered, so background work
     * (boot receiver, alarms, logout cleanup) still finds the right database
     * even if Firebase has not loaded the user yet.
     */
    private static String currentUid(Context app) {
        SharedPreferences sp = app.getSharedPreferences("db_owner", Context.MODE_PRIVATE);
        String uid = FirebaseAuth.getInstance().getUid();
        if (uid != null) {
            if (!uid.equals(sp.getString("uid", null))) {
                sp.edit().putString("uid", uid).apply();
            }
            return uid;
        }
        return sp.getString("uid", "guest");
    }
}