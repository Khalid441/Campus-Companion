package com.example.campuscompanion.adapter;

import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.example.campuscompanion.data.local.StudySession;
import com.example.campuscompanion.databinding.ItemStudyBinding;
import com.example.campuscompanion.util.TimeUtil;

import java.util.Objects;

public class StudyAdapter extends ListAdapter<StudySession, StudyAdapter.VH> {

    public interface Listener {
        void onClick(StudySession s);

        void onToggle(StudySession s, boolean enabled);
    }

    private static final String[] SHORT = {"Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat"};

    private static final DiffUtil.ItemCallback<StudySession> DIFF =
            new DiffUtil.ItemCallback<StudySession>() {
                @Override
                public boolean areItemsTheSame(@NonNull StudySession a, @NonNull StudySession b) {
                    return a.id == b.id;
                }

                @Override
                public boolean areContentsTheSame(@NonNull StudySession a, @NonNull StudySession b) {
                    return Objects.equals(a.subject, b.subject) && Objects.equals(a.topic, b.topic)
                            && a.daysMask == b.daysMask && a.startMinutes == b.startMinutes
                            && a.endMinutes == b.endMinutes && a.reminderMinutes == b.reminderMinutes
                            && a.enabled == b.enabled;
                }
            };

    private final Listener listener;

    public StudyAdapter(Listener listener) {
        super(DIFF);
        this.listener = listener;
    }

    public static String daysLabel(int mask) {
        if (mask == 127) return "Every day";
        if (mask == 31) return "Sun \u2013 Thu";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 7; i++) {
            if ((mask & (1 << i)) != 0) {
                if (sb.length() > 0) sb.append(", ");
                sb.append(SHORT[i]);
            }
        }
        return sb.toString();
    }

    public static String reminderLabel(int minutes) {
        if (minutes < 0) return "No reminder";
        if (minutes == 0) return "Reminder at start";
        return "Reminder " + minutes + " min before";
    }

    public static String durationLabel(int minutes) {
        int h = minutes / 60;
        int m = minutes % 60;
        if (h == 0) return m + "m";
        return m == 0 ? h + "h" : h + "h " + m + "m";
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new VH(ItemStudyBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull VH holder, int position) {
        holder.bind(getItem(position));
    }

    class VH extends RecyclerView.ViewHolder {
        private final ItemStudyBinding b;

        VH(ItemStudyBinding b) {
            super(b.getRoot());
            this.b = b;
        }

        void bind(StudySession s) {
            b.tvSubject.setText(s.subject);
            b.tvTime.setText(TimeUtil.fmt(s.startMinutes) + " \u2013 " + TimeUtil.fmt(s.endMinutes)
                    + "  \u2022  " + durationLabel(s.endMinutes - s.startMinutes));
            b.tvDays.setText(daysLabel(s.daysMask));
            b.tvReminder.setText(reminderLabel(s.reminderMinutes));

            boolean hasTopic = s.topic != null && !s.topic.isEmpty();
            b.tvTopic.setVisibility(hasTopic ? android.view.View.VISIBLE : android.view.View.GONE);
            if (hasTopic) b.tvTopic.setText(s.topic);

            b.getRoot().setAlpha(s.enabled ? 1f : 0.5f);
            b.getRoot().setOnClickListener(v -> listener.onClick(s));

            b.swEnabled.setOnCheckedChangeListener(null);
            b.swEnabled.setChecked(s.enabled);
            b.swEnabled.setOnCheckedChangeListener((button, on) -> listener.onToggle(s, on));
        }
    }
}
