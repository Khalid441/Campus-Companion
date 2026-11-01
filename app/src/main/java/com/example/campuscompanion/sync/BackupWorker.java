package com.example.campuscompanion.sync;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

/** Uploads the automatic cloud backup in the background (only when it is switched on). */
public class BackupWorker extends Worker {

    public BackupWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
    }

    @NonNull
    @Override
    public Result doWork() {
        Context c = getApplicationContext();
        if (!BackupManager.isEnabled(c)) return Result.success();
        return BackupManager.backupBlocking(c) ? Result.success() : Result.retry();
    }
}
