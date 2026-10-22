package com.example.campuscompanion.view;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.MotionEvent;
import android.view.View;

import androidx.core.graphics.ColorUtils;

import com.google.android.material.color.MaterialColors;

import java.util.Calendar;

/**
 * Custom View: month calendar drawn with Canvas. Days that have deadlines get coloured dots
 * (red = overdue, orange = pending, green = done). Tap a day to select it.
 */
public class MonthCalendarView extends View {

    public interface OnDayClickListener {
        void onDayClick(int day);
    }

    private static final String[] DAY_NAMES = {"Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat"};
    private static final int RED = 0xFFE53935;
    private static final int ORANGE = 0xFFFB8C00;
    private static final int GREEN = 0xFF43A047;

    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint headerPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint ringPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private final float headerH;
    private final float cellH;
    private final int onSurface;
    private final int primary;
    private final int onPrimary;

    private int offset = 0;          // blank cells before day 1 (Sunday first)
    private int daysInMonth = 30;
    private int rows = 5;
    private int todayDay = -1;
    private int selectedDay = -1;
    private int[] pending = new int[32];
    private int[] done = new int[32];
    private int[] overdue = new int[32];
    private OnDayClickListener listener;

    public MonthCalendarView(Context context) {
        this(context, null);
    }

    public MonthCalendarView(Context context, AttributeSet attrs) {
        super(context, attrs);
        headerH = dp(30);
        cellH = dp(50);

        onSurface = MaterialColors.getColor(context,
                com.google.android.material.R.attr.colorOnSurface, Color.BLACK);
        primary = MaterialColors.getColor(context,
                com.google.android.material.R.attr.colorPrimary, 0xFF5B3FD1);
        onPrimary = MaterialColors.getColor(context,
                com.google.android.material.R.attr.colorOnPrimary, Color.WHITE);

        textPaint.setTextAlign(Paint.Align.CENTER);
        textPaint.setTextSize(sp(15));

        headerPaint.setTextAlign(Paint.Align.CENTER);
        headerPaint.setTextSize(sp(11));
        headerPaint.setFakeBoldText(true);
        headerPaint.setColor(ColorUtils.setAlphaComponent(onSurface, 150));

        ringPaint.setStyle(Paint.Style.STROKE);
        ringPaint.setStrokeWidth(dp(1.8f));
        ringPaint.setColor(primary);

        setMonth(Calendar.getInstance().get(Calendar.YEAR), Calendar.getInstance().get(Calendar.MONTH));
    }

    /** month: Calendar.JANUARY .. Calendar.DECEMBER */
    public void setMonth(int year, int month) {
        Calendar first = Calendar.getInstance();
        first.clear();
        first.set(year, month, 1);
        offset = first.get(Calendar.DAY_OF_WEEK) - 1;
        daysInMonth = first.getActualMaximum(Calendar.DAY_OF_MONTH);
        rows = (offset + daysInMonth + 6) / 7;

        Calendar now = Calendar.getInstance();
        boolean sameMonth = now.get(Calendar.YEAR) == year && now.get(Calendar.MONTH) == month;
        todayDay = sameMonth ? now.get(Calendar.DAY_OF_MONTH) : -1;

        requestLayout();
        invalidate();
    }

    /** Arrays are indexed by day of month (1..31). */
    public void setMarks(int[] overdueCounts, int[] pendingCounts, int[] doneCounts) {
        overdue = overdueCounts;
        pending = pendingCounts;
        done = doneCounts;
        invalidate();
    }

    public void setSelectedDay(int day) {
        selectedDay = day;
        invalidate();
    }

    public void setOnDayClickListener(OnDayClickListener l) {
        listener = l;
    }

    @Override
    protected void onMeasure(int widthSpec, int heightSpec) {
        int h = Math.round(headerH + rows * cellH);
        setMeasuredDimension(resolveSize(getSuggestedMinimumWidth(), widthSpec), h);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float colW = getWidth() / 7f;

        for (int i = 0; i < 7; i++) {
            canvas.drawText(DAY_NAMES[i], (i + 0.5f) * colW, headerH / 2f + dp(4), headerPaint);
        }

        for (int d = 1; d <= daysInMonth; d++) {
            int idx = offset + d - 1;
            int row = idx / 7;
            int col = idx % 7;
            float cx = (col + 0.5f) * colW;
            float top = headerH + row * cellH;
            float cy = top + dp(19);

            if (d == selectedDay) {
                fillPaint.setColor(primary);
                canvas.drawCircle(cx, cy, dp(17), fillPaint);
                textPaint.setColor(onPrimary);
                textPaint.setFakeBoldText(true);
            } else {
                if (d == todayDay) canvas.drawCircle(cx, cy, dp(17), ringPaint);
                textPaint.setColor(onSurface);
                textPaint.setFakeBoldText(d == todayDay);
            }
            float ty = cy - (textPaint.ascent() + textPaint.descent()) / 2f;
            canvas.drawText(String.valueOf(d), cx, ty, textPaint);

            // up to three dots under the number
            int[] colors = new int[3];
            int n = 0;
            if (overdue[d] > 0) colors[n++] = RED;
            if (pending[d] > 0) colors[n++] = ORANGE;
            if (done[d] > 0) colors[n++] = GREEN;
            float spacing = dp(7);
            float startX = cx - (n - 1) * spacing / 2f;
            float dy = top + cellH - dp(7);
            for (int k = 0; k < n; k++) {
                fillPaint.setColor(colors[k]);
                canvas.drawCircle(startX + k * spacing, dy, dp(2.5f), fillPaint);
            }
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        if (e.getAction() == MotionEvent.ACTION_DOWN) return true;
        if (e.getAction() == MotionEvent.ACTION_UP) {
            float x = e.getX();
            float y = e.getY();
            if (y >= headerH && getWidth() > 0) {
                int col = (int) (x / (getWidth() / 7f));
                int row = (int) ((y - headerH) / cellH);
                int day = row * 7 + col - offset + 1;
                if (col >= 0 && col < 7 && day >= 1 && day <= daysInMonth) {
                    performClick();
                    if (listener != null) listener.onDayClick(day);
                }
            }
            return true;
        }
        return super.onTouchEvent(e);
    }

    @Override
    public boolean performClick() {
        return super.performClick();
    }

    private float dp(float v) {
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, getResources().getDisplayMetrics());
    }

    private float sp(float v) {
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, v, getResources().getDisplayMetrics());
    }
}
