package com.example.campuscompanion.util;

import com.example.campuscompanion.data.local.Deadline;
import com.example.campuscompanion.data.local.Exam;
import com.example.campuscompanion.data.local.RoutineEntry;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

/** Builds an iCalendar (.ics) file that Google Calendar, Outlook and Apple Calendar can import. */
public final class IcsBuilder {
    private final StringBuilder sb = new StringBuilder();
    private final SimpleDateFormat utc = new SimpleDateFormat("yyyyMMdd'T'HHmmss'Z'", Locale.US);
    private final SimpleDateFormat dayOnly = new SimpleDateFormat("yyyyMMdd", Locale.US);
    private final SimpleDateFormat local = new SimpleDateFormat("yyyyMMdd'T'HHmmss", Locale.US);
    private final String stamp;
    private int events = 0;

    private static final String[] BYDAY = {"SU", "MO", "TU", "WE", "TH", "FR", "SA"};   // Calendar.SUNDAY = 1

    public IcsBuilder() {
        utc.setTimeZone(TimeZone.getTimeZone("UTC"));
        stamp = utc.format(new Date());
        line("BEGIN:VCALENDAR");
        line("VERSION:2.0");
        line("PRODID:-//Campus Companion//Student planner//EN");
        line("CALSCALE:GREGORIAN");
        line("METHOD:PUBLISH");
    }

    public int count() { return events; }

    public IcsBuilder deadlines(List<Deadline> list) {
        for (Deadline d : list) {
            if (d.done || d.dueAt <= 0) continue;
            begin("dl-" + d.id + "-" + d.dueAt);
            line("DTSTART:" + utc.format(new Date(d.dueAt)));
            line("DTEND:" + utc.format(new Date(d.dueAt + 30 * 60_000L)));
            line("SUMMARY:" + esc("Due: " + d.title));
            String desc = join(d.course, d.note);
            if (!desc.isEmpty()) line("DESCRIPTION:" + esc(desc));
            line("BEGIN:VALARM");
            line("TRIGGER:-PT1H");
            line("ACTION:DISPLAY");
            line("DESCRIPTION:" + esc("Due in 1 hour: " + d.title));
            line("END:VALARM");
            end();
        }
        return this;
    }

    public IcsBuilder exams(List<Exam> list) {
        for (Exam e : list) {
            if (e.examDate <= 0) continue;
            begin("ex-" + e.id + "-" + e.examDate);
            line("DTSTART;VALUE=DATE:" + dayOnly.format(new Date(e.examDate)));
            line("DTEND;VALUE=DATE:" + dayOnly.format(new Date(DateUtil.addDays(e.examDate, 1))));
            line("SUMMARY:" + esc("Exam: " + e.subject));
            if (e.note != null && !e.note.isEmpty()) line("DESCRIPTION:" + esc(e.note));
            end();
        }
        return this;
    }

    /** Weekly repeating classes, starting from their next occurrence (floating local time). */
    public IcsBuilder routine(List<RoutineEntry> list) {
        long now = System.currentTimeMillis();
        for (RoutineEntry r : list) {
            Calendar c = Calendar.getInstance();
            c.setTimeInMillis(DateUtil.startOfDay(now));
            int diff = (r.dayOfWeek - c.get(Calendar.DAY_OF_WEEK) + 7) % 7;
            c.add(Calendar.DAY_OF_MONTH, diff);
            Calendar start = (Calendar) c.clone();
            start.add(Calendar.MINUTE, r.startMinutes);
            Calendar finish = (Calendar) c.clone();
            finish.add(Calendar.MINUTE, Math.max(r.endMinutes, r.startMinutes + 30));

            begin("cl-" + r.id + "-" + r.dayOfWeek + "-" + r.startMinutes);
            line("DTSTART:" + local.format(start.getTime()));
            line("DTEND:" + local.format(finish.getTime()));
            line("RRULE:FREQ=WEEKLY;BYDAY=" + BYDAY[(r.dayOfWeek - 1 + 7) % 7]);
            line("SUMMARY:" + esc(r.course));
            if (r.room != null && !r.room.isEmpty()) line("LOCATION:" + esc(r.room));
            if (r.teacher != null && !r.teacher.isEmpty()) line("DESCRIPTION:" + esc(r.teacher));
            end();
        }
        return this;
    }

    public String build() {
        return sb + "END:VCALENDAR\r\n";
    }

    // ---------------------------------------------------------------------

    private void begin(String uid) {
        events++;
        line("BEGIN:VEVENT");
        line("UID:" + uid + "@campuscompanion");
        line("DTSTAMP:" + stamp);
    }

    private void end() {
        line("END:VEVENT");
    }

    private static String join(String a, String b) {
        boolean ea = a == null || a.isEmpty(), eb = b == null || b.isEmpty();
        if (ea) return eb ? "" : b;
        return eb ? a : a + " - " + b;
    }

    /** Escapes text values as the iCalendar format requires. */
    static String esc(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace(";", "\\;").replace(",", "\\,")
                .replace("\r\n", "\\n").replace("\n", "\\n");
    }

    /** Lines longer than 75 characters are folded: continuation lines start with a space. */
    private void line(String s) {
        while (s.length() > 73) {
            sb.append(s, 0, 73).append("\r\n ");
            s = s.substring(73);
        }
        sb.append(s).append("\r\n");
    }
}
