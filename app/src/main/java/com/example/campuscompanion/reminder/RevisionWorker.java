package com.example.campuscompanion.reminder;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.example.campuscompanion.data.local.AppDatabase;
import com.example.campuscompanion.data.local.Topic;
import com.example.campuscompanion.notification.NotificationHelper;
import com.example.campuscompanion.util.DateUtil;

import java.util.List;

/** Runs once a day and posts the "topics to revise" notification when something is due. */
public class RevisionWorker extends Worker {

    public RevisionWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
    }

    @NonNull
    @Override
    public Result doWork() {
        Context c = getApplicationContext();
        if (!RevisionScheduler.isEnabled(c)) return Result.success();

        List<Topic> due = AppDatabase.get(c).topicDao()
                .getDueNow(DateUtil.endOfDay(System.currentTimeMillis()));
        if (!due.isEmpty()) {
            NotificationHelper.showRevisionReminder(c, due.size(), due.get(0).title);
        }
        return Result.success();
    }
}
