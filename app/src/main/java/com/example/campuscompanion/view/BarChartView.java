package com.example.campuscompanion.view;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.View;

import androidx.core.graphics.ColorUtils;

import com.google.android.material.color.MaterialColors;

/** Custom View: simple bar chart with a label under each bar (drawn with Canvas). */
public class BarChartView extends View {

    private final Paint barPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint labelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint valuePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();

    private String[] labels = new String[0];
    private int[] values = new int[0];
    private int barColor = 0xFF5B3FD1;

    public BarChartView(Context context) {
        super(context);
        init(context);
    }

    public BarChartView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    public BarChartView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context);
    }

    private void init(Context context) {
        int onSurface = MaterialColors.getColor(context,
                com.google.android.material.R.attr.colorOnSurface, Color.BLACK);
        barColor = MaterialColors.getColor(context,
                com.google.android.material.R.attr.colorPrimary, barColor);

        labelPaint.setTextAlign(Paint.Align.CENTER);
        labelPaint.setTextSize(sp(11));
        labelPaint.setColor(ColorUtils.setAlphaComponent(onSurface, 170));

        valuePaint.setTextAlign(Paint.Align.CENTER);
        valuePaint.setTextSize(sp(12));
        valuePaint.setFakeBoldText(true);
        valuePaint.setColor(onSurface);
    }

    public void setData(String[] newLabels, int[] newValues) {
        labels = newLabels;
        values = newValues;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        int n = Math.min(labels.length, values.length);
        if (n == 0) return;

        float w = getWidth();
        float h = getHeight();
        float labelH = dp(24);
        float topPad = dp(20);
        float chartH = h - labelH - topPad;
        float baseline = topPad + chartH;

        int max = 1;
        for (int i = 0; i < n; i++) max = Math.max(max, values[i]);

        float slot = w / n;
        float barW = slot * 0.5f;

        for (int i = 0; i < n; i++) {
            float cx = slot * i + slot / 2f;
            float barH = values[i] == 0 ? dp(3) : values[i] / (float) max * chartH;

            barPaint.setColor(values[i] == 0 ? 0x33808080 : barColor);
            rect.set(cx - barW / 2f, baseline - barH, cx + barW / 2f, baseline);
            canvas.drawRoundRect(rect, dp(6), dp(6), barPaint);

            if (values[i] > 0) {
                canvas.drawText(String.valueOf(values[i]), cx, rect.top - dp(5), valuePaint);
            }
            canvas.drawText(labels[i], cx, h - dp(6), labelPaint);
        }
    }

    private float dp(float v) {
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, getResources().getDisplayMetrics());
    }

    private float sp(float v) {
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, v, getResources().getDisplayMetrics());
    }
}
