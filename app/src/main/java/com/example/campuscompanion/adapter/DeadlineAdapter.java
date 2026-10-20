package com.example.campuscompanion.adapter;

import android.graphics.Paint;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.example.campuscompanion.R;
import com.example.campuscompanion.data.local.Deadline;
import com.example.campuscompanion.databinding.ItemDeadlineBinding;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.Objects;

public class DeadlineAdapter extends ListAdapter<Deadline, DeadlineAdapter.VH> {

    public interface Listener {
        void onToggleDone(Deadline d, boolean done);
        void onLongPress(Deadline d);
    }

    private static final SimpleDateFormat FMT =
            new SimpleDateFormat("EEE, dd MMM yyyy  hh:mm a", Locale.getDefault());

    private static final DiffUtil.ItemCallback<Deadline> DIFF = new DiffUtil.ItemCallback<Deadline>() {
        @Override
        public boolean areItemsTheSame(@NonNull Deadline a, @NonNull Deadline b) {
            return a.id == b.id;
        }

        @Override
        public boolean areContentsTheSame(@NonNull Deadline a, @NonNull Deadline b) {
            return Objects.equals(a.title, b.title)
                    && Objects.equals(a.course, b.course)
                    && Objects.equals(a.priority, b.priority)
                    && a.dueAt == b.dueAt
                    && a.done == b.done
                    && a.isShared == b.isShared;
        }
    };

    private final Listener listener;

    public DeadlineAdapter(Listener listener) {
        super(DIFF);
        this.listener = listener;
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemDeadlineBinding b = ItemDeadlineBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false);
        return new VH(b);
    }

    @Override
    public void onBindViewHolder(@NonNull VH holder, int position) {
        holder.bind(getItem(position));
    }

    class VH extends RecyclerView.ViewHolder {
        private final ItemDeadlineBinding b;

        VH(ItemDeadlineBinding b) {
            super(b.getRoot());
            this.b = b;
        }

        void bind(Deadline d) {
            b.title.setText(d.title);
            b.course.setText(d.course == null || d.course.isEmpty() ? "No course" : d.course);
            b.due.setText(FMT.format(new Date(d.dueAt)));
            b.sharedTag.setVisibility(d.isShared ? View.VISIBLE : View.GONE);

            int colorRes;
            if (Deadline.HIGH.equals(d.priority)) colorRes = R.color.priority_high;
            else if (Deadline.LOW.equals(d.priority)) colorRes = R.color.priority_low;
            else colorRes = R.color.priority_medium;
            b.priorityStrip.setBackgroundColor(ContextCompat.getColor(b.getRoot().getContext(), colorRes));

            boolean overdue = !d.done && d.dueAt < System.currentTimeMillis();
            b.due.setTextColor(overdue
                    ? ContextCompat.getColor(b.getRoot().getContext(), R.color.overdue)
                    : b.course.getCurrentTextColor());

            if (d.done) {
                b.title.setPaintFlags(b.title.getPaintFlags() | Paint.STRIKE_THRU_TEXT_FLAG);
            } else {
                b.title.setPaintFlags(b.title.getPaintFlags() & ~Paint.STRIKE_THRU_TEXT_FLAG);
            }

            b.checkDone.setOnCheckedChangeListener(null);
            b.checkDone.setChecked(d.done);
            b.checkDone.setOnCheckedChangeListener((btn, checked) -> listener.onToggleDone(d, checked));

            b.getRoot().setOnLongClickListener(v -> {
                listener.onLongPress(d);
                return true;
            });
        }
    }
}
