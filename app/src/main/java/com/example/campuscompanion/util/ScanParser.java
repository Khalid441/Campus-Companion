package com.example.campuscompanion.util;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Turns OCR text into routine entries or deadlines. Pure Java so it can be unit-tested. */
public final class ScanParser {
    private ScanParser() {}

    public static final class Slot {
        public int day;            // Calendar.SUNDAY..SATURDAY
        public int start, end;     // minutes from midnight
        public String course = "";
        public String room = "";
    }

    public static final class Due {
        public String title;
        public long millis;
        public boolean hasTime;
    }

    private static final String[][] DAY_NAMES = {
            {"sun", "sunday"}, {"mon", "monday"}, {"tue", "tuesday", "tues"}, {"wed", "wednesday"},
            {"thu", "thursday", "thur", "thurs"}, {"fri", "friday"}, {"sat", "saturday"}};

    private static final Pattern RANGE = Pattern.compile(
            "(\\d{1,2})(?:[:.](\\d{2}))?\\s*(am|pm|a\\.m\\.|p\\.m\\.)?\\s*(?:-|–|—|to)\\s*(\\d{1,2})(?:[:.](\\d{2}))?\\s*(am|pm|a\\.m\\.|p\\.m\\.)?",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern CODE = Pattern.compile("\\b([A-Za-z]{2,4})[\\s-]?(\\d{3,4})\\b");
    private static final Pattern ROOM = Pattern.compile("(?:room|rm)\\.?\\s*[:#-]?\\s*([A-Za-z0-9-]+)", Pattern.CASE_INSENSITIVE);

    /** Returns Calendar day (1..7) if the line contains a day name as a whole word, else 0. */
    static int dayIn(String line) {
        String low = line.toLowerCase();
        for (int d = 0; d < 7; d++) {
            for (String n : DAY_NAMES[d]) {
                if (Pattern.compile("\\b" + n + "\\b").matcher(low).find()) return d + 1;
            }
        }
        return 0;
    }

    public static List<Slot> routine(String text) {
        List<Slot> out = new ArrayList<>();
        if (text == null) return out;
        int current = 0;
        for (String raw : text.split("\\r?\\n")) {
            String line = raw.trim();
            if (line.isEmpty()) continue;
            int day = dayIn(line);
            Matcher m = RANGE.matcher(line);
            boolean hasRange = m.find();
            if (day != 0 && !hasRange) { current = day; continue; }   // a header line
            if (day != 0) current = day;
            if (!hasRange || current == 0) continue;

            int h1 = Integer.parseInt(m.group(1)), m1 = m.group(2) == null ? 0 : Integer.parseInt(m.group(2));
            int h2 = Integer.parseInt(m.group(4)), m2 = m.group(5) == null ? 0 : Integer.parseInt(m.group(5));
            if (h1 > 24 || h2 > 24 || m1 > 59 || m2 > 59) continue;
            String a1 = m.group(3) == null ? "" : m.group(3).toLowerCase();
            String a2 = m.group(6) == null ? "" : m.group(6).toLowerCase();
            int s = to24(h1, a1, a1.isEmpty() && a2.isEmpty() ? 0 : -1) ;
            int e = to24(h2, a2, 0);
            if (a1.isEmpty() && !a2.isEmpty()) {               // "10 - 11 am": start shares the am/pm
                s = to24(h1, a2, 0);
                if (s > e) s = to24(h1, a2.startsWith("p") ? "am" : "pm", 0);
            } else if (a1.isEmpty()) {                          // no am/pm at all: campus hours
                s = h1 < 7 ? h1 + 12 : h1;
                e = h2 < 7 ? h2 + 12 : h2;
                if (h1 == 12) s = 12;
                if (h2 == 12) e = 12;
                if (e <= s) e = e + 12 <= 23 ? e + 12 : e;
            }
            int start = s * 60 + m1, end = e * 60 + m2;
            if (end <= start || start >= 24 * 60) continue;

            Slot slot = new Slot();
            slot.day = current;
            slot.start = start;
            slot.end = end;
            Matcher c = CODE.matcher(line);
            if (c.find()) slot.course = c.group(1).toUpperCase() + " " + c.group(2);
            Matcher r = ROOM.matcher(line);
            if (r.find()) slot.room = r.group(1);
            out.add(slot);
        }
        return out;
    }

    private static int to24(int h, String ap, int ignored) {
        if (ap.startsWith("p")) return h == 12 ? 12 : h + 12;
        if (ap.startsWith("a")) return h == 12 ? 0 : h;
        return h;
    }

    public static List<Due> deadlines(String text, long now) {
        List<Due> out = new ArrayList<>();
        if (text == null) return out;
        for (String raw : text.split("\\r?\\n")) {
            String line = raw.trim();
            if (line.length() < 4) continue;
            NaturalDate.Result r = NaturalDate.parse(line, now);
            if (!r.hasDate) continue;
            Due d = new Due();
            d.title = (r.title == null || r.title.trim().isEmpty()) ? line : r.title.trim();
            d.millis = r.millis;
            d.hasTime = r.hasTime;
            out.add(d);
        }
        return out;
    }
}
