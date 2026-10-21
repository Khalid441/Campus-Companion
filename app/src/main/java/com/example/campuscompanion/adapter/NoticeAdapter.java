package com.example.campuscompanion.adapter;

import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.example.campuscompanion.databinding.ItemNoticeBinding;
import com.example.campuscompanion.model.Notice;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.Objects;

public class NoticeAdapter extends ListAdapter<Notice, NoticeAdapter.VH> {

    public interface Listener {
        void onLongPress(Notice n);
    }

    private static final SimpleDateFormat FMT =
            new SimpleDateFormat("EEE, dd MMM  hh:mm a", Locale.getDefault());

    private static final DiffUtil.ItemCallback<Notice> DIFF = new DiffUtil.ItemCallback<Notice>() {
        @Override
        public boolean areItemsTheSame(@NonNull Notice a, @NonNull Notice b) {
            return Objects.equals(a.id, b.id);
        }

        @Override
        public boolean areContentsTheSame(@NonNull Notice a, @NonNull Notice b) {
            return Objects.equals(a.message, b.message)
                    && Objects.equals(a.author, b.author)
                    && a.createdAt == b.createdAt;
        }
    };

    private final Listener listener;

    public NoticeAdapter(Listener listener) {
        super(DIFF);
        this.listener = listener;
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new VH(ItemNoticeBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull VH holder, int position) {
        holder.bind(getItem(position));
    }

    class VH extends RecyclerView.ViewHolder {
        private final ItemNoticeBinding b;

        VH(ItemNoticeBinding b) {
            super(b.getRoot());
            this.b = b;
        }

        void bind(Notice n) {
            b.tvMessage.setText(n.message);
            String who = (n.author == null || n.author.isEmpty()) ? "CR" : n.author;
            b.tvMeta.setText(who + "  \u2022  " + FMT.format(new Date(n.createdAt)));
            b.getRoot().setOnLongClickListener(v -> {
                listener.onLongPress(n);
                return true;
            });
        }
    }
}
