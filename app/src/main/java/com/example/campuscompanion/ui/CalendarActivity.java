package com.example.campuscompanion.ui;

import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;

import com.example.campuscompanion.data.local.AppDatabase;
import com.example.campuscompanion.data.local.Deadline;
import com.example.campuscompanion.databinding.ActivityCalendarBinding;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/** Month calendar of deadlines: dots on busy days, tap a day to see its deadlines. */
public class CalendarActivity extends AppCompatActivity {

    private ActivityCalendarBinding b;
    private int year;
    private int month;
    private int selectedDay;
    private List<Deadline> all = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        b = ActivityCalendarBinding.inflate(getLayoutInflater());
        setContentView(b.getRoot());

        goToToday();

        b.calendar.setOnDayClickListener(day -> {
            selectedDay = day;
            render();
        });
        b.btnPrev.setOnClickListener(v -> shiftMonth(-1));
        b.btnNext.setOnClickListener(v -> shiftMonth(1));
        b.btnToday.setOnClickListener(v -> {
            goToToday();
            render();
        });

        AppDatabase.get(this).deadlineDao().getAll().observe(this, list -> {
            all = list;
            render();
        });
    }

    private void goToToday() {
        Calendar now = Calendar.getInstance();
        year = now.get(Calendar.YEAR);
        month = now.get(Calendar.MONTH);
        selectedDay = now.get(Calendar.DAY_OF_MONTH);
    }

    private void shiftMonth(int delta) {
        month += delta;
        if (month < 0) {
            month = 11;
            year--;
        } else if (month > 11) {
            month = 0;
            year++;
        }
        selectedDay = 1;
        render();
    }

    private void render() {
        long now = System.currentTimeMillis();
        int[] overdue = new int[32];
        int[] pending = new int[32];
        int[] done = new int[32];

        Calendar c = Calendar.getInstance();
        for (Deadline d : all) {
            c.setTimeInMillis(d.dueAt);
            if (c.get(Calendar.YEAR) != year || c.get(Calendar.MONTH) != month) continue;
            int day = c.get(Calendar.DAY_OF_MONTH);
            if (d.done) done[day]++;
            else if (d.dueAt < now) overdue[day]++;
            else pending[day]++;
        }

        Calendar title = Calendar.getInstance();
        title.clear();
        title.set(year, month, 1);
        b.tvMonth.setText(new SimpleDateFormat("MMMM yyyy", Locale.getDefault()).format(title.getTime()));

        b.calendar.setMonth(year, month);
        b.calendar.setMarks(overdue, pending, done);
        b.calendar.setSelectedDay(selectedDay);

        showDay(now);
    }

    private void showDay(long now) {
        Calendar c = Calendar.getInstance();
        List<Deadline> dayList = new ArrayList<>();
        for (Deadline d : all) {
            c.setTimeInMillis(d.dueAt);
            if (c.get(Calendar.YEAR) == year && c.get(Calendar.MONTH) == month
                    && c.get(Calendar.DAY_OF_MONTH) == selectedDay) {
                dayList.add(d);
            }
        }
        Collections.sort(dayList, (x, y) -> Long.compare(x.dueAt, y.dueAt));

        Calendar sel = Calendar.getInstance();
        sel.clear();
        sel.set(year, month, selectedDay);
        b.tvSelectedDate.setText(new SimpleDateFormat("EEEE, dd MMMM", Locale.getDefault())
                .format(sel.getTime()));

        if (dayList.isEmpty()) {
            b.tvDayList.setText("No deadlines on this day.");
            return;
        }

        SimpleDateFormat time = new SimpleDateFormat("hh:mm a", Locale.getDefault());
        StringBuilder sb = new StringBuilder();
        for (Deadline d : dayList) {
            if (sb.length() > 0) sb.append("\n\n");
            String status = d.done ? "Done" : (d.dueAt < now ? "Overdue" : "Pending");
            sb.append(time.format(new java.util.Date(d.dueAt))).append("  \u2022  ").append(d.title);
            sb.append("\n");
            if (d.course != null && !d.course.isEmpty()) sb.append(d.course).append("  \u2022  ");
            sb.append(status);
            if (d.isShared) sb.append("  \u2022  Shared by CR");
        }
        b.tvDayList.setText(sb.toString());
    }
}
