package com.example.campuscompanion.ui;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;

import androidx.appcompat.app.AppCompatActivity;

import com.example.campuscompanion.R;
import com.example.campuscompanion.data.local.AppDatabase;
import com.example.campuscompanion.data.local.AttendanceRecord;
import com.example.campuscompanion.data.local.Deadline;
import com.example.campuscompanion.data.local.Topic;
import com.example.campuscompanion.databinding.ActivityRecapBinding;
import com.example.campuscompanion.databinding.ItemRecapCardBinding;
import com.example.campuscompanion.util.DateUtil;
import com.example.campuscompanion.util.RevisionPlan;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/** What happened in the last 7 days: attendance, topics, revision and deadlines. */
public class RecapActivity extends AppCompatActivity {

    private ActivityRecapBinding b;
    private List<AttendanceRecord> records = new ArrayList<>();
    private List<Topic> topics = new ArrayList<>();
    private List<Deadline> deadlines = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        b = ActivityRecapBinding.inflate(getLayoutInflater());
        setContentView(b.getRoot());

        AppDatabase db = AppDatabase.get(this);
        db.attendanceDao().getAllRecords().observe(this, l -> { records = l; render(); });
        db.topicDao().getAll().observe(this, l -> { topics = l; render(); });
        db.deadlineDao().getAll().observe(this, l -> { deadlines = l; render(); });
    }

    private void render() {
        long now = System.currentTimeMillis();
        long from = DateUtil.addDays(now, -6);
        long to = DateUtil.endOfDay(now);
        SimpleDateFormat f = new SimpleDateFormat("d MMM", Locale.getDefault());
        b.tvRecapRange.setText(f.format(new Date(from)) + " – " + f.format(new Date(now)) + "  (last 7 days)");
        b.list.removeAllViews();

        // ---- attendance ----
        int present = 0, absent = 0, cancelled = 0;
        for (AttendanceRecord r : records) {
            if (r.date < from || r.date > to) continue;
            if (r.status == AttendanceRecord.PRESENT) present++;
            else if (r.status == AttendanceRecord.ABSENT) absent++;
            else cancelled++;
        }
        int held = present + absent;
        String attBig = held == 0 ? "No classes marked" : present + " of " + held + " attended";
        String attDetail = held == 0 ? "Mark your classes in Attendance."
                : Math.round(100.0 * present / held) + "%" + (absent > 0 ? "  •  missed " + absent : "  •  none missed")
                + (cancelled > 0 ? "  •  " + cancelled + " cancelled" : "");
        addCard("ATTENDANCE", attBig, attDetail, getColor(R.color.home_attendance), AttendanceActivity.class);

        // ---- topics covered ----
        List<String> covered = new ArrayList<>();
        for (Topic t : topics) if (t.taught && t.taughtAt >= from && t.taughtAt <= to) covered.add(t.title);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < covered.size() && i < 4; i++) {
            if (i > 0) sb.append("\n");
            sb.append("• ").append(covered.get(i));
        }
        if (covered.size() > 4) sb.append("\n+ ").append(covered.size() - 4).append(" more");
        addCard("TOPICS COVERED", covered.isEmpty() ? "None noted" : String.valueOf(covered.size()),
                covered.isEmpty() ? "Tap Present in a class and write what was taught." : sb.toString(),
                getColor(R.color.home_classes), AttendanceActivity.class);

        // ---- revision ----
        int due = 0, notRevised = 0;
        for (Topic t : topics) {
            if (RevisionPlan.isDue(t, now)) due++;
            if (t.taught && t.revisedCount == 0) notRevised++;
        }
        addCard("REVISION", due == 0 ? "All caught up" : due + (due == 1 ? " topic due" : " topics due"),
                notRevised == 0 ? "Every taught topic was revised at least once."
                        : notRevised + (notRevised == 1 ? " taught topic has" : " taught topics have")
                        + " not been revised yet.",
                getColor(R.color.study_teal_text), RevisionActivity.class);

        // ---- deadlines ----
        int total = 0, done = 0, upcoming = 0;
        long nextWeek = DateUtil.endOfDay(DateUtil.addDays(now, 7));
        for (Deadline d : deadlines) {
            if (d.dueAt >= from && d.dueAt <= to) {
                total++;
                if (d.done) done++;
            }
            if (!d.done && d.dueAt > to && d.dueAt <= nextWeek) upcoming++;
        }
        addCard("DEADLINES", total == 0 ? "None this week" : done + " of " + total + " done",
                upcoming == 0 ? "Nothing due in the next 7 days."
                        : upcoming + (upcoming == 1 ? " deadline" : " deadlines") + " due in the next 7 days.",
                getColor(R.color.home_deadline), DeadlinesActivity.class);

        addHeatmap(now);
    }

    /** 13 weeks of activity: classes attended, revisions and focus time. */
    private void addHeatmap(long now) {
        final int cells = com.example.campuscompanion.view.HeatmapView.WEEKS * 7;
        long today = DateUtil.startOfDay(now);
        long thisWeek = DateUtil.addDays(today, -(DateUtil.dayOfWeek(today) - 1));   // Sunday
        long start = DateUtil.addDays(thisWeek, -(com.example.campuscompanion.view.HeatmapView.WEEKS - 1) * 7);

        java.util.Map<Long, Integer> revisions = com.example.campuscompanion.util.StudyStats.revisionsPerDay(this);
        java.util.Map<Long, Integer> classes = new java.util.HashMap<>();
        for (AttendanceRecord r : records) {
            if (r.status != AttendanceRecord.PRESENT) continue;
            Integer n = classes.get(r.date);
            classes.put(r.date, n == null ? 1 : n + 1);
        }

        final int[] levels = new int[cells];
        final String[] details = new String[cells];
        int firstFuture = cells;
        int activeDays = 0;
        SimpleDateFormat f = new SimpleDateFormat("EEE d MMM", Locale.getDefault());
        for (int i = 0; i < cells; i++) {
            long day = DateUtil.addDays(start, i);
            if (day > today) {
                firstFuture = Math.min(firstFuture, i);
                continue;
            }
            int c = classes.containsKey(day) ? classes.get(day) : 0;
            int rv = revisions.containsKey(day) ? revisions.get(day) : 0;
            int focus = com.example.campuscompanion.util.FocusStats.forDay(this, day);
            int score = c * 2 + rv * 2 + Math.min(4, focus / 15);
            levels[i] = score == 0 ? 0 : score <= 2 ? 1 : score <= 4 ? 2 : score <= 7 ? 3 : 4;
            if (score > 0) activeDays++;
            details[i] = f.format(new Date(day)) + ": " + c + (c == 1 ? " class" : " classes") + ", "
                    + rv + (rv == 1 ? " revision" : " revisions") + ", " + focus + " min focus";
        }

        com.example.campuscompanion.databinding.ItemHeatmapBinding h =
                com.example.campuscompanion.databinding.ItemHeatmapBinding.inflate(LayoutInflater.from(this), b.list, false);
        h.tvHeatBig.setText(activeDays + (activeDays == 1 ? " active day" : " active days"));
        h.heatmap.setLevels(levels, firstFuture);
        h.heatmap.setContentDescription("Study activity over the last 13 weeks: " + activeDays + " active days");
        h.heatmap.setOnCellListener(index -> h.tvHeatDetail.setText(details[index]));
        b.list.addView(h.getRoot());
    }

    private void addCard(String label, String big, String detail, int color, Class<?> target) {
        ItemRecapCardBinding c = ItemRecapCardBinding.inflate(LayoutInflater.from(this), b.list, false);
        c.tvRecapLabel.setText(label);
        c.tvRecapLabel.setTextColor(color);
        c.tvRecapBig.setText(big);
        c.tvRecapDetail.setText(detail);
        c.tvRecapDetail.setVisibility(detail == null || detail.isEmpty() ? View.GONE : View.VISIBLE);
        c.recapCard.setStrokeColor(color);
        c.recapCard.setOnClickListener(v -> startActivity(new Intent(this, target)));
        b.list.addView(c.getRoot());
    }
}
