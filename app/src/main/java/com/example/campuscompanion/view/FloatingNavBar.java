package com.example.campuscompanion.view;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.drawable.GradientDrawable;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.TextView;

import androidx.annotation.Nullable;

import com.example.campuscompanion.R;
import com.google.android.material.color.MaterialColors;

import java.util.ArrayList;
import java.util.List;

/**
 * Simple bottom bar for the floating pill: icon with a rounded highlight when selected, and a small
 * label underneath. Built from plain views so its layout is fully predictable (no library insets).
 * Reads its tabs from @menu/bottom_nav.
 */
public class FloatingNavBar extends LinearLayout {

    public interface OnSelected {
        boolean onSelected(MenuItem item);
    }

    private static final class Tab {
        MenuItem item;
        View root;
        FrameLayout pill;
        ImageView icon;
        TextView label;
    }

    private final List<Tab> tabs = new ArrayList<>();
    private OnSelected listener;
    private int selectedId = 0;

    public FloatingNavBar(Context c) { this(c, null); }

    public FloatingNavBar(Context c, @Nullable AttributeSet a) {
        super(c, a);
        setOrientation(HORIZONTAL);
        setGravity(Gravity.CENTER_VERTICAL);
        int pad = dp(6);
        setPadding(pad, dp(8), pad, dp(8));
        build();
    }

    private int dp(int v) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, getResources().getDisplayMetrics());
    }

    private void build() {
        Menu menu = new PopupMenu(getContext(), this).getMenu();
        new android.view.MenuInflater(getContext()).inflate(R.menu.bottom_nav, menu);

        for (int i = 0; i < menu.size(); i++) {
            final MenuItem item = menu.getItem(i);
            final Tab t = new Tab();
            t.item = item;

            LinearLayout col = new LinearLayout(getContext());
            col.setOrientation(VERTICAL);
            col.setGravity(Gravity.CENTER_HORIZONTAL);
            col.setClickable(true);
            col.setFocusable(true);
            col.setContentDescription(item.getTitle());
            TypedValue ripple = new TypedValue();
            getContext().getTheme().resolveAttribute(android.R.attr.selectableItemBackgroundBorderless, ripple, true);
            col.setBackgroundResource(ripple.resourceId);

            FrameLayout pill = new FrameLayout(getContext());
            GradientDrawable bg = new GradientDrawable();
            bg.setShape(GradientDrawable.RECTANGLE);
            bg.setCornerRadius(dp(16));
            bg.setColor(getContext().getColor(R.color.nav_indicator));
            pill.setBackground(bg);
            pill.getBackground().setAlpha(0);
            ImageView icon = new ImageView(getContext());
            icon.setImageDrawable(item.getIcon());
            icon.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
            FrameLayout.LayoutParams ip = new FrameLayout.LayoutParams(dp(24), dp(24), Gravity.CENTER);
            pill.addView(icon, ip);
            col.addView(pill, new LinearLayout.LayoutParams(dp(56), dp(30)));

            TextView label = new TextView(getContext());
            label.setText(item.getTitle());
            label.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
            label.setSingleLine(true);
            label.setGravity(Gravity.CENTER);
            label.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
            lp.topMargin = dp(2);
            col.addView(label, lp);

            t.root = col;
            t.pill = pill;
            t.icon = icon;
            t.label = label;
            tabs.add(t);
            addView(col, new LinearLayout.LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f));
            col.setOnClickListener(v -> select(item.getItemId(), true));
        }
        if (!tabs.isEmpty()) {
            selectedId = tabs.get(0).item.getItemId();
            paint();
        }
    }

    public void setOnItemSelectedListener(OnSelected l) {
        listener = l;
    }

    public int getSelectedItemId() {
        return selectedId;
    }

    public void setSelectedItemId(int id) {
        select(id, true);
    }

    private void select(int id, boolean notify) {
        Tab target = null;
        for (Tab t : tabs) if (t.item.getItemId() == id) target = t;
        if (target == null) return;
        boolean changed = id != selectedId;
        selectedId = id;
        paint();
        if (changed && notify && listener != null) listener.onSelected(target.item);
    }

    private void paint() {
        int on = MaterialColors.getColor(this, com.google.android.material.R.attr.colorOnSurface);
        int off = MaterialColors.getColor(this, com.google.android.material.R.attr.colorOnSurfaceVariant);
        for (Tab t : tabs) {
            boolean sel = t.item.getItemId() == selectedId;
            t.pill.getBackground().setAlpha(sel ? 255 : 0);
            t.icon.setImageTintList(ColorStateList.valueOf(sel ? on : off));
            t.label.setTextColor(sel ? on : off);
            t.label.setTypeface(null, sel ? android.graphics.Typeface.BOLD : android.graphics.Typeface.NORMAL);
            t.root.setSelected(sel);
        }
    }
}
