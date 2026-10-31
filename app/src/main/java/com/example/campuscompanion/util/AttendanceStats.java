package com.example.campuscompanion.util;

import com.example.campuscompanion.data.local.AttendanceCourse;
import com.example.campuscompanion.data.local.AttendanceRecord;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Turns the saved courses and records into numbers for the screens. */
public final class AttendanceStats {
    private AttendanceStats() { }

    /** courseId -> {present, absent, cancelled} */
    public static Map<Integer, int[]> counts(List<AttendanceRecord> records) {
        Map<Integer, int[]> map = new HashMap<>();
        if (records == null) return map;
        for (AttendanceRecord r : records) {
            int[] c = map.get(r.courseId);
            if (c == null) {
                c = new int[3];
                map.put(r.courseId, c);
            }
            if (r.status >= 0 && r.status <= 2) c[r.status]++;
        }
        return map;
    }

    public static AttendanceMath.Result forCourse(AttendanceCourse course, Map<Integer, int[]> counts) {
        int[] c = counts.get(course.id);
        if (c == null) c = new int[3];
        return AttendanceMath.compute(c[0], c[1], c[2], course.minPercent, course.totalPlanned);
    }

    /** Overall percentage across all courses, or -1 when nothing is marked yet. */
    public static int overallPercent(Map<Integer, int[]> counts) {
        int present = 0, held = 0;
        for (int[] c : counts.values()) {
            present += c[0];
            held += c[0] + c[1];
        }
        return held == 0 ? -1 : (int) Math.round(100.0 * present / held);
    }

    /** Classes attended in a row across all courses (records are newest first). */
    public static int streak(List<AttendanceRecord> newestFirst) {
        if (newestFirst == null) return 0;
        int[] s = new int[newestFirst.size()];
        for (int i = 0; i < s.length; i++) s[i] = newestFirst.get(i).status;
        return AttendanceMath.streak(s);
    }
}
