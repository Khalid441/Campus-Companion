package com.example.campuscompanion.ui;

import android.content.res.ColorStateList;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;

import androidx.appcompat.app.AppCompatActivity;

import com.example.campuscompanion.R;
import com.example.campuscompanion.data.local.AppDatabase;
import com.example.campuscompanion.data.local.AttendanceCourse;
import com.example.campuscompanion.data.local.AttendanceRecord;
import com.example.campuscompanion.data.local.Deadline;
import com.example.campuscompanion.data.local.RoutineEntry;
import com.example.campuscompanion.data.local.StudySession;
import com.example.campuscompanion.data.local.Topic;
import com.example.campuscompanion.databinding.ActivityDayBinding;
import com.example.campuscompanion.databinding.ItemDayChipBinding;
import com.example.campuscompanion.databinding.ItemDayEventBinding;
import com.example.campuscompanion.util.CourseMatcher;
import com.example.campuscompanion.util.DateUtil;
import com.example.campuscompanion.util.RevisionPlan;
import com.example.campuscompanion.util.TimeUtil;
import com.google.android.material.color.MaterialColors;
import com.google.android.material.snackbar.Snackbar;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * "My day": classes, study sessions, deadlines and revision for one day in a single timeline.
 * Classes can be marked Present / Absent right here.
 */
public class DayActivity extends AppCompatActivity {

    private static final int DAYS_BEFORE = 2;
    private static final int DAYS_AFTER = 4;
    private static final int NO_TIME = Integer.MAX_VALUE;

    private static class Ev {
        int minutes;
        String kind, title, meta;
        int color;
        AttendanceCourse course;     // only for classes
        boolean isClass;
    }

    private ActivityDayBinding b;
    private long selected;           // start of the shown day

    private List<RoutineEntry> routine = new ArrayList<>();
    private List<StudySession> study = new ArrayList<>();
    private List<Deadline> deadlines = new ArrayList<>();
    private List<AttendanceCourse> courses = new ArrayList<>();
    private List<AttendanceRecord> records = new ArrayList<>();
    private List<Topic> topics = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        b = ActivityDayBinding.inflate(getLayoutInflater());
        setContentView(b.getRoot());

        selected = DateUtil.startOfDay(System.currentTimeMillis());
        buildStrip();

