package com.example.campuscompanion.ui;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;

import androidx.appcompat.app.AppCompatActivity;

import com.example.campuscompanion.data.local.AppDatabase;
import com.example.campuscompanion.data.local.Topic;
import com.example.campuscompanion.data.local.TopicDao;
import com.example.campuscompanion.databinding.ActivityTopicsBinding;
import com.example.campuscompanion.databinding.DialogTopicBinding;
import com.example.campuscompanion.databinding.ItemTopicBinding;
import com.example.campuscompanion.util.DateUtil;
import com.example.campuscompanion.util.RevisionPlan;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/** Topic checklist of one course: planned, taught, and how many times revised. */
public class TopicsActivity extends AppCompatActivity {

    public static final String EXTRA_COURSE_ID = "course_id";
    public static final String EXTRA_COURSE_NAME = "course_name";

    private static final SimpleDateFormat DAY = new SimpleDateFormat("dd MMM", Locale.getDefault());

    private ActivityTopicsBinding b;
    private TopicDao dao;
    private int courseId;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        b = ActivityTopicsBinding.inflate(getLayoutInflater());
        setContentView(b.getRoot());

        courseId = getIntent().getIntExtra(EXTRA_COURSE_ID, -1);
        String name = getIntent().getStringExtra(EXTRA_COURSE_NAME);
        b.tvTopicsTitle.setText(name == null ? "Topics" : name);

        dao = AppDatabase.get(this).topicDao();
        dao.getForCourse(courseId).observe(this, this::render);
        b.fabAdd.setOnClickListener(v -> showAddDialog());
    }

    private void render(List<Topic> topics) {
        b.list.removeAllViews();
        b.emptyText.setVisibility(topics.isEmpty() ? View.VISIBLE : View.GONE);

        int taught = 0, revisedAll = 0;
        long now = System.currentTimeMillis();
        for (Topic t : topics) {
            if (t.taught) taught++;
            if (t.taught && t.revisedCount >= RevisionPlan.STAGES) revisedAll++;
            b.list.addView(row(t, now));
        }
        b.tvTopicsSub.setText(topics.isEmpty() ? "Nothing here yet"
                : taught + " of " + topics.size() + " taught  •  " + revisedAll + " fully revised");
    }

    private View row(Topic t, long now) {
        ItemTopicBinding r = ItemTopicBinding.inflate(LayoutInflater.from(this), b.list, false);
        r.tvTopicTitle.setText(t.title);

        r.cbTaught.setChecked(t.taught);
        r.cbTaught.setOnClickListener(v -> toggleTaught(t, r.cbTaught.isChecked()));

        if (!t.taught) {
            r.tvTopicStatus.setText("Not taught yet");
        } else {
            StringBuilder sb = new StringBuilder("Taught ").append(DAY.format(new Date(t.taughtAt)))
                    .append("  •  Revised ").append(t.revisedCount).append("/").append(RevisionPlan.STAGES);
            if (RevisionPlan.isActive(t)) {
                sb.append(RevisionPlan.isDue(t, now) ? "  •  Revise today"
                        : "  •  Next " + DAY.format(new Date(t.nextRevisionAt)));
            } else if (!t.remind && t.revisedCount < RevisionPlan.STAGES) {
                sb.append("  •  Reminders off");
            }
            r.tvTopicStatus.setText(sb);
        }

        r.btnRevised.setVisibility(RevisionPlan.isDue(t, now) ? View.VISIBLE : View.GONE);
        r.btnRevised.setOnClickListener(v -> AppDatabase.executor.execute(() -> {
            Topic u = t.copy();
            RevisionPlan.markRevised(u, System.currentTimeMillis());
            dao.update(u);
            com.example.campuscompanion.util.StudyStats.recordRevision(getApplicationContext());
        }));

        r.getRoot().setOnLongClickListener(v -> {
            confirmDelete(t);
            return true;
        });
        return r.getRoot();
    }

    private void toggleTaught(Topic t, boolean nowTaught) {
        AppDatabase.executor.execute(() -> {
            Topic u = t.copy();
            long now = System.currentTimeMillis();
            if (nowTaught) {
                RevisionPlan.startRevision(u, DateUtil.startOfDay(now), true, now);
            } else {
                u.taught = false;
                u.revisedCount = 0;
                u.nextRevisionAt = 0;
            }
            dao.update(u);
        });
    }

    private void confirmDelete(Topic t) {
        new MaterialAlertDialogBuilder(this)
                .setTitle("Delete this topic?")
                .setMessage(t.title)
                .setPositiveButton("Delete", (d, w) -> AppDatabase.executor.execute(() -> dao.delete(t)))
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void showAddDialog() {
        DialogTopicBinding d = DialogTopicBinding.inflate(getLayoutInflater());
        androidx.appcompat.app.AlertDialog dialog = new MaterialAlertDialogBuilder(this)
                .setTitle("Add topic")
                .setView(d.getRoot())
                .setPositiveButton("Add", null)
                .setNegativeButton("Cancel", null)
                .create();
        dialog.show();

        dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String title = d.etTopicTitle.getText() == null ? "" : d.etTopicTitle.getText().toString().trim();
            if (title.isEmpty()) {
                d.tilTopic.setError("Enter the topic");
                return;
            }
            boolean taught = d.cbAlreadyTaught.isChecked();
            AppDatabase.executor.execute(() -> {
                if (dao.findByTitle(courseId, title) != null) return;   // already in the list
                Topic t = new Topic();
                t.courseId = courseId;
                t.title = title;
                long now = System.currentTimeMillis();
                if (taught) RevisionPlan.startRevision(t, DateUtil.startOfDay(now), true, now);
                dao.insert(t);
            });
            dialog.dismiss();
        });
    }
}
