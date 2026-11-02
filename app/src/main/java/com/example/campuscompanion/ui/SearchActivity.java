package com.example.campuscompanion.ui;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.TextWatcher;
import android.text.style.BackgroundColorSpan;
import android.text.style.StyleSpan;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.campuscompanion.MainActivity;
import com.example.campuscompanion.R;
import com.example.campuscompanion.auth.UserSession;
import com.example.campuscompanion.data.local.AppDatabase;
import com.example.campuscompanion.data.local.AttendanceCourse;
import com.example.campuscompanion.data.local.AttendanceRecord;
import com.example.campuscompanion.data.local.Deadline;
import com.example.campuscompanion.data.local.Exam;
import com.example.campuscompanion.data.local.RoutineEntry;
import com.example.campuscompanion.data.local.StudySession;
import com.example.campuscompanion.data.local.Topic;
import com.example.campuscompanion.databinding.ActivitySearchBinding;
import com.example.campuscompanion.databinding.ItemSearchHeaderBinding;
import com.example.campuscompanion.databinding.ItemSearchHitBinding;
import com.example.campuscompanion.model.Notice;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * One search box for the whole app. Everything is read once from the phone (notices from the
 * Firestore cache/server) and filtered in memory while you type, so it works offline.
 */
public class SearchActivity extends AppCompatActivity {

    private static final String PREFS = "search_prefs";
    private static final int MAX_RECENT = 6;

    /** One row of the list: either a section header or a result. */
    private static final class Row {
        static final int HEADER = 0, HIT = 1, RECENT = 2, CLEAR = 3;
        final int type;
        String icon = "";
        String title = "";
        String sub = "";
        Runnable open;

        Row(int type) { this.type = type; }
    }

    /** A searchable thing, plus where to go when it is tapped. */
    private static final class Item {
        final String section, icon, title, sub;
        final Intent target;

        Item(String section, String icon, String title, String sub, Intent target) {
            this.section = section;
            this.icon = icon;
            this.title = title == null ? "" : title;
            this.sub = sub == null ? "" : sub;
            this.target = target;
        }
    }

    private static final String[] ORDER = {
            "Deadlines", "Notices", "Courses", "Topics", "Class notes",
            "Exams", "Study plan", "Class routine"};

    private ActivitySearchBinding b;
    private final List<Item> items = new ArrayList<>();
    private final List<Row> rows = new ArrayList<>();
    private RowAdapter adapter;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private boolean loaded = false;
    private String query = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        b = ActivitySearchBinding.inflate(getLayoutInflater());
        setContentView(b.getRoot());

        adapter = new RowAdapter();
        b.rvResults.setLayoutManager(new LinearLayoutManager(this));
        b.rvResults.setAdapter(adapter);

