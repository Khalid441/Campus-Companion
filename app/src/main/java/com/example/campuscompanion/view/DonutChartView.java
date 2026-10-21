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

/** Custom View: donut chart with a text in the middle (drawn with Canvas). */
public class DonutChartView extends View {

    private final Paint arcPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint trackPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF oval = new RectF();

    private float[] values = new float[0];
    private int[] colors = new int[0];
    private String centerText = "";

    public DonutChartView(Context context) {
        super(context);
        init(context);
    }

    public DonutChartView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    public DonutChartView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context);
    }

    private void init(Context context) {
        arcPaint.setStyle(Paint.Style.STROKE);
        arcPaint.setStrokeWidth(dp(18));
        trackPaint.setStyle(Paint.Style.STROKE);
        trackPaint.setStrokeWidth(dp(18));
        trackPaint.setColor(0x33808080);

        textPaint.setTextAlign(Paint.Align.CENTER);
        textPaint.setFakeBoldText(true);
        textPaint.setTextSize(TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_SP, 26, getResources().getDisplayMetrics()));
        textPaint.setColor(MaterialColors.getColor(context,
                com.google.android.material.R.attr.colorOnSurface, Color.BLACK));
    }

    public void setData(float[] newValues, int[] newColors, String text) {
        values = newValues;
        colors = newColors;
        centerText = text == null ? "" : text;
        invalidate();
    }

    @Override
    protected void onMeasure(int widthSpec, int heightSpec) {
        int size = Math.round(dp(150));
        setMeasuredDimension(resolveSize(size, widthSpec), resolveSize(size, heightSpec));
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float cx = getWidth() / 2f;
        float cy = getHeight() / 2f;
        float half = Math.min(getWidth(), getHeight()) / 2f - arcPaint.getStrokeWidth() / 2f - dp(2);
        oval.set(cx - half, cy - half, cx + half, cy + half);

        canvas.drawArc(oval, 0, 360, false, trackPaint);

        float sum = 0;
        for (float v : values) sum += v;
        if (sum > 0) {
            float start = -90;
            for (int i = 0; i < values.length && i < colors.length; i++) {
                float sweep = values[i] / sum * 360f;
                if (sweep <= 0) continue;
                arcPaint.setColor(colors[i]);
                canvas.drawArc(oval, start, sweep, false, arcPaint);
                start += sweep;
            }
        }

        float ty = cy - (textPaint.ascent() + textPaint.descent()) / 2f;
        canvas.drawText(centerText, cx, ty, textPaint);
    }

    private float dp(float v) {
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, getResources().getDisplayMetrics());
    }
}
