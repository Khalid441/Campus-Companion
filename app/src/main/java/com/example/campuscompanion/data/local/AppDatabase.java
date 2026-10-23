package com.example.campuscompanion.data.local;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.NonNull;
import androidx.room.Database;
import androidx.room.InvalidationTracker;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import androidx.room.migration.Migration;
import androidx.sqlite.db.SupportSQLiteDatabase;

import com.example.campuscompanion.widget.NextDeadlineWidget;
import com.google.firebase.auth.FirebaseAuth;

import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Database(entities = {Deadline.class, RoutineEntry.class, StudySession.class},
        version = 2, exportSchema = false)
public abstract class AppDatabase extends RoomDatabase {

    public abstract DeadlineDao deadlineDao();
    public abstract RoutineDao routineDao();
    public abstract StudyDao studyDao();

    // Room forbids writes on the main thread, so all writes go through this executor.
    public static final ExecutorService executor = Executors.newSingleThreadExecutor();

    /** Version 1 -> 2: adds the personal study sessions table. Existing data is kept. */
    static final Migration MIGRATION_1_2 = new Migration(1, 2) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase db) {
            db.execSQL("CREATE TABLE IF NOT EXISTS `study_sessions` ("
                    + "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, "
                    + "`subject` TEXT, "
                    + "`topic` TEXT, "
                    + "`daysMask` INTEGER NOT NULL, "
                    + "`startMinutes` INTEGER NOT NULL, "
                    + "`endMinutes` INTEGER NOT NULL, "
                    + "`reminderMinutes` INTEGER NOT NULL, "
                    + "`enabled` INTEGER NOT NULL)");
        }
    };

    private static volatile AppDatabase INSTANCE;
    private static volatile String INSTANCE_UID;

    /** Returns the database that belongs to the currently signed-in account. */
    public static AppDatabase get(Context context) {
        final Context app = context.getApplicationContext();
        String uid = currentUid(app);

        if (INSTANCE == null || !uid.equals(INSTANCE_UID)) {
            synchronized (AppDatabase.class) {
                if (INSTANCE == null || !uid.equals(INSTANCE_UID)) {
                    AppDatabase db = Room.databaseBuilder(
                                    app,
                                    AppDatabase.class,
                                    "campus_companion_" + uid + ".db")
                            .addMigrations(MIGRATION_1_2)
                            .build();

                    // Redraw the home screen widget whenever deadlines or the routine change
                    db.getInvalidationTracker().addObserver(
                            new InvalidationTracker.Observer("deadlines", "routine") {
                                @Override
                                public void onInvalidated(@NonNull Set<String> tables) {
                                    NextDeadlineWidget.refresh(app);
                                }
                            });

                    INSTANCE = db;
                    INSTANCE_UID = uid;
                }
            }
        }
        return INSTANCE;
    }

    /**
     * The signed-in user's id. The last id is remembered, so background work
     * (boot receiver, alarms, widget, logout cleanup) still finds the right database
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
