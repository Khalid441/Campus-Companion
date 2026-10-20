package com.example.campuscompanion.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.example.campuscompanion.data.local.RoutineEntry;
import com.example.campuscompanion.databinding.ItemRoutineBinding;
import com.example.campuscompanion.util.TimeUtil;

import java.util.Objects;

public class RoutineAdapter extends ListAdapter<RoutineEntry, RoutineAdapter.VH> {

    public interface Listener {
        void onLongPress(RoutineEntry e);
    }

    private static final DiffUtil.ItemCallback<RoutineEntry> DIFF = new DiffUtil.ItemCallback<RoutineEntry>() {
        @Override
        public boolean areItemsTheSame(@NonNull RoutineEntry a, @NonNull RoutineEntry b) {
            return a.id == b.id;
        }

        @Override
        public boolean areContentsTheSame(@NonNull RoutineEntry a, @NonNull RoutineEntry b) {
            return Objects.equals(a.course, b.course) && Objects.equals(a.room, b.room)
                    && Objects.equals(a.teacher, b.teacher) && a.dayOfWeek == b.dayOfWeek
                    && a.startMinutes == b.startMinutes && a.endMinutes == b.endMinutes;
        }
    };

    private final Listener listener;

    public RoutineAdapter(Listener listener) {
        super(DIFF);
        this.listener = listener;
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new VH(ItemRoutineBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull VH holder, int position) {
        holder.bind(getItem(position));
    }

    class VH extends RecyclerView.ViewHolder {
        private final ItemRoutineBinding b;

        VH(ItemRoutineBinding b) {
            super(b.getRoot());
            this.b = b;
        }

        void bind(RoutineEntry e) {
            b.tvCourse.setText(e.course);
            b.tvDay.setText(TimeUtil.dayName(e.dayOfWeek));
            b.tvTime.setText(TimeUtil.fmt(e.startMinutes) + " - " + TimeUtil.fmt(e.endMinutes));

            StringBuilder sb = new StringBuilder();
            if (e.room != null && !e.room.isEmpty()) sb.append("Room ").append(e.room);
            if (e.teacher != null && !e.teacher.isEmpty()) {
                if (sb.length() > 0) sb.append("  \u2022  ");
                sb.append(e.teacher);
            }
            b.tvRoomTeacher.setText(sb);
            b.tvRoomTeacher.setVisibility(sb.length() == 0 ? View.GONE : View.VISIBLE);

            b.getRoot().setOnLongClickListener(v -> {
                listener.onLongPress(e);
                return true;
            });
        }
    }
}
