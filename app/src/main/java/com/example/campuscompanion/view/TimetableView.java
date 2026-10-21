package com.example.campuscompanion.view;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.text.TextPaint;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.View;

import androidx.core.graphics.ColorUtils;

import com.example.campuscompanion.data.local.RoutineEntry;
import com.google.android.material.color.MaterialColors;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

/**
 * Custom View: weekly timetable grid (days across, hours down) drawn with Canvas.
 * Put it inside a ScrollView - its height grows with the number of hours shown.
 */
public class TimetableView extends View {

    private static final int[] PALETTE = {
            0xFF5B3FD1, 0xFF00897B, 0xFFE65100, 0xFFC2185B,
            0xFF1565C0, 0xFF6A1B9A, 0xFF2E7D32, 0xFFAD1457};
    private static final String[] DAY_SHORT = {"Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat"};

    private final Paint gridPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint nowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint hourText = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint headerText = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint courseText = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint roomText = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();

    private final float timeColW;
    private final float headerH;
    private final float hourH;
    private final int todayTint;

    private List<RoutineEntry> entries = new ArrayList<>();
    private int firstHour = 8;
    private int lastHour = 17;
    private int[] days = {1, 2, 3, 4, 5};   // Calendar.SUNDAY.. (1..7)

    public TimetableView(Context context) {
        this(context, null);
    }

    public TimetableView(Context context, AttributeSet attrs) {
        super(context, attrs);
        timeColW = dp(44);
        headerH = dp(32);
        hourH = dp(64);

        int onSurface = MaterialColors.getColor(context,
                com.google.android.material.R.attr.colorOnSurface, Color.BLACK);
        int primaryContainer = MaterialColors.getColor(context,
                com.google.android.material.R.attr.colorPrimaryContainer, Color.LTGRAY);
        todayTint = ColorUtils.setAlphaComponent(primaryContainer, 110);

        gridPaint.setColor(0x33808080);
        gridPaint.setStrokeWidth(dp(1));

        nowPaint.setColor(0xFFE53935);
        nowPaint.setStrokeWidth(dp(2));

        hourText.setTextSize(sp(10));
        hourText.setColor(ColorUtils.setAlphaComponent(onSurface, 160));

        headerText.setTextSize(sp(12));
        headerText.setFakeBoldText(true);
        headerText.setTextAlign(Paint.Align.CENTER);
        headerText.setColor(onSurface);

        courseText.setTextSize(sp(11));
        courseText.setFakeBoldText(true);
        courseText.setColor(Color.WHITE);

        roomText.setTextSize(sp(9.5f));
        roomText.setColor(0xDDFFFFFF);
    }

    public void setEntries(List<RoutineEntry> list) {
        entries = list == null ? new ArrayList<RoutineEntry>() : new ArrayList<>(list);

        int minStart = 8 * 60;
        int maxEnd = 17 * 60;
        boolean fri = false;
        boolean sat = false;
        for (RoutineEntry e : entries) {
            minStart = Math.min(minStart, e.startMinutes);
            maxEnd = Math.max(maxEnd, e.endMinutes);
            if (e.dayOfWeek == Calendar.FRIDAY) fri = true;
            if (e.dayOfWeek == Calendar.SATURDAY) sat = true;
        }
        firstHour = Math.max(0, minStart / 60);
        lastHour = Math.min(24, (maxEnd + 59) / 60);
        if (lastHour <= firstHour) lastHour = firstHour + 1;

        List<Integer> d = new ArrayList<>();
        for (int i = Calendar.SUNDAY; i <= Calendar.THURSDAY; i++) d.add(i);
        if (fri) d.add(Calendar.FRIDAY);
        if (sat) d.add(Calendar.SATURDAY);
        days = new int[d.size()];
        for (int i = 0; i < days.length; i++) days[i] = d.get(i);

        requestLayout();
        invalidate();
    }

    @Override
    protected void onMeasure(int widthSpec, int heightSpec) {
        int h = Math.round(headerH + (lastHour - firstHour) * hourH + dp(8));
        setMeasuredDimension(resolveSize(getSuggestedMinimumWidth(), widthSpec), h);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float w = getWidth();
        float colW = (w - timeColW) / days.length;
        float bottom = headerH + (lastHour - firstHour) * hourH;

        Calendar now = Calendar.getInstance();
        int today = now.get(Calendar.DAY_OF_WEEK);
        int nowMin = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE);

        // Day headers (today highlighted)
        for (int i = 0; i < days.length; i++) {
            float x = timeColW + i * colW;
            if (days[i] == today) {
                fillPaint.setColor(todayTint);
                canvas.drawRect(x, 0, x + colW, bottom, fillPaint);
            }
            canvas.drawText(DAY_SHORT[days[i] - 1], x + colW / 2f, headerH / 2f + dp(4), headerText);
        }

        // Hour lines + labels
        for (int h = firstHour; h <= lastHour; h++) {
            float y = headerH + (h - firstHour) * hourH;
            canvas.drawLine(timeColW, y, w, y, gridPaint);
            int h12 = h % 12 == 0 ? 12 : h % 12;
            String s = h12 + (h >= 12 && h < 24 ? " PM" : " AM");
            canvas.drawText(s, dp(4), y + dp(12), hourText);
        }

        // Day separators
        for (int i = 0; i <= days.length; i++) {
            float x = timeColW + i * colW;
            canvas.drawLine(x, headerH, x, bottom, gridPaint);
        }

        // Class blocks
        for (RoutineEntry e : entries) {
            int idx = indexOfDay(e.dayOfWeek);
            if (idx < 0) continue;

            float left = timeColW + idx * colW + dp(2);
            float right = timeColW + (idx + 1) * colW - dp(2);
            float top = headerH + (e.startMinutes - firstHour * 60) / 60f * hourH + dp(1);
            float bot = headerH + (e.endMinutes - firstHour * 60) / 60f * hourH - dp(1);
            if (bot - top < dp(14)) bot = top + dp(14);

            String key = e.course == null ? "" : e.course.toLowerCase();
            fillPaint.setColor(PALETTE[(key.hashCode() & 0x7fffffff) % PALETTE.length]);
            rect.set(left, top, right, bot);
            canvas.drawRoundRect(rect, dp(6), dp(6), fillPaint);

            float availW = right - left - dp(8);
            canvas.save();
            canvas.clipRect(rect);
            if (e.course != null) {
                CharSequence c = TextUtils.ellipsize(e.course, courseText, availW, TextUtils.TruncateAt.END);
                canvas.drawText(c, 0, c.length(), left + dp(4), top + dp(14), courseText);
            }
            if (e.room != null && !e.room.isEmpty() && bot - top >= dp(30)) {
                CharSequence r = TextUtils.ellipsize(e.room, roomText, availW, TextUtils.TruncateAt.END);
                canvas.drawText(r, 0, r.length(), left + dp(4), top + dp(27), roomText);
            }
            canvas.restore();
        }

        // Current time line (only in today's column)
        int ti = indexOfDay(today);
        if (ti >= 0 && nowMin >= firstHour * 60 && nowMin <= lastHour * 60) {
            float y = headerH + (nowMin - firstHour * 60) / 60f * hourH;
            canvas.drawLine(timeColW + ti * colW, y, timeColW + (ti + 1) * colW, y, nowPaint);
        }
    }

    private int indexOfDay(int day) {
        for (int i = 0; i < days.length; i++) if (days[i] == day) return i;
        return -1;
    }

    private float dp(float v) {
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, getResources().getDisplayMetrics());
    }

    private float sp(float v) {
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, v, getResources().getDisplayMetrics());
    }
}
