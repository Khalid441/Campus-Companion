package com.example.campuscompanion.sync;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.example.campuscompanion.auth.UserSession;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QuerySnapshot;

import java.util.concurrent.TimeUnit;

/** WorkManager job: pulls the section's deadlines once, even if the app is closed. */
public class SyncWorker extends Worker {

    public SyncWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
    }

    @NonNull
    @Override
    public Result doWork() {
        Context app = getApplicationContext();
        String section = UserSession.section(app);
        if (section == null || FirebaseAuth.getInstance().getCurrentUser() == null) {
            return Result.success();
        }
        try {
            QuerySnapshot snap = Tasks.await(
                    FirebaseFirestore.getInstance()
                            .collection("sections").document(section).collection("deadlines").get(),
                    30, TimeUnit.SECONDS);
            if (snap.isEmpty() && snap.getMetadata().isFromCache()) return Result.success();
            SyncManager.reconcileBlocking(app, snap.getDocuments());
            return Result.success();
        } catch (Exception e) {
            return Result.retry();
        }
    }
}
