package com.example.campuscompanion.adapter;

import android.content.res.ColorStateList;
import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.example.campuscompanion.R;
import com.example.campuscompanion.data.local.AttendanceCourse;
import com.example.campuscompanion.databinding.ItemAttendanceCourseBinding;
import com.example.campuscompanion.util.AttendanceMath;

import java.util.Objects;

public class AttendanceAdapter extends ListAdapter<AttendanceAdapter.Row, AttendanceAdapter.VH> {

    /** A course together with its calculated numbers. */
    public static class Row {
        public final AttendanceCourse course;
        public final AttendanceMath.Result stats;
        public final String lastTopic;   // newest class note, may be null

        public Row(AttendanceCourse course, AttendanceMath.Result stats, String lastTopic) {
            this.course = course;
            this.stats = stats;
            this.lastTopic = lastTopic;
        }
    }

    public interface Listener {
        void onMark(AttendanceCourse course, int status);
        void onPickDate(AttendanceCourse course);
        void onOpenMenu(AttendanceCourse course);
        void onOpenTopics(AttendanceCourse course);
    }

    private static final DiffUtil.ItemCallback<Row> DIFF = new DiffUtil.ItemCallback<Row>() {
        @Override
        public boolean areItemsTheSame(@NonNull Row a, @NonNull Row b) {
            return a.course.id == b.course.id;
        }

        @Override
        public boolean areContentsTheSame(@NonNull Row a, @NonNull Row b) {
            return Objects.equals(a.course.name, b.course.name)
                    && Objects.equals(a.course.code, b.course.code)
                    && a.course.minPercent == b.course.minPercent
                    && a.course.totalPlanned == b.course.totalPlanned
                    && Objects.equals(a.lastTopic, b.lastTopic)
                    && a.stats.present == b.stats.present
                    && a.stats.absent == b.stats.absent
                    && a.stats.cancelled == b.stats.cancelled;
        }
    };

    private final Listener listener;

    public AttendanceAdapter(Listener listener) {
        super(DIFF);
        this.listener = listener;
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new VH(ItemAttendanceCourseBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull VH holder, int position) {
        holder.bind(getItem(position));
    }

    class VH extends RecyclerView.ViewHolder {
        private final ItemAttendanceCourseBinding b;

        VH(ItemAttendanceCourseBinding b) {
            super(b.getRoot());
            this.b = b;
        }

        void bind(Row row) {
            AttendanceCourse c = row.course;
            AttendanceMath.Result s = row.stats;

            b.tvName.setText(c.title());
            b.tvCounts.setText("Present " + s.present + "  •  Absent " + s.absent
                    + "  •  Cancelled " + s.cancelled + "\nMinimum " + c.minPercent + "%"
                    + (c.totalPlanned > 0 ? "  •  " + c.totalPlanned + " classes planned" : ""));
            b.tvAdvice.setText(s.advice);

            boolean hasTopic = row.lastTopic != null && !row.lastTopic.isEmpty();
            b.tvLastTopic.setText(hasTopic ? row.lastTopic : "No class note yet. Tap Present to add one.");
            b.boxLastTopic.setOnClickListener(v -> listener.onOpenTopics(c));

            int colorRes = s.level == AttendanceMath.LOW ? R.color.att_low
                    : s.level == AttendanceMath.CLOSE ? R.color.att_close : R.color.att_safe;
            int color = ContextCompat.getColor(b.getRoot().getContext(), colorRes);

            if (s.held == 0) {
                b.tvPercent.setText("--");
                b.ring.setProgressCompat(0, false);
            } else {
                b.tvPercent.setText(s.percent + "%");
                b.ring.setProgressCompat(s.percent, true);
            }
            b.ring.setContentDescription(s.held == 0
                    ? "No classes marked yet"
                    : "Attendance " + s.percent + " percent, your minimum is " + c.minPercent + " percent");
            b.ring.setIndicatorColor(color);
            b.tvPercent.setTextColor(s.held == 0 ? b.tvAdvice.getCurrentTextColor() : color);
            b.btnCancel.setStrokeColor(ColorStateList.valueOf(color));

            b.btnPresent.setOnClickListener(v -> listener.onMark(c, 0));
            b.btnAbsent.setOnClickListener(v -> listener.onMark(c, 1));
            b.btnCancel.setOnClickListener(v -> listener.onMark(c, 2));
            b.btnOtherDate.setOnClickListener(v -> listener.onPickDate(c));
            b.getRoot().setOnClickListener(v -> listener.onOpenMenu(c));
            b.getRoot().setOnLongClickListener(v -> {
                listener.onOpenMenu(c);
                return true;
            });
        }
    }
}