        b.btnBack.setOnClickListener(v -> finish());
        b.btnClear.setOnClickListener(v -> b.etSearch.setText(""));
        b.etSearch.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int c, int d) { }
            @Override public void onTextChanged(CharSequence s, int a, int c, int d) { }
            @Override public void afterTextChanged(Editable s) {
                query = s.toString().trim();
                b.btnClear.setVisibility(query.isEmpty() ? View.GONE : View.VISIBLE);
                handler.removeCallbacksAndMessages(null);
                handler.postDelayed(SearchActivity.this::refresh, 200);
            }
        });
        b.etSearch.setOnEditorActionListener((v, action, e) -> {
            if (action == EditorInfo.IME_ACTION_SEARCH) {
                hideKeyboard();
                return true;
            }
            return false;
        });

        showRecents();
        loadData();
        b.etSearch.requestFocus();
    }

    // ---------------------------------------------------------------------
    // Loading
    // ---------------------------------------------------------------------

    private void loadData() {
        Context app = getApplicationContext();
        AppDatabase.executor.execute(() -> {
            AppDatabase db = AppDatabase.get(app);
            List<Item> found = new ArrayList<>();

            for (Deadline d : db.deadlineDao().getAllNow()) {
                String sub = (d.course == null || d.course.isEmpty() ? "" : d.course + "  •  ")
                        + com.example.campuscompanion.util.TimeUtil.friendly(d.dueAt)
                        + (d.done ? "  •  done" : "");
                found.add(new Item("Deadlines", "⏰", d.title,
                        join(sub, d.note), tab(R.id.nav_deadlines)));
            }

            Map<Integer, AttendanceCourse> courses = new HashMap<>();
            for (AttendanceCourse c : db.attendanceDao().getCoursesNow()) {
                courses.put(c.id, c);
                String code = c.code == null ? "" : c.code;
                found.add(new Item("Courses", "📊", c.name, code, tab(R.id.nav_attendance)));
            }

            for (Topic t : db.topicDao().getAllNow()) {
                AttendanceCourse c = courses.get(t.courseId);
                String where = c != null ? c.name : (t.subject == null ? "" : t.subject);
                found.add(new Item("Topics", "📝", t.title, where, topicTarget(app, t, c)));
            }

            for (AttendanceRecord r : db.attendanceDao().getRecordsNow()) {
                if (r.topic == null || r.topic.trim().isEmpty()) continue;
                AttendanceCourse c = courses.get(r.courseId);
                found.add(new Item("Class notes", "📓", r.topic,
                        c == null ? "" : c.name, topicTarget(app, null, c)));
            }

            for (Exam e : db.examDao().getAllNow()) {
                found.add(new Item("Exams", "🎯", e.subject,
                        join(examLabel(e.examDate), e.note),
                        new Intent(app, ExamsActivity.class)));
            }

            for (StudySession s : db.studyDao().getAllNow()) {
                found.add(new Item("Study plan", "📖", s.subject, s.topic, tab(R.id.nav_study)));
            }

            for (RoutineEntry r : db.routineDao().getAllNow()) {
                found.add(new Item("Class routine", "🏫", r.course,
                        join(r.room, r.teacher), new Intent(app, RoutineActivity.class)));
            }

            runOnUiThread(() -> {
                if (isFinishing() || isDestroyed()) return;
                items.addAll(found);
                loaded = true;
                refresh();
            });
        });

        loadNotices();
    }

    private void loadNotices() {
        String section = UserSession.section(this);
        if (section == null || section.isEmpty()) return;
        FirebaseFirestore.getInstance()
                .collection("sections").document(section).collection("notices")
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .limit(50)
                .get()
                .addOnSuccessListener(snap -> {
                    if (isFinishing() || isDestroyed()) return;
                    for (DocumentSnapshot d : snap.getDocuments()) {
                        Notice n = Notice.from(d);
                        items.add(new Item("Notices", "📢", n.message,
                                join(n.author, com.example.campuscompanion.util.TimeUtil.friendly(n.createdAt)),
                                tab(R.id.nav_notices)));
                    }
                    refresh();
                });
    }

    private Intent tab(int navId) {
        return MainActivity.tabIntent(this, navId);
    }

    private Intent topicTarget(Context c, Topic t, AttendanceCourse course) {
        if (course != null) {
            return new Intent(c, TopicsActivity.class)
                    .putExtra(TopicsActivity.EXTRA_COURSE_ID, course.id)
                    .putExtra(TopicsActivity.EXTRA_COURSE_NAME, course.name);
        }
        return new Intent(c, RevisionActivity.class);
    }

    private static String examLabel(long date) {
        int days = com.example.campuscompanion.util.ExamUtil.daysLeft(date);
        return days < 0 ? "passed" : com.example.campuscompanion.util.ExamUtil.label(days);
    }

    private static String join(String a, String b) {
        boolean ea = a == null || a.isEmpty(), eb = b == null || b.isEmpty();
        if (ea) return eb ? "" : b;
        return eb ? a : a + "  •  " + b;
    }

    // ---------------------------------------------------------------------
    // Searching
    // ---------------------------------------------------------------------

    private void refresh() {
        if (b == null) return;
        if (query.isEmpty()) {
            showRecents();
            return;
        }
        String[] words = query.toLowerCase(Locale.getDefault()).split("\\s+");
        rows.clear();
        int count = 0;
        for (String section : ORDER) {
            List<Item> hits = new ArrayList<>();
            for (Item it : items) {
                if (!it.section.equals(section)) continue;
                String hay = (it.title + " " + it.sub).toLowerCase(Locale.getDefault());
                boolean all = true;
                for (String w : words) if (!hay.contains(w)) { all = false; break; }
                if (all) hits.add(it);
            }
            if (hits.isEmpty()) continue;
            Row h = new Row(Row.HEADER);
            h.title = section + "  (" + hits.size() + ")";
            rows.add(h);
            for (Item it : hits) {
                Row r = new Row(Row.HIT);
                r.icon = it.icon;
                r.title = it.title;
                r.sub = it.sub;
                final Item item = it;
                r.open = () -> openItem(item);
                rows.add(r);
                count++;
            }
        }
        adapter.notifyDataSetChanged();
        if (count == 0) {
            b.tvEmpty.setVisibility(View.VISIBLE);
            b.tvEmpty.setText(loaded ? "Nothing found for \"" + query + "\"" : "Searching…");
        } else {
            b.tvEmpty.setVisibility(View.GONE);
        }
    }

    private void openItem(Item item) {
        saveRecent(query);
        hideKeyboard();
        startActivity(item.target);
        // Tab targets clear the stack up to Home; other screens open on top of search
    }

    /** Highlights every typed word inside a result. */
    private CharSequence highlight(String text) {
        if (query.isEmpty() || text.isEmpty()) return text;
        SpannableString sp = new SpannableString(text);
        String lower = text.toLowerCase(Locale.getDefault());
        for (String w : query.toLowerCase(Locale.getDefault()).split("\\s+")) {
            if (w.isEmpty()) continue;
            int from = 0;
            while (true) {
                int i = lower.indexOf(w, from);
                if (i < 0) break;
                sp.setSpan(new BackgroundColorSpan(Color.parseColor("#55FFC107")), i, i + w.length(),
                        Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                sp.setSpan(new StyleSpan(android.graphics.Typeface.BOLD), i, i + w.length(),
                        Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                from = i + w.length();
            }
        }
        return sp;
    }

    // ---------------------------------------------------------------------
    // Recent searches
    // ---------------------------------------------------------------------

    private SharedPreferences prefs() {
        return getSharedPreferences(PREFS, MODE_PRIVATE);
    }

    private String recentKey() {
        String uid = FirebaseAuth.getInstance().getUid();
        return "recent_" + (uid == null ? "" : uid);
    }

    private List<String> recents() {
        List<String> out = new ArrayList<>();
        String raw = prefs().getString(recentKey(), "");
        for (String s : raw.split("\n")) if (!s.trim().isEmpty()) out.add(s);
        return out;
    }

    private void saveRecent(String q) {
        if (q == null || q.trim().length() < 2) return;
        List<String> list = recents();
        list.remove(q);
        list.add(0, q);
        while (list.size() > MAX_RECENT) list.remove(list.size() - 1);
        prefs().edit().putString(recentKey(), android.text.TextUtils.join("\n", list)).apply();
    }

    private void showRecents() {
        rows.clear();
        List<String> list = recents();
        if (!list.isEmpty()) {
            Row h = new Row(Row.HEADER);
            h.title = "Recent searches";
            rows.add(h);
            for (String s : list) {
                Row r = new Row(Row.RECENT);
                r.icon = "🕘";
                r.title = s;
                final String q = s;
                r.open = () -> {
                    b.etSearch.setText(q);
                    b.etSearch.setSelection(q.length());
                };
                rows.add(r);
            }
            Row c = new Row(Row.CLEAR);
            c.title = "Clear recent searches";
            c.open = () -> {
                prefs().edit().remove(recentKey()).apply();
                showRecents();
            };
            rows.add(c);
            b.tvEmpty.setVisibility(View.GONE);
        } else {
            b.tvEmpty.setVisibility(View.VISIBLE);
            b.tvEmpty.setText("Search your deadlines, notices, courses, topics, class notes, exams and routine.");
        }
        adapter.notifyDataSetChanged();
    }

    private void hideKeyboard() {
        InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) imm.hideSoftInputFromWindow(b.etSearch.getWindowToken(), 0);
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        super.onDestroy();
    }

    // ---------------------------------------------------------------------
    // Adapter
    // ---------------------------------------------------------------------

    private final class RowAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
        @Override
        public int getItemViewType(int position) {
            return rows.get(position).type == Row.HEADER ? 0 : 1;
        }

        @Override
        public int getItemCount() {
            return rows.size();
        }

        @NonNull
        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            LayoutInflater inf = LayoutInflater.from(parent.getContext());
            if (viewType == 0) {
                return new HeaderVH(ItemSearchHeaderBinding.inflate(inf, parent, false));
            }
            return new HitVH(ItemSearchHitBinding.inflate(inf, parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
            Row r = rows.get(position);
            if (holder instanceof HeaderVH) {
                ((HeaderVH) holder).h.tvHeader.setText(r.title);
                return;
            }
            HitVH vh = (HitVH) holder;
            boolean plain = r.type != Row.HIT;
            vh.h.tvIcon.setText(r.icon);
            vh.h.tvIcon.setVisibility(r.type == Row.CLEAR ? View.GONE : View.VISIBLE);
            vh.h.tvTitle.setText(plain ? r.title : highlight(r.title));
            vh.h.tvSub.setText(plain ? "" : highlight(r.sub));
            vh.h.tvSub.setVisibility(plain || r.sub.isEmpty() ? View.GONE : View.VISIBLE);
            vh.h.hitRoot.setOnClickListener(v -> { if (r.open != null) r.open.run(); });
        }
    }

    private static final class HeaderVH extends RecyclerView.ViewHolder {
        final ItemSearchHeaderBinding h;
        HeaderVH(ItemSearchHeaderBinding h) { super(h.getRoot()); this.h = h; }
    }

    private static final class HitVH extends RecyclerView.ViewHolder {
        final ItemSearchHitBinding h;
        HitVH(ItemSearchHitBinding h) { super(h.getRoot()); this.h = h; }
    }
}
