package com.example.campuscompanion.util;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reads dates and times out of everyday English ("Assignment 3 due Friday 5 pm", "lab report on
 * 12 Nov at 9:30", "quiz in 3 days"). Used by voice input and by the photo scanner.
 * Day comes before month in numeric dates (05/11 = 5 November), as in Bangladesh.
 */
public final class NaturalDate {
    private NaturalDate() { }

    public static final class Result {
        /** Epoch millis of the due moment, or 0 when no date or time was found. */
        public long millis;
        public boolean hasDate;
        public boolean hasTime;
        /** The text with the date and time words taken out. */
        public String title = "";
    }

    private static final String[] MONTHS = {"jan", "feb", "mar", "apr", "may", "jun",
            "jul", "aug", "sep", "oct", "nov", "dec"};
    private static final String MONTH_RE =
            "(jan(?:uary)?|feb(?:ruary)?|mar(?:ch)?|apr(?:il)?|may|jun(?:e)?|jul(?:y)?|aug(?:ust)?|"
                    + "sep(?:t(?:ember)?)?|oct(?:ober)?|nov(?:ember)?|dec(?:ember)?)";
    private static final String[] DAYS = {"sun", "mon", "tue", "wed", "thu", "fri", "sat"};   // Calendar order

    private static final Pattern TIME_AMPM = Pattern.compile(
            "\\b(?:at\\s+)?(\\d{1,2})(?:[:.](\\d{2}))?\\s*([ap])\\.?\\s?m\\b\\.?", Pattern.CASE_INSENSITIVE);
    private static final Pattern TIME_24 = Pattern.compile(
            "\\b(?:at\\s+)?([01]?\\d|2[0-3]):([0-5]\\d)\\b");
    private static final Pattern NOON = Pattern.compile("\\b(?:at\\s+)?(noon|midnight)\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern IN_N = Pattern.compile(
            "\\bin\\s+(\\d+|an?|one|two|three|four|five|six|seven|eight|nine|ten)\\s+(hour|day|week)s?\\b",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern REL_DAY = Pattern.compile(
            "\\b(day after tomorrow|tomorrow|today|tonight)\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern WEEKDAY = Pattern.compile(
            "\\b(?:(next|this|coming)\\s+)?(sunday|monday|tuesday|wednesday|thursday|friday|saturday|"
                    + "sun|mon|tues?|wed|thurs?|thu|fri|sat)\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern D_MONTH = Pattern.compile(
            "\\b(\\d{1,2})(?:st|nd|rd|th)?\\s+(?:of\\s+)?" + MONTH_RE + "\\b(?:,?\\s+(\\d{4}))?", Pattern.CASE_INSENSITIVE);
    private static final Pattern MONTH_D = Pattern.compile(
            "\\b" + MONTH_RE + "\\s+(\\d{1,2})(?:st|nd|rd|th)?\\b(?:,?\\s+(\\d{4}))?", Pattern.CASE_INSENSITIVE);
    private static final Pattern NUMERIC = Pattern.compile(
            "\\b(\\d{1,2})[/\\-.](\\d{1,2})(?:[/\\-.](\\d{2,4}))?\\b");

    private static final class Span {
        final int start, end;
        Span(int s, int e) { start = s; end = e; }
        boolean overlaps(int s, int e) { return s < end && e > start; }
    }

    public static Result parse(String text, long now) {
        Result r = new Result();
        if (text == null) return r;
        String s = text.trim();
        List<Span> used = new ArrayList<>();

        Calendar nowCal = Calendar.getInstance();
        nowCal.setTimeInMillis(now);

        int hour = -1, minute = 0;
        Calendar date = null;          // day only
        long hoursAhead = -1;

        // ---- time of day ----
        Matcher m = TIME_AMPM.matcher(s);
        if (m.find()) {
            int h = Integer.parseInt(m.group(1));
            if (h >= 1 && h <= 12) {
                minute = m.group(2) == null ? 0 : Integer.parseInt(m.group(2));
                boolean pm = m.group(3).equalsIgnoreCase("p");
                hour = (h % 12) + (pm ? 12 : 0);
                used.add(new Span(m.start(), m.end()));
            }
        }
        if (hour < 0) {
            m = TIME_24.matcher(s);
            if (m.find()) {
                hour = Integer.parseInt(m.group(1));
                minute = Integer.parseInt(m.group(2));
                used.add(new Span(m.start(), m.end()));
            }
        }
        if (hour < 0) {
            m = NOON.matcher(s);
            if (m.find()) {
                hour = m.group(1).equalsIgnoreCase("noon") ? 12 : 23;
                minute = m.group(1).equalsIgnoreCase("noon") ? 0 : 59;
                used.add(new Span(m.start(), m.end()));
            }
        }

        // ---- "in 3 days" ----
        m = IN_N.matcher(s);
        if (m.find() && free(used, m.start(), m.end())) {
            int n = number(m.group(1));
            String unit = m.group(2).toLowerCase(Locale.ROOT);
            used.add(new Span(m.start(), m.end()));
            if (unit.equals("hour")) {
                hoursAhead = n;
            } else {
                date = dayOnly(nowCal);
                date.add(Calendar.DAY_OF_MONTH, unit.equals("week") ? 7 * n : n);
            }
        }

        // ---- today / tomorrow ----
        if (date == null && hoursAhead < 0) {
            m = REL_DAY.matcher(s);
            if (m.find() && free(used, m.start(), m.end())) {
                String w = m.group(1).toLowerCase(Locale.ROOT);
                date = dayOnly(nowCal);
                if (w.equals("tomorrow")) date.add(Calendar.DAY_OF_MONTH, 1);
                else if (w.startsWith("day after")) date.add(Calendar.DAY_OF_MONTH, 2);
                if (w.equals("tonight") && hour < 0) { hour = 21; minute = 0; }
                used.add(new Span(m.start(), m.end()));
            }
        }

        // ---- explicit dates ----
        if (date == null && hoursAhead < 0) {
            m = D_MONTH.matcher(s);
            if (m.find() && free(used, m.start(), m.end())) {
                date = explicit(nowCal, Integer.parseInt(m.group(1)), monthIndex(m.group(2)), m.group(3));
                if (date != null) used.add(new Span(m.start(), m.end()));
            }
        }
        if (date == null && hoursAhead < 0) {
            m = MONTH_D.matcher(s);
            if (m.find() && free(used, m.start(), m.end())) {
                date = explicit(nowCal, Integer.parseInt(m.group(2)), monthIndex(m.group(1)), m.group(3));
                if (date != null) used.add(new Span(m.start(), m.end()));
            }
        }
        if (date == null && hoursAhead < 0) {
            m = NUMERIC.matcher(s);
            while (m.find()) {
                if (!free(used, m.start(), m.end())) continue;
                int d = Integer.parseInt(m.group(1));
                int mo = Integer.parseInt(m.group(2)) - 1;
                if (d < 1 || d > 31 || mo < 0 || mo > 11) continue;
                String y = m.group(3);
                if (y != null && y.length() == 2) y = "20" + y;
                date = explicit(nowCal, d, mo, y);
                if (date != null) {
                    used.add(new Span(m.start(), m.end()));
                    break;
                }
            }
        }

        // ---- weekday ----
        if (date == null && hoursAhead < 0) {
            m = WEEKDAY.matcher(s);
            if (m.find() && free(used, m.start(), m.end())) {
                int target = weekdayIndex(m.group(2));
                date = dayOnly(nowCal);
                int diff = (target - (nowCal.get(Calendar.DAY_OF_WEEK) - 1) + 7) % 7;   // 0..6
                if (diff == 0) diff = 7;                                             // same weekday: next week
                date.add(Calendar.DAY_OF_MONTH, diff);
                used.add(new Span(m.start(), m.end()));
            }
        }

        // ---- build the moment ----
        if (hoursAhead >= 0) {
            r.millis = now + hoursAhead * 3600_000L;
            r.hasDate = true;
            r.hasTime = true;
        } else if (date != null) {
            r.hasDate = true;
            r.hasTime = hour >= 0;
            date.set(Calendar.HOUR_OF_DAY, hour >= 0 ? hour : 23);
            date.set(Calendar.MINUTE, hour >= 0 ? minute : 59);
            r.millis = date.getTimeInMillis();
        } else if (hour >= 0) {
            Calendar c = dayOnly(nowCal);
            c.set(Calendar.HOUR_OF_DAY, hour);
            c.set(Calendar.MINUTE, minute);
            if (c.getTimeInMillis() <= now) c.add(Calendar.DAY_OF_MONTH, 1);   // that time has passed today
            r.millis = c.getTimeInMillis();
            r.hasDate = true;
            r.hasTime = true;
        }

        r.title = cleanTitle(s, used);
        return r;
    }

    // ---------------------------------------------------------------------

    private static boolean free(List<Span> used, int s, int e) {
        for (Span x : used) if (x.overlaps(s, e)) return false;
        return true;
    }

    private static Calendar dayOnly(Calendar from) {
        Calendar c = (Calendar) from.clone();
        c.set(Calendar.HOUR_OF_DAY, 0);
        c.set(Calendar.MINUTE, 0);
        c.set(Calendar.SECOND, 0);
        c.set(Calendar.MILLISECOND, 0);
        return c;
    }

    private static Calendar explicit(Calendar now, int day, int month, String yearText) {
        if (month < 0 || day < 1 || day > 31) return null;
        Calendar c = dayOnly(now);
        c.setLenient(false);
        int year = yearText == null ? now.get(Calendar.YEAR) : Integer.parseInt(yearText);
        try {
            c.set(year, month, day);
            c.getTimeInMillis();   // throws when the date does not exist (31 Feb)
        } catch (IllegalArgumentException e) {
            return null;
        }
        c.setLenient(true);
        if (yearText == null && c.before(dayOnly(now))) c.add(Calendar.YEAR, 1);   // already passed: next year
        return c;
    }

    private static int monthIndex(String name) {
        String n = name.toLowerCase(Locale.ROOT);
        for (int i = 0; i < MONTHS.length; i++) if (n.startsWith(MONTHS[i])) return i;
        return -1;
    }

    private static int weekdayIndex(String name) {
        String n = name.toLowerCase(Locale.ROOT);
        for (int i = 0; i < DAYS.length; i++) if (n.startsWith(DAYS[i])) return i;
        return 0;
    }

    private static int number(String w) {
        switch (w.toLowerCase(Locale.ROOT)) {
            case "a": case "an": case "one": return 1;
            case "two": return 2;
            case "three": return 3;
            case "four": return 4;
            case "five": return 5;
            case "six": return 6;
            case "seven": return 7;
            case "eight": return 8;
            case "nine": return 9;
            case "ten": return 10;
            default:
                try { return Integer.parseInt(w); } catch (NumberFormatException e) { return 1; }
        }
    }

    private static String cleanTitle(String s, List<Span> used) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            boolean skip = false;
            for (Span x : used) if (i >= x.start && i < x.end) { skip = true; break; }
            if (!skip) sb.append(s.charAt(i));
        }
        String t = sb.toString().replaceAll("\\s+", " ").trim();
        // leading commands
        t = t.replaceAll("(?i)^(please\\s+)?(add|create|set|make|new|remind me (to|about))\\s+(a\\s+|an\\s+|the\\s+)?(new\\s+)?(deadline\\s+)?(for\\s+|to\\s+)?", "");
        // joiner words left behind at the ends
        String prev;
        do {
            prev = t;
            t = t.replaceAll("(?i)^(due|by|on|at|in|before|for|is|the)\\s+", "")
                    .replaceAll("(?i)\\s+(due|by|on|at|in|before|for|is|next|this|coming|of)$", "")
                    .replaceAll("^[\\s,.;:\\-]+|[\\s,.;:\\-]+$", "");
        } while (!t.equals(prev));
        t = t.replaceAll("(?i)\\s+(due|by|on|at|before)\\s+(due|by|on|at|before)\\b", " ");
        if (!t.isEmpty()) t = Character.toUpperCase(t.charAt(0)) + t.substring(1);
        return t;
    }
}
