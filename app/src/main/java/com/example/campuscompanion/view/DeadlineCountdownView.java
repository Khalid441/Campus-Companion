package com.example.campuscompanion.view;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.View;

import com.google.android.material.color.MaterialColors;

/**
 * Custom View (drawn with Canvas): a ring that shrinks as a deadline gets closer,
 * with the remaining time in the middle. Green > 3 days, orange 1-3 days,
 * red < 1 day, dark red when overdue, grey with a tick when done.
 */
public class DeadlineCountdownView extends View {

    private static final long HOUR = 60L * 60 * 1000;
    private static final long DAY = 24 * HOUR;
    private static final long WINDOW_MS = 7 * DAY;   // a full ring = one week or more left

    private static final int GREEN = 0xFF43A047;
    private static final int ORANGE = 0xFFFB8C00;
    private static final int RED = 0xFFE53935;
    private static final int DARK_RED = 0xFFB71C1C;
    private static final int GREY = 0xFF9E9E9E;

    private final Paint trackPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint arcPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF oval = new RectF();

    private long dueAt;
    private boolean done;
    private float progress = 1f;
    private int color = GREEN;
    private String label = "";

    public DeadlineCountdownView(Context context) {
        super(context);
        init(context);
    }

    public DeadlineCountdownView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    public DeadlineCountdownView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context);
    }

    private void init(Context context) {
        float stroke = dp(5);

        trackPaint.setStyle(Paint.Style.STROKE);
        trackPaint.setStrokeWidth(stroke);
        trackPaint.setColor(0x33808080);

        arcPaint.setStyle(Paint.Style.STROKE);
        arcPaint.setStrokeWidth(stroke);
        arcPaint.setStrokeCap(Paint.Cap.ROUND);

        textPaint.setTextAlign(Paint.Align.CENTER);
        textPaint.setFakeBoldText(true);
        textPaint.setTextSize(TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_SP, 13, getResources().getDisplayMetrics()));
        textPaint.setColor(MaterialColors.getColor(context,
                com.google.android.material.R.attr.colorOnSurface, Color.BLACK));
    }

    /** Call whenever the deadline or its done state changes (or to refresh the clock). */
    public void setDeadline(long dueAtMillis, boolean isDone) {
        this.dueAt = dueAtMillis;
        this.done = isDone;
        refresh();
    }

    private void refresh() {
        long left = dueAt - System.currentTimeMillis();

        if (done) {
            label = "\u2713";
            color = GREY;
            progress = 1f;
        } else if (left <= 0) {
            label = "Late";
            color = DARK_RED;
            progress = 1f;
        } else {
            if (left >= DAY) label = (left / DAY) + "d";
            else if (left >= HOUR) label = (left / HOUR) + "h";
            else label = Math.max(1, left / 60_000L) + "m";

            if (left > 3 * DAY) color = GREEN;
            else if (left > DAY) color = ORANGE;
            else color = RED;

            progress = Math.max(0.04f, Math.min(1f, (float) left / WINDOW_MS));
        }
        setContentDescription(done ? "Done" : (left <= 0 ? "Overdue" : label + " left"));
        invalidate();
    }

    @Override
    protected void onMeasure(int widthSpec, int heightSpec) {
        int size = Math.round(dp(52));
        setMeasuredDimension(resolveSize(size, widthSpec), resolveSize(size, heightSpec));
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float pad = arcPaint.getStrokeWidth() / 2f + dp(1);
        oval.set(pad, pad, getWidth() - pad, getHeight() - pad);

        canvas.drawArc(oval, 0, 360, false, trackPaint);

        arcPaint.setColor(color);
        canvas.drawArc(oval, -90, 360f * progress, false, arcPaint);

        float cy = getHeight() / 2f - (textPaint.ascent() + textPaint.descent()) / 2f;
        canvas.drawText(label, getWidth() / 2f, cy, textPaint);
    }

    private float dp(float v) {
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, getResources().getDisplayMetrics());
    }
}
