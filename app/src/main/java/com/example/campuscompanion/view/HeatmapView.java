package com.example.campuscompanion.view;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

/**
 * GitHub-style activity calendar: columns are weeks, rows are days (Sunday at the top).
 * Each cell has a level 0 to 4; future days are left empty.
 */
public class HeatmapView extends View {

    public interface OnCellListener {
        void onCell(int index);
    }

    public static final int WEEKS = 13;
    private static final int[] COLORS = {0x33888888, 0xFFB2DFDB, 0xFF4DB6AC, 0xFF00897B, 0xFF00574B};

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private int[] levels = new int[WEEKS * 7];
    private int firstFuture = WEEKS * 7;       // cells from here on are in the future
    private OnCellListener listener;
    private float cell, gap;

    public HeatmapView(Context c) { super(c); }
    public HeatmapView(Context c, AttributeSet a) { super(c, a); }
    public HeatmapView(Context c, AttributeSet a, int s) { super(c, a, s); }

    /** @param levels 91 values (0 to 4), oldest day first, starting on a Sunday. */
    public void setLevels(int[] levels, int firstFutureIndex) {
        this.levels = levels;
        this.firstFuture = firstFutureIndex;
        invalidate();
    }

    public void setOnCellListener(OnCellListener l) {
        listener = l;
    }

    @Override
    protected void onMeasure(int widthSpec, int heightSpec) {
        int w = MeasureSpec.getSize(widthSpec);
        float g = getResources().getDisplayMetrics().density * 3;
        float size = (w - getPaddingLeft() - getPaddingRight() - g * (WEEKS - 1)) / WEEKS;
        int h = (int) (7 * size + 6 * g) + getPaddingTop() + getPaddingBottom();
        setMeasuredDimension(w, h);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        gap = getResources().getDisplayMetrics().density * 3;
        cell = (getWidth() - getPaddingLeft() - getPaddingRight() - gap * (WEEKS - 1)) / WEEKS;
        float radius = cell * 0.22f;
        for (int i = 0; i < WEEKS * 7; i++) {
            if (i >= firstFuture) continue;
            int week = i / 7, day = i % 7;
            float x = getPaddingLeft() + week * (cell + gap);
            float y = getPaddingTop() + day * (cell + gap);
            rect.set(x, y, x + cell, y + cell);
            paint.setColor(COLORS[Math.max(0, Math.min(4, levels[i]))]);
            canvas.drawRoundRect(rect, radius, radius, paint);
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        if (e.getAction() == MotionEvent.ACTION_DOWN) return true;
        if (e.getAction() == MotionEvent.ACTION_UP && listener != null && cell > 0) {
            int week = (int) ((e.getX() - getPaddingLeft()) / (cell + gap));
            int day = (int) ((e.getY() - getPaddingTop()) / (cell + gap));
            int index = week * 7 + day;
            if (week >= 0 && week < WEEKS && day >= 0 && day < 7 && index < firstFuture) {
                listener.onCell(index);
                performClick();
            }
            return true;
        }
        return super.onTouchEvent(e);
    }

    @Override
    public boolean performClick() {
        return super.performClick();
    }
}
