package com.example.campuscompanion.util;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.graphics.pdf.PdfDocument;

import com.example.campuscompanion.data.local.AttendanceCourse;
import com.example.campuscompanion.data.local.AttendanceRecord;
import com.example.campuscompanion.data.local.Topic;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Builds a simple A4 attendance report (no extra libraries: Android's own PdfDocument). */
public final class AttendancePdf {
    private static final int W = 595, H = 842, MARGIN = 40;

    private AttendancePdf() { }

    public static File create(Context c, String studentName, String sectionLabel,
                              List<AttendanceCourse> courses, List<AttendanceRecord> records,
                              List<Topic> topics) throws IOException {
        File dir = new File(c.getCacheDir(), "exports");
        if (!dir.exists() && !dir.mkdirs()) throw new IOException("Cannot create export folder");
        File out = new File(dir, "attendance-report.pdf");

        PdfDocument doc = new PdfDocument();
        try {
            Page page = new Page(doc);
            Map<Integer, int[]> counts = AttendanceStats.counts(records);
            SimpleDateFormat day = new SimpleDateFormat("d MMM yyyy", Locale.getDefault());

            page.text("Attendance report", 22, true, 0xFF2E1F7A);
            page.gap(4);
            String who = (studentName == null || studentName.isEmpty()) ? "Student" : studentName;
            page.text(who + (sectionLabel == null || sectionLabel.isEmpty() ? "" : "  •  " + sectionLabel),
                    12, false, Color.DKGRAY);
            page.text("Created " + day.format(new Date()) + " with Campus Companion", 10, false, Color.GRAY);
            page.gap(8);

            int overall = AttendanceStats.overallPercent(counts);
            if (overall >= 0) page.text("Overall attendance: " + overall + "%", 14, true, Color.BLACK);
            page.gap(10);

            if (courses.isEmpty()) page.text("No courses added yet.", 12, false, Color.DKGRAY);

            for (AttendanceCourse course : courses) {
                AttendanceMath.Result r = AttendanceStats.forCourse(course, counts);
                page.ensure(110);
                page.rule();
                page.text(course.title(), 14, true, Color.BLACK);
                page.text(r.held == 0
                                ? "No classes marked yet  •  minimum " + course.minPercent + "%"
                                : r.percent + "%   (present " + r.present + ", absent " + r.absent
                                + ", cancelled " + r.cancelled + ")   •   minimum " + course.minPercent + "%",
                        12, false, r.level == AttendanceMath.LOW ? 0xFFB91C1C : Color.BLACK);
                page.text(r.advice, 11, false, Color.DKGRAY);

                int days = ExamUtil.daysLeft(course.examDate);
                if (days >= 0) {
                    page.text("Exam: " + day.format(new Date(course.examDate)) + " (" + ExamUtil.label(days) + ")",
                            11, false, Color.DKGRAY);
                }

                List<String> covered = new ArrayList<>();
                for (Topic t : topics) {
                    if (t.courseId == course.id && t.taught && t.title != null) covered.add(t.title);
                }
                if (!covered.isEmpty()) {
                    StringBuilder sb = new StringBuilder("Topics covered: ");
                    for (int i = 0; i < covered.size(); i++) {
                        if (i > 0) sb.append(", ");
                        sb.append(covered.get(i));
                    }
                    page.text(sb.toString(), 11, false, Color.DKGRAY);
                }
                page.gap(10);
            }
            page.finish();

            try (FileOutputStream fos = new FileOutputStream(out)) {
                doc.writeTo(fos);
            }
        } finally {
            doc.close();
        }
        return out;
    }

    /** Tracks the current page and the vertical position, starting a new page when needed. */
    private static final class Page {
        private final PdfDocument doc;
        private PdfDocument.Page page;
        private Canvas canvas;
        private float y;
        private int number = 0;

        Page(PdfDocument doc) {
            this.doc = doc;
            start();
        }

        private void start() {
            number++;
            page = doc.startPage(new PdfDocument.PageInfo.Builder(W, H, number).create());
            canvas = page.getCanvas();
            y = MARGIN;
        }

        void finish() {
            doc.finishPage(page);
        }

        void ensure(float needed) {
            if (y + needed > H - MARGIN) {
                doc.finishPage(page);
                start();
            }
        }

        void gap(float dy) {
            y += dy;
        }

        void rule() {
            Paint p = new Paint();
            p.setColor(0xFFDDDDDD);
            canvas.drawLine(MARGIN, y, W - MARGIN, y, p);
            y += 10;
        }

        void text(String s, float size, boolean bold, int color) {
            Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
            p.setTextSize(size);
            p.setColor(color);
            p.setTypeface(bold ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT);
            float lineH = size * 1.35f;
            for (String line : wrap(s, p, W - 2 * MARGIN)) {
                ensure(lineH);
                canvas.drawText(line, MARGIN, y + size, p);
                y += lineH;
            }
        }

        private static List<String> wrap(String s, Paint p, float width) {
            List<String> lines = new ArrayList<>();
            if (s == null) return lines;
            String rest = s;
            while (!rest.isEmpty()) {
                int fit = p.breakText(rest, true, width, null);
                if (fit >= rest.length()) {
                    lines.add(rest);
                    break;
                }
                int cut = rest.lastIndexOf(' ', fit);
                if (cut <= 0) cut = fit;
                lines.add(rest.substring(0, cut).trim());
                rest = rest.substring(cut).trim();
            }
            return lines;
        }
    }
}