        AppDatabase db = AppDatabase.get(this);
        db.routineDao().getAll().observe(this, l -> { routine = l; render(); });
        db.studyDao().getAll().observe(this, l -> { study = l; render(); });
        db.deadlineDao().getAll().observe(this, l -> { deadlines = l; render(); });
        db.attendanceDao().getCourses().observe(this, l -> { courses = l; render(); });
        db.attendanceDao().getAllRecords().observe(this, l -> { records = l; render(); });
        db.topicDao().getAll().observe(this, l -> { topics = l; render(); });
    }

    // ---------------------------------------------------------------------
    // Day strip
    // ---------------------------------------------------------------------

    private void buildStrip() {
        b.dayStrip.removeAllViews();
        long today = DateUtil.startOfDay(System.currentTimeMillis());
        SimpleDateFormat dow = new SimpleDateFormat("EEE", Locale.getDefault());
        SimpleDateFormat dom = new SimpleDateFormat("d", Locale.getDefault());

        for (int i = -DAYS_BEFORE; i <= DAYS_AFTER; i++) {
            final long day = DateUtil.addDays(today, i);
            ItemDayChipBinding c = ItemDayChipBinding.inflate(LayoutInflater.from(this), b.dayStrip, false);
            Date d = new Date(day);
            c.btnChip.setText(dow.format(d) + "\n" + dom.format(d));

            boolean on = day == selected;
            int bg = on ? MaterialColors.getColor(c.btnChip, com.google.android.material.R.attr.colorPrimary)
                    : MaterialColors.getColor(c.btnChip, com.google.android.material.R.attr.colorSurfaceVariant);
            int fg = on ? MaterialColors.getColor(c.btnChip, com.google.android.material.R.attr.colorOnPrimary)
                    : MaterialColors.getColor(c.btnChip, com.google.android.material.R.attr.colorOnSurfaceVariant);
            c.btnChip.setBackgroundTintList(ColorStateList.valueOf(bg));
            c.btnChip.setTextColor(fg);
            c.btnChip.setOnClickListener(v -> {
                selected = day;
                buildStrip();
                render();
            });
            b.dayStrip.addView(c.getRoot());
        }
    }

    // ---------------------------------------------------------------------
    // Timeline
    // ---------------------------------------------------------------------

    private void render() {
        if (b == null) return;
        List<Ev> events = collect();

        b.timeline.removeAllViews();
        b.emptyText.setVisibility(events.isEmpty() ? View.VISIBLE : View.GONE);
        b.tvDaySub.setText(new SimpleDateFormat("EEEE, d MMMM", Locale.getDefault()).format(new Date(selected))
                + (events.isEmpty() ? "" : "  •  " + events.size() + (events.size() == 1 ? " item" : " items")));

        for (int i = 0; i < events.size(); i++) {
            b.timeline.addView(row(events.get(i), i == events.size() - 1));
        }
    }

    private List<Ev> collect() {
        List<Ev> out = new ArrayList<>();
        long now = System.currentTimeMillis();
        int dow = DateUtil.dayOfWeek(selected);
        int dayIndex = dow - 1;                       // 0 = Sunday
        int classColor = getColor(R.color.home_classes);
        int deadlineColor = getColor(R.color.home_deadline);
        int teal = getColor(R.color.study_teal);

        for (RoutineEntry r : routine) {
            if (r.dayOfWeek != dow) continue;
            Ev e = new Ev();
            e.isClass = true;
            e.minutes = r.startMinutes;
            e.kind = "CLASS";
            e.title = r.course;
            e.color = classColor;
            e.course = CourseMatcher.find(courses, r.course);
            int len = r.endMinutes - r.startMinutes;
            String room = (r.room == null || r.room.isEmpty()) ? "" : "Room " + r.room + "  •  ";
            e.meta = room + TimeUtil.fmt(r.startMinutes) + " – " + TimeUtil.fmt(r.endMinutes)
                    + (len > 0 ? "  •  " + duration(len) : "");
            out.add(e);
        }

        for (StudySession s : study) {
            if (!s.enabled || (s.daysMask & (1 << dayIndex)) == 0) continue;
            Ev e = new Ev();
            e.minutes = s.startMinutes;
            e.kind = "STUDY";
            e.title = s.subject;
            e.color = teal;
            e.meta = TimeUtil.fmt(s.startMinutes) + " – " + TimeUtil.fmt(s.endMinutes)
                    + (s.topic == null || s.topic.isEmpty() ? "" : "  •  " + s.topic);
            out.add(e);
        }

        for (Deadline d : deadlines) {
            if (!DateUtil.sameDay(d.dueAt, selected)) continue;
            Ev e = new Ev();
            e.minutes = DateUtil.minutesOfDay(d.dueAt);
            e.kind = d.done ? "DEADLINE • DONE" : "DEADLINE";
            e.title = d.title;
            e.color = deadlineColor;
            e.meta = (d.course == null || d.course.isEmpty() ? "" : d.course + "  •  ")
                    + "Due " + TimeUtil.fmt(e.minutes);
            out.add(e);
        }

        Collections.sort(out, (x, y) -> Integer.compare(x.minutes, y.minutes));

        boolean isToday = selected == DateUtil.startOfDay(now);
        for (Topic t : topics) {
            if (!RevisionPlan.isActive(t)) continue;
            boolean show = isToday ? RevisionPlan.isDue(t, now)
                    : DateUtil.startOfDay(t.nextRevisionAt) == selected;
            if (!show) continue;
            Ev e = new Ev();
            e.minutes = NO_TIME;
            e.kind = "REVISE • " + (t.revisedCount + 1) + " OF " + RevisionPlan.STAGES;
            e.title = t.title;
            e.color = teal;
            String cname = null;
            for (AttendanceCourse c : courses) if (c.id == t.courseId) cname = c.title();
            e.meta = cname == null ? "Revision" : cname;
            out.add(e);
        }
        return out;
    }

    private static String duration(int minutes) {
        int h = minutes / 60, m = minutes % 60;
        if (h == 0) return m + "m";
        return m == 0 ? h + "h" : h + "h " + m + "m";
    }

    private View row(Ev e, boolean last) {
        ItemDayEventBinding r = ItemDayEventBinding.inflate(LayoutInflater.from(this), b.timeline, false);
        r.tvEvTime.setText(e.minutes == NO_TIME ? "Any time" : TimeUtil.fmt(e.minutes));
        r.tvEvKind.setText(e.kind);
        r.tvEvKind.setTextColor(e.color);
        r.tvEvTitle.setText(e.title);
        r.tvEvMeta.setText(e.meta);
        r.evCard.setStrokeColor(e.color);
        r.evLine.setVisibility(last ? View.INVISIBLE : View.VISIBLE);

        GradientDrawable dot = new GradientDrawable();
        dot.setShape(GradientDrawable.OVAL);
        dot.setColor(e.color);
        r.evDot.setBackground(dot);

        if (e.isClass) bindAttendance(r, e);
        return r.getRoot();
    }

    /** Present / Absent buttons or the saved status for a class on the shown day. */
    private void bindAttendance(ItemDayEventBinding r, Ev e) {
        if (e.course == null) {
            if (selected <= DateUtil.startOfDay(System.currentTimeMillis())) {
                r.tvEvStatus.setVisibility(View.VISIBLE);
                r.tvEvStatus.setText("Add this course in Attendance to track it");
            }
            return;
        }

        AttendanceRecord rec = null;
        for (AttendanceRecord x : records) {
            if (x.courseId == e.course.id && x.date == selected) { rec = x; break; }
        }

        if (rec != null) {
            String[] names = {"Present", "Absent", "Cancelled"};
            boolean note = rec.topic != null && !rec.topic.isEmpty();
            r.tvEvStatus.setVisibility(View.VISIBLE);
            r.tvEvStatus.setText(names[rec.status] + (note ? "  •  Note added" : ""));
        } else if (selected <= DateUtil.startOfDay(System.currentTimeMillis())) {
            r.evActions.setVisibility(View.VISIBLE);
            r.btnEvPresent.setOnClickListener(v -> mark(e.course, AttendanceRecord.PRESENT));
            r.btnEvAbsent.setOnClickListener(v -> mark(e.course, AttendanceRecord.ABSENT));
        }
    }

    private void mark(AttendanceCourse course, int status) {
        AttendanceActions.mark(this, course, status, selected, rec ->
                Snackbar.make(b.getRoot(), (status == AttendanceRecord.PRESENT ? "Present" : "Absent")
                                + " saved for " + course.name, Snackbar.LENGTH_LONG)
                        .setAction("Undo", v -> AppDatabase.executor.execute(() ->
                                AppDatabase.get(getApplicationContext()).attendanceDao().deleteRecord(rec)))
                        .show());
    }
}
