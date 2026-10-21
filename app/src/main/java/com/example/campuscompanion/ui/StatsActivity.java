package com.example.campuscompanion.ui;

import android.os.Bundle;
import android.view.View;

import androidx.appcompat.app.AppCompatActivity;

import com.example.campuscompanion.data.local.AppDatabase;
import com.example.campuscompanion.data.local.Deadline;
import com.example.campuscompanion.databinding.ActivityStatsBinding;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

/** Statistics: completion donut, deadlines due in the next 7 days, priority breakdown. */
public class StatsActivity extends AppCompatActivity {

    private static final int GREEN = 0xFF43A047;
    private static final int ORANGE = 0xFFFB8C00;
    private static final int RED = 0xFFE53935;

    private ActivityStatsBinding b;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        b = ActivityStatsBinding.inflate(getLayoutInflater());
        setContentView(b.getRoot());

        AppDatabase.get(this).deadlineDao().getAll().observe(this, this::render);
    }

    private void render(List<Deadline> list) {
        long now = System.currentTimeMillis();

        Calendar todayStart = Calendar.getInstance();
        todayStart.set(Calendar.HOUR_OF_DAY, 0);
        todayStart.set(Calendar.MINUTE, 0);
        todayStart.set(Calendar.SECOND, 0);
        todayStart.set(Calendar.MILLISECOND, 0);
        long dayMs = 24L * 60 * 60 * 1000;

        int done = 0, pending = 0, overdue = 0;
        int high = 0, medium = 0, low = 0;
        int[] perDay = new int[7];

        for (Deadline d : list) {
            if (d.done) done++;
            else if (d.dueAt < now) overdue++;
            else pending++;

            if (Deadline.HIGH.equals(d.priority)) high++;
            else if (Deadline.LOW.equals(d.priority)) low++;
            else medium++;

            if (!d.done) {
                long diff = d.dueAt - todayStart.getTimeInMillis();
                if (diff >= 0) {
                    int idx = (int) (diff / dayMs);
                    if (idx < 7) perDay[idx]++;
                }
            }
        }

        int total = list.size();
        int rate = total == 0 ? 0 : Math.round(done * 100f / total);

        b.donut.setData(new float[]{done, pending, overdue},
                new int[]{GREEN, ORANGE, RED}, rate + "%");

        b.tvDone.setText("\u25CF Done  " + done);
        b.tvDone.setTextColor(GREEN);
        b.tvPending.setText("\u25CF Pending  " + pending);
        b.tvPending.setTextColor(ORANGE);
        b.tvOverdue.setText("\u25CF Overdue  " + overdue);
        b.tvOverdue.setTextColor(RED);

        b.tvTotal.setText(total == 0
                ? "No deadlines yet. Add some to see your stats."
                : total + " deadlines in total, " + rate + "% completed");

        String[] labels = new String[7];
        SimpleDateFormat fmt = new SimpleDateFormat("EEE", Locale.getDefault());
        for (int i = 0; i < 7; i++) {
            Calendar c = (Calendar) todayStart.clone();
            c.add(Calendar.DAY_OF_MONTH, i);
            labels[i] = i == 0 ? "Today" : fmt.format(c.getTime());
        }
        b.bars.setData(labels, perDay);

        b.tvPriority.setText("High  " + high + "     Medium  " + medium + "     Low  " + low);
        b.emptyHint.setVisibility(total == 0 ? View.VISIBLE : View.GONE);
    }
}
