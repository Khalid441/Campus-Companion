package com.example.campuscompanion.util;

import com.example.campuscompanion.data.local.AttendanceCourse;

import java.util.List;
import java.util.Locale;

/** Links a class in the routine ("CSE 2209 Theory") to the attendance course the student added. */
public final class CourseMatcher {
    private CourseMatcher() { }

    private static String norm(String s) {
        return s == null ? "" : s.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
    }

    public static AttendanceCourse find(List<AttendanceCourse> courses, String routineCourse) {
        String r = norm(routineCourse);
        if (r.length() < 3 || courses == null) return null;
        for (AttendanceCourse c : courses) {
            String code = norm(c.code);
            String name = norm(c.name);
            if (!code.isEmpty() && (r.contains(code) || code.contains(r))) return c;
            if (!name.isEmpty() && (r.equals(name) || (name.length() >= 4 && r.contains(name))
                    || (r.length() >= 4 && name.contains(r)))) return c;
        }
        return null;
    }

    /** True when two typed subject names obviously mean the same course. */
    public static boolean same(String a, String b) {
        String x = norm(a), y = norm(b);
        if (x.length() < 3 || y.length() < 3) return false;
        return x.equals(y) || (x.length() >= 4 && y.contains(x)) || (y.length() >= 4 && x.contains(y));
    }
}
