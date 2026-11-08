package com.example.campuscompanion.ui;

import android.Manifest;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import com.example.campuscompanion.MainActivity;
import com.example.campuscompanion.R;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.location.Address;
import android.location.Geocoder;
import android.location.LocationManager;
import android.os.Build;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.core.content.ContextCompat;

import com.example.campuscompanion.auth.UserSession;
import com.example.campuscompanion.data.local.AppDatabase;
import com.example.campuscompanion.data.local.AttendanceCourse;
import com.example.campuscompanion.data.local.AttendanceDao;
import com.example.campuscompanion.data.local.AttendanceRecord;
import com.example.campuscompanion.data.local.Deadline;
import com.example.campuscompanion.data.local.RoutineEntry;
import com.example.campuscompanion.data.local.StudySession;
import com.example.campuscompanion.data.local.Topic;
import com.example.campuscompanion.data.remote.WeatherClient;
import com.example.campuscompanion.data.remote.WeatherResponse;
import com.example.campuscompanion.databinding.ActivityMainBinding;
import com.example.campuscompanion.databinding.ItemTileBinding;
import com.example.campuscompanion.model.Notice;
import com.example.campuscompanion.notification.NotificationHelper;
import com.example.campuscompanion.reminder.ReminderScheduler;
import com.example.campuscompanion.reminder.RevisionScheduler;
import com.example.campuscompanion.reminder.StudyReminderScheduler;
import com.example.campuscompanion.sync.NoticeManager;
import com.example.campuscompanion.sync.SyncManager;
import com.example.campuscompanion.ui.AddDeadlineActivity;
import com.example.campuscompanion.ui.AttendanceActivity;
import com.example.campuscompanion.ui.CalendarActivity;
import com.example.campuscompanion.ui.DayActivity;
import com.example.campuscompanion.ui.DeadlinesActivity;
import com.example.campuscompanion.ui.ExamsActivity;
import com.example.campuscompanion.ui.LoginActivity;
import com.example.campuscompanion.ui.NoticesActivity;
import com.example.campuscompanion.ui.RecapActivity;
import com.example.campuscompanion.ui.RevisionActivity;
import com.example.campuscompanion.ui.RoutineActivity;
import com.example.campuscompanion.ui.SettingsActivity;
import com.example.campuscompanion.ui.StatsActivity;
import com.example.campuscompanion.ui.StudyActivity;
import com.example.campuscompanion.ui.TimetableActivity;
import com.example.campuscompanion.util.AttendanceMath;
import com.example.campuscompanion.util.AttendanceStats;
import com.example.campuscompanion.util.DateUtil;
import com.example.campuscompanion.util.RevisionPlan;
import com.example.campuscompanion.util.TimeUtil;
import com.example.campuscompanion.widget.NextDeadlineWidget;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.Priority;
import com.google.android.gms.tasks.CancellationTokenSource;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class HomeFragment extends Fragment {

    private ActivityMainBinding b;
    private FusedLocationProviderClient fusedClient;
    private Call<WeatherResponse> currentCall;
    private List<AttendanceCourse> homeCourses = new ArrayList<>();
    private List<AttendanceRecord> homeRecords = new ArrayList<>();
    private List<RoutineEntry> todayClasses = new ArrayList<>();
    private List<com.example.campuscompanion.data.local.Exam> homeExams = new ArrayList<>();
    private Notice shownNotice;
    private java.util.List<String> tips = new ArrayList<>();
    private int tipIndex = 0;

    // Location permission (fine + coarse together)
    private final ActivityResultLauncher<String[]> locationPermission =
            registerForActivityResult(new ActivityResultContracts.RequestMultiplePermissions(), result -> {
                if (hasLocationPermission()) {
                    loadWeather();
                } else {
                    loadWeatherDefaultCity();
                }
            });

    // Voice input: "Assignment 3 due Friday 5 pm" opens the deadline form already filled in
    private final ActivityResultLauncher<Intent> voiceLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
                if (result.getResultCode() != android.app.Activity.RESULT_OK || result.getData() == null) return;
                java.util.ArrayList<String> heard =
                        result.getData().getStringArrayListExtra(android.speech.RecognizerIntent.EXTRA_RESULTS);
                if (heard == null || heard.isEmpty() || !isAdded()) return;
                long now = System.currentTimeMillis();
                String text = heard.get(0);
                com.example.campuscompanion.util.NaturalDate.Result parsed =
                        com.example.campuscompanion.util.NaturalDate.parse(text, now);
                for (String alt : heard) {                      // prefer an alternative that has a date
                    com.example.campuscompanion.util.NaturalDate.Result p =
                            com.example.campuscompanion.util.NaturalDate.parse(alt, now);
                    if (p.hasDate) { text = alt; parsed = p; break; }
                }
                Toast.makeText(requireContext(), "Heard: " + text, Toast.LENGTH_LONG).show();
                Intent i = new Intent(requireContext(), AddDeadlineActivity.class)
                        .putExtra(AddDeadlineActivity.EXTRA_PREFILL_TITLE,
                                parsed.title.isEmpty() ? text : parsed.title)
                        .putExtra(AddDeadlineActivity.EXTRA_PREFILL_DUE, parsed.millis);
                startActivity(i);
            });

    private void startVoice() {
        Intent i = new Intent(android.speech.RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                .putExtra(android.speech.RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                        android.speech.RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                .putExtra(android.speech.RecognizerIntent.EXTRA_PROMPT, "Say: Assignment 3 due Friday 5 pm")
                .putExtra(android.speech.RecognizerIntent.EXTRA_MAX_RESULTS, 3);
        try {
            voiceLauncher.launch(i);
        } catch (android.content.ActivityNotFoundException e) {
            Toast.makeText(requireContext(), "Voice input is not available on this phone", Toast.LENGTH_LONG).show();
        }
    }

    private MainActivity host() {
        return (MainActivity) requireActivity();
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        b = ActivityMainBinding.inflate(inflater, container, false);
        return b.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        Context ctx = requireContext();
        fusedClient = LocationServices.getFusedLocationProviderClient(ctx);

        // Ask for location once when the screen opens
        if (!hasLocationPermission()) {
            locationPermission.launch(new String[]{
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
            });
        }

        String name = UserSession.name(ctx);
        int hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
        String hello = hour < 12 ? "Good morning" : hour < 17 ? "Good afternoon" : "Good evening";
        b.tvGreeting.setText(hello + (name.isEmpty() ? "" : ",\n" + name));
        b.tvSection.setText(UserSession.label(ctx));
        b.tvRole.setVisibility(UserSession.isCr(ctx) ? View.VISIBLE : View.GONE);

        // Notices, study and attendance are tabs: switch inside the same screen (no new window)
        NoticeManager.latest.observe(getViewLifecycleOwner(), this::renderNotice);
        b.cardNotice.setOnClickListener(v -> host().selectTab(R.id.nav_notices));

        b.cardTip.setOnClickListener(v -> {
            if (tips.size() > 1) {
                tipIndex = (tipIndex + 1) % tips.size();
                showTip();
            }
        });
        b.cardLevel.setOnClickListener(v -> startActivity(new Intent(ctx, AchievementsActivity.class)));
        b.cardPoll.setOnClickListener(v -> startActivity(new Intent(ctx, PollsActivity.class)));
        b.btnVoice.setOnClickListener(v -> startVoice());
        b.btnSearch.setOnClickListener(v -> startActivity(new Intent(ctx, SearchActivity.class)));
        b.btnSettings.setOnClickListener(v -> startActivity(new Intent(ctx, SettingsActivity.class)));
        b.cardGlance.setOnClickListener(v -> startActivity(new Intent(ctx, DayActivity.class)));
        b.btnNoticeAck.setOnClickListener(v -> ackNotice());

        b.btnPost.setVisibility(UserSession.isCr(ctx) ? View.VISIBLE : View.GONE);
        b.btnPost.setOnClickListener(v -> startActivity(
                new Intent(ctx, AddDeadlineActivity.class)
                        .putExtra(AddDeadlineActivity.EXTRA_POST_TO_SECTION, true)));

        b.cardStudy.setOnClickListener(v -> host().selectTab(R.id.nav_study));
        b.tvRevise.setOnClickListener(v -> startActivity(new Intent(ctx, RevisionActivity.class)));
        b.tvStudyGoal.setOnClickListener(v -> GoalDialog.show(ctx, this::renderGoal));
        b.tvWeather.setOnClickListener(v -> {
            if (hasLocationPermission()) loadWeather(); else loadWeatherDefaultCity();
        });
        b.tvNextExam.setOnClickListener(v -> startActivity(new Intent(ctx, ExamsActivity.class)));
        buildTiles();

        showGlance();
        showTodaysStudy();
        showRevisionCount();
        showAttendance();
        if (savedInstanceState == null) animateCards();
    }

    /** Coming back to Home from another tab: refresh the parts that change over time. */
    @Override
    public void onHiddenChanged(boolean hidden) {
        super.onHiddenChanged(hidden);
        if (!hidden && b != null) {
            renderNotice(NoticeManager.latest.getValue());
            renderNextClass();
            renderGoal();
            refreshTips();
        }
    }

    /** Level card on Home; also announces new badges and keeps the (optional) leaderboard up to date. */
    private void refreshLevel() {
        Context app = requireContext().getApplicationContext();
        AppDatabase.executor.execute(() -> {
            com.example.campuscompanion.util.Gamification.Snapshot s =
                    com.example.campuscompanion.util.Gamification.compute(app);
            com.example.campuscompanion.util.Leaderboard.uploadIfOn(app, s);
            FragmentActivity act = getActivity();
            if (act == null) return;
            act.runOnUiThread(() -> {
                if (!isAdded() || getView() == null) return;
                b.tvLevelHome.setText("\u2B50 Level " + s.level + "  \u2022  "
                        + com.example.campuscompanion.util.LevelMath.title(s.level));
                int span = Math.max(1, s.xpNextLevel - s.xpThisLevel);
                b.levelBarHome.setProgress(Math.min(100, (s.xp - s.xpThisLevel) * 100 / span));
                b.tvLevelDetail.setText(s.xp + " XP  \u2022  " + (s.xpNextLevel - s.xp)
                        + " to level " + (s.level + 1) + "  \u2022  " + s.unlocked.size() + " badges  \u203A");
                for (com.example.campuscompanion.util.Gamification.Badge nb : s.newlyUnlocked) {
                    Toast.makeText(requireContext(), nb.emoji + " New badge: " + nb.name, Toast.LENGTH_LONG).show();
                }
            });
        });
    }

    /** Shows the newest open poll you have not voted on yet. */
    @SuppressWarnings("unchecked")
    private void refreshPoll() {
        String section = UserSession.section(requireContext());
        String uid = FirebaseAuth.getInstance().getUid();
        if (section == null || uid == null) {
            b.cardPoll.setVisibility(View.GONE);
            return;
        }
        com.google.firebase.firestore.FirebaseFirestore.getInstance()
                .collection("sections").document(section).collection("polls")
                .orderBy("createdAt", com.google.firebase.firestore.Query.Direction.DESCENDING)
                .limit(5).get()
                .addOnSuccessListener(snap -> {
                    if (!isAdded() || getView() == null) return;
                    for (com.google.firebase.firestore.DocumentSnapshot d : snap.getDocuments()) {
                        if (Boolean.TRUE.equals(d.getBoolean("closed"))) continue;
                        java.util.Map<String, Object> votes = (java.util.Map<String, Object>) d.get("votes");
                        if (votes != null && votes.containsKey(uid)) continue;
                        b.tvPollHome.setText(d.getString("question"));
                        b.cardPoll.setVisibility(View.VISIBLE);
                        return;
                    }
                    b.cardPoll.setVisibility(View.GONE);
                })
                .addOnFailureListener(e -> {
                    if (isAdded() && getView() != null) b.cardPoll.setVisibility(View.GONE);
                });
    }

    /** Builds the suggestion list off the main thread and shows the first one. */
    private void refreshTips() {
        Context app = requireContext().getApplicationContext();
        AppDatabase.executor.execute(() -> {
            java.util.List<String> built = com.example.campuscompanion.util.SmartTips.build(app);
            FragmentActivity act = getActivity();
            if (act == null) return;
            act.runOnUiThread(() -> {
                if (!isAdded() || getView() == null) return;
                tips = built;
                if (tipIndex >= tips.size()) tipIndex = 0;
                showTip();
            });
        });
    }

    private void showTip() {
        if (b == null) return;
        if (tips.isEmpty()) {
            b.cardTip.setVisibility(View.GONE);
            return;
        }
        b.cardTip.setVisibility(View.VISIBLE);
        b.tvTip.setText(tips.get(tipIndex));
        b.tvTipMore.setText(tips.size() > 1 ? "Tap for next tip  (" + (tipIndex + 1) + " of " + tips.size() + ")" : "");
        b.tvTipMore.setVisibility(tips.size() > 1 ? View.VISIBLE : View.GONE);
    }

    @Override
    public void onStart() {
        super.onStart();
        startLive();
    }

    @Override
    public void onStop() {
        super.onStop();
        stopLive();
    }

    // ---- Realtime Database: presence + live class status ----
    private Runnable stopPresence, stopStatus;

    private void startLive() {
        Context ctx = getContext();
        if (ctx == null || b == null) return;
        stopLive();
        if (!com.example.campuscompanion.util.LiveClass.available(ctx)) {
            b.cardLive.setVisibility(View.GONE);
            return;
        }
        b.cardLive.setVisibility(View.VISIBLE);
        boolean cr = UserSession.isCr(ctx);
        b.liveActions.setVisibility(cr ? View.VISIBLE : View.GONE);
        b.btnLivePost.setOnClickListener(v -> postLiveDialog());
        b.btnLiveClear.setOnClickListener(v -> com.example.campuscompanion.util.LiveClass.post(
                requireContext(), com.example.campuscompanion.util.LiveClass.CLEAR, "", () -> { }, () ->
                        Toast.makeText(requireContext(), "Could not clear. Check your connection.", Toast.LENGTH_SHORT).show()));

        stopPresence = com.example.campuscompanion.util.LiveClass.watchPresence(ctx, n -> {
            if (!isAdded() || getView() == null) return;
            b.tvOnline.setText("\uD83D\uDFE2 Live class  \u2022  " + n + (n == 1 ? " classmate online" : " classmates online"));
        });
        stopStatus = com.example.campuscompanion.util.LiveClass.watchStatus(ctx, st -> {
            if (!isAdded() || getView() == null) return;
            if (st == null || !st.active()) {
                b.tvLiveStatus.setVisibility(View.GONE);
                b.tvLiveMeta.setVisibility(View.GONE);
                b.tvLiveNone.setVisibility(View.VISIBLE);
                return;
            }
            b.tvLiveNone.setVisibility(View.GONE);
            b.tvLiveStatus.setVisibility(View.VISIBLE);
            b.tvLiveStatus.setText(com.example.campuscompanion.util.LiveClass.icon(st.type) + " "
                    + com.example.campuscompanion.util.LiveClass.label(st.type)
                    + (st.message.isEmpty() ? "" : ": " + st.message));
            b.tvLiveMeta.setVisibility(View.VISIBLE);
            b.tvLiveMeta.setText((st.byName.isEmpty() ? "CR" : st.byName) + "  \u2022  "
                    + com.example.campuscompanion.util.LiveClass.ago(st.at));
        });
    }

    private void stopLive() {
        if (stopPresence != null) { stopPresence.run(); stopPresence = null; }
        if (stopStatus != null) { stopStatus.run(); stopStatus = null; }
    }

    private void postLiveDialog() {
        Context ctx = requireContext();
        final String[] types = {com.example.campuscompanion.util.LiveClass.CANCELLED,
                com.example.campuscompanion.util.LiveClass.ROOM,
                com.example.campuscompanion.util.LiveClass.LATE,
                com.example.campuscompanion.util.LiveClass.NOTE};
        final String[] names = {"\u274C Class cancelled", "\uD83D\uDEAA Room changed", "\u23F0 Running late", "\uD83D\uDCE2 Announcement"};
        final int[] chosen = {0};
        final android.widget.EditText input = new android.widget.EditText(ctx);
        input.setHint("Details, e.g. CSE 2209 now in Room B-105");
        input.setSingleLine(true);
        android.widget.LinearLayout box = new android.widget.LinearLayout(ctx);
        int pad = (int) (20 * getResources().getDisplayMetrics().density);
        box.setPadding(pad, 0, pad, 0);
        box.addView(input, new android.widget.LinearLayout.LayoutParams(
                android.widget.LinearLayout.LayoutParams.MATCH_PARENT, android.widget.LinearLayout.LayoutParams.WRAP_CONTENT));
        new com.google.android.material.dialog.MaterialAlertDialogBuilder(ctx)
                .setTitle("Post a live update")
                .setSingleChoiceItems(names, 0, (d, which) -> chosen[0] = which)
                .setView(box)
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Post to class", (d, wch) -> com.example.campuscompanion.util.LiveClass.post(
                        ctx, types[chosen[0]], input.getText().toString().trim(),
                        () -> { },
                        () -> Toast.makeText(ctx, "Could not post. Check your connection.", Toast.LENGTH_SHORT).show()))
                .show();
    }

    @Override
    public void onResume() {
        super.onResume();
        if (b == null) return;

        renderNotice(NoticeManager.latest.getValue());
        renderNextClass();
        renderGoal();
        refreshTips();
        refreshPoll();
        refreshLevel();
        NextDeadlineWidget.refresh(requireContext());

        // Only fetch here if permission is already granted; otherwise the
        // permission dialog result (or the fallback) handles it.
        if (hasLocationPermission()) {
            loadWeather();
        } else {
            loadWeatherDefaultCity();
        }
    }

    // ---------------------------------------------------------------------
    // Notices
    // ---------------------------------------------------------------------

    private void renderNotice(Notice n) {
        if (b == null) return;
        shownNotice = n;
        if (n == null) {
            b.tvNotice.setText("No notices yet");
            b.btnNoticeAck.setVisibility(View.GONE);
            return;
        }
        boolean unread = n.createdAt > NoticeManager.lastSeen(requireContext());
        String who = (n.author == null || n.author.isEmpty()) ? "CR" : n.author;
        b.tvNotice.setText((unread ? "\uD83D\uDD34 " : "") + n.message + "\n\u2014 " + who);

        String uid = FirebaseAuth.getInstance().getUid();
        boolean acked = uid != null && n.ackedBy.contains(uid);
        b.btnNoticeAck.setVisibility(uid == null || n.id == null ? View.GONE : View.VISIBLE);
        b.btnNoticeAck.setText((acked ? "\u2713 Got it" : "Got it")
                + (n.ackedBy.isEmpty() ? "" : "  \u2022  " + n.ackedBy.size()));
    }

    /** "Got it" on the Home card: same as in the notice list. */
    private void ackNotice() {
        Notice n = shownNotice;
        String uid = FirebaseAuth.getInstance().getUid();
        String section = UserSession.section(requireContext());
        if (n == null || n.id == null || uid == null || section == null) return;
        boolean acked = n.ackedBy.contains(uid);
        Context app = requireContext().getApplicationContext();
        FirebaseFirestore.getInstance()
                .collection("sections").document(section)
                .collection("notices").document(n.id)
                .update("ackedBy", acked ? FieldValue.arrayRemove(uid) : FieldValue.arrayUnion(uid))
                .addOnFailureListener(e -> Toast.makeText(app,
                        "Could not save: " + e.getMessage(), Toast.LENGTH_LONG).show());
    }

    // ---------------------------------------------------------------------
    // Weather + location
    // ---------------------------------------------------------------------

    private boolean hasLocationPermission() {
        return ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED
                || ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_COARSE_LOCATION)
                == PackageManager.PERMISSION_GRANTED;
    }

    /** Gets the device location, then loads weather for exactly that spot. */
    @SuppressLint("MissingPermission")
    private void loadWeather() {
        LocationManager lm = (LocationManager) requireContext().getSystemService(Context.LOCATION_SERVICE);
        boolean enabled = lm.isProviderEnabled(LocationManager.GPS_PROVIDER)
                || lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER);
        if (!enabled) {
            // Location switched off in device settings: use the default city
            loadWeatherDefaultCity();
            return;
        }

        b.tvWeather.setText("Getting your location...");
        fusedClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY,
                        new CancellationTokenSource().getToken())
                .addOnSuccessListener(location -> {
                    if (!isAdded() || getView() == null) return;
                    if (location != null) {
                        final double lat = location.getLatitude();
                        final double lon = location.getLongitude();
                        resolvePlaceName(lat, lon, place ->
                                fetchWeather(WeatherClient.fetchCurrent(lat, lon), place));
                    } else {
                        loadWeatherDefaultCity();
                    }
                })
                .addOnFailureListener(e -> {
                    if (isAdded() && getView() != null) loadWeatherDefaultCity();
                });
    }

    /** Fallback: fixed default city. */
    private void loadWeatherDefaultCity() {
        if (b == null) return;
        fetchWeather(WeatherClient.fetchCurrent(), WeatherClient.CITY);
    }

    /** Lab 11: HTTP request + JSON parsing with Retrofit and Gson. */
    private void fetchWeather(Call<WeatherResponse> call, String label) {
        // Only the newest request may update the card
        if (currentCall != null) currentCall.cancel();
        currentCall = call;
        b.tvWeather.setText("Weather in " + label + "...");

        call.enqueue(new Callback<WeatherResponse>() {
            @Override
            public void onResponse(Call<WeatherResponse> call, Response<WeatherResponse> response) {
                if (call.isCanceled() || !isAdded() || getView() == null) return;
                WeatherResponse body = response.body();
                if (response.isSuccessful() && body != null && body.current != null) {
                    boolean day = body.current.isDay == 1;

                    String desc = WeatherResponse.describe(body.current.weatherCode);
                    if (!day && body.current.weatherCode == 0) desc = "Clear night";

                    b.tvWeather.setText(String.format(Locale.getDefault(),
                            "%s  %.0f\u00B0C  \u2022  %s  \u2022  %s",
                            weatherEmoji(body.current.weatherCode, day),
                            body.current.temperature,
                            desc,
                            label));
                } else {
                    android.util.Log.e("WEATHER", "HTTP " + response.code() + " " + call.request().url());
                    b.tvWeather.setText("Weather unavailable right now");
                }
            }

            @Override
            public void onFailure(Call<WeatherResponse> call, Throwable t) {
                if (call.isCanceled() || !isAdded() || getView() == null) return;
                android.util.Log.e("WEATHER", "Request failed: " + call.request().url(), t);

                String msg;
                if (t instanceof java.net.UnknownHostException
                        || t instanceof java.net.ConnectException) {
                    msg = "\u2601\uFE0F No internet, weather unavailable";
                } else if (t instanceof java.net.SocketTimeoutException) {
                    msg = "\u23F3 Weather is slow, tap to retry";
                } else {
                    msg = "Weather unavailable right now";
                }
                b.tvWeather.setText(msg);
            }
        });
    }

    private interface PlaceCallback {
        void onPlace(String place);
    }

    /** Turns coordinates into a city/area name (runs off the main thread). */
    private void resolvePlaceName(double lat, double lon, PlaceCallback cb) {
        AppDatabase.executor.execute(() -> {
            String place = "Your location";
            try {
                List<Address> list = new Geocoder(requireContext().getApplicationContext(), Locale.getDefault()).getFromLocation(lat, lon, 1);
                if (list != null && !list.isEmpty()) {
                    Address a = list.get(0);
                    if (a.getLocality() != null) place = a.getLocality();
                    else if (a.getSubAdminArea() != null) place = a.getSubAdminArea();
                    else if (a.getAdminArea() != null) place = a.getAdminArea();
                }
            } catch (Exception ignored) { }
            final String result = place;
            FragmentActivity act = getActivity();
            if (act == null) return;
            act.runOnUiThread(() -> {
                if (isAdded() && getView() != null) cb.onPlace(result);
            });
        });
    }

    private String weatherEmoji(int code, boolean day) {
        if (code == 0 || code == 1) return day ? "\u2600\uFE0F" : "\uD83C\uDF19";   // sun / moon
        if (code <= 3) return day ? "\u26C5" : "\u2601\uFE0F";                      // partly cloudy / cloud
        if (code <= 48) return "\uD83C\uDF2B\uFE0F";                                // fog
        if (code <= 67) return "\uD83C\uDF27\uFE0F";                                // rain
        if (code <= 77) return "\u2744\uFE0F";                                      // snow
        if (code <= 82) return "\uD83C\uDF26\uFE0F";                                // rain showers
        if (code <= 86) return "\u2744\uFE0F";                                      // snow showers
        return "\u26C8\uFE0F";                                                      // thunderstorm
    }

    // ---------------------------------------------------------------------
    // Today at a glance, study plan, revision count
    // ---------------------------------------------------------------------

    private void showGlance() {
        AppDatabase db = AppDatabase.get(requireContext());
        int today = Calendar.getInstance().get(Calendar.DAY_OF_WEEK);
        db.routineDao().getForDay(today).observe(getViewLifecycleOwner(), list -> {
            todayClasses = list;
            renderNextClass();
        });
        db.deadlineDao().getNextPending().observe(getViewLifecycleOwner(), d -> renderNextDeadline(d));
        db.examDao().getAll().observe(getViewLifecycleOwner(), list -> {
            homeExams = list;
            renderExam();
        });
    }

    private void renderNextClass() {
        if (b == null) return;
        if (todayClasses.isEmpty()) {
            b.tvNextClass.setText("No classes today");
            return;
        }
        int nowMin = DateUtil.minutesOfDay(System.currentTimeMillis());
        for (RoutineEntry e : todayClasses) {
            if (e.endMinutes <= nowMin) continue;
            boolean running = e.startMinutes <= nowMin;
            String room = (e.room == null || e.room.isEmpty()) ? "" : "  (" + e.room + ")";
            b.tvNextClass.setText((running ? "Now: " : "Next class: ")
                    + TimeUtil.fmt(e.startMinutes) + "  " + e.course + room);
            return;
        }
        b.tvNextClass.setText("No more classes today");
    }

    private void renderNextDeadline(Deadline d) {
        if (b == null) return;
        if (d == null) {
            b.tvNextDeadline.setText("No pending deadlines");
            return;
        }
        boolean overdue = d.dueAt < System.currentTimeMillis();
        b.tvNextDeadline.setText((overdue ? "Overdue: " : "Next deadline: ") + d.title
                + "  \u2022  " + TimeUtil.friendly(d.dueAt));
    }

    private void showTodaysStudy() {
        AppDatabase.get(requireContext()).studyDao().getAll().observe(getViewLifecycleOwner(), list -> {
            int dayIndex = Calendar.getInstance().get(Calendar.DAY_OF_WEEK) - 1;   // 0 = Sunday
            StringBuilder sb = new StringBuilder();
            for (StudySession s : list) {
                if (!s.enabled || (s.daysMask & (1 << dayIndex)) == 0) continue;
                if (sb.length() > 0) sb.append("\n");
                sb.append(TimeUtil.fmt(s.startMinutes)).append(" \u2013 ")
                        .append(TimeUtil.fmt(s.endMinutes)).append("  ").append(s.subject);
            }
            b.tvStudyToday.setText(sb.length() == 0
                    ? "Nothing planned. Tap to add a study session."
                    : sb.toString());
        });
    }

    /** Revision streak and the weekly goal bar. */
    private void renderGoal() {
        if (b == null) return;
        int goal = com.example.campuscompanion.util.StudyStats.goal(requireContext());
        int done = com.example.campuscompanion.util.StudyStats.revisionsThisWeek(requireContext());
        b.tvStudyGoal.setText(com.example.campuscompanion.util.StudyStats.summary(requireContext()) + "   \u203A");
        b.goalBar.setVisibility(goal == 0 ? View.GONE : View.VISIBLE);
        b.goalBar.setProgress(Math.min(100, goal == 0 ? 0 : done * 100 / goal));
        b.goalBar.setContentDescription("Weekly revision goal: " + done + " of " + goal);
    }

    private void showRevisionCount() {
        AppDatabase.get(requireContext()).topicDao().getAll().observe(getViewLifecycleOwner(), list -> {
            long now = System.currentTimeMillis();
            int due = 0;
            for (Topic t : list) if (RevisionPlan.isDue(t, now)) due++;
            b.tvRevise.setText(due == 0 ? "No revision due today  \u203A"
                    : due + (due == 1 ? " topic" : " topics") + " to revise  \u203A");
            renderGoal();
        });
    }

    // ---------------------------------------------------------------------
    // Attendance card, time-of-day order and entrance animation
    // ---------------------------------------------------------------------

    private void showAttendance() {
        AttendanceDao dao = AppDatabase.get(requireContext()).attendanceDao();
        dao.getCourses().observe(getViewLifecycleOwner(), list -> {
            homeCourses = list;
            renderAttendance();
        });
        dao.getAllRecords().observe(getViewLifecycleOwner(), list -> {
            homeRecords = list;
            renderAttendance();
        });
        b.cardAttendance.setOnClickListener(v -> host().selectTab(R.id.nav_attendance));
    }

    /** The nearest upcoming exam from the Exams list. */
    private void renderExam() {
        if (b == null) return;
        com.example.campuscompanion.data.local.Exam best = null;
        int bestDays = Integer.MAX_VALUE;
        for (com.example.campuscompanion.data.local.Exam x : homeExams) {
            int d = com.example.campuscompanion.util.ExamUtil.daysLeft(x.examDate);
            if (d >= 0 && d < bestDays) {
                best = x;
                bestDays = d;
            }
        }
        if (best == null) {
            b.tvNextExam.setVisibility(View.GONE);
            return;
        }
        b.tvNextExam.setVisibility(View.VISIBLE);
        b.tvNextExam.setText("Exam: " + best.subject + "  \u2022  "
                + com.example.campuscompanion.util.ExamUtil.label(bestDays));
    }

    private void renderAttendance() {
        if (b == null) return;
        if (homeCourses.isEmpty()) {
            b.tvAttendanceHome.setText("Add your courses and track every class.");
            return;
        }
        Map<Integer, int[]> counts = AttendanceStats.counts(homeRecords);
        int overall = AttendanceStats.overallPercent(counts);
        if (overall < 0) {
            b.tvAttendanceHome.setText("No classes marked yet. Tap to mark today's classes.");
            return;
        }

        AttendanceCourse worst = null;
        AttendanceMath.Result worstStats = null;
        for (AttendanceCourse c : homeCourses) {
            AttendanceMath.Result r = AttendanceStats.forCourse(c, counts);
            if (r.held == 0) continue;
            if (worstStats == null || r.percent - c.minPercent < worstStats.percent - worst.minPercent) {
                worst = c;
                worstStats = r;
            }
        }

        StringBuilder sb = new StringBuilder("Overall ").append(overall).append("%");
        int streak = AttendanceStats.streak(homeRecords);
        if (streak >= 2) sb.append("   \uD83D\uDD25 ").append(streak).append(" in a row");
        if (worst != null) {
            sb.append("\n").append(worstStats.level == AttendanceMath.LOW ? "\u26A0\uFE0F " : "")
                    .append("Lowest: ").append(worst.title()).append("  ")
                    .append(worstStats.percent).append("% (min ").append(worst.minPercent).append("%)");
        }
        b.tvAttendanceHome.setText(sb);
    }

    /** Cards slide up and fade in, one after another. */
    private void animateCards() {
        ViewGroup parent = b.homeContainer;
        for (int i = 0; i < parent.getChildCount(); i++) {
            View v = parent.getChildAt(i);
            v.setAlpha(0f);
            v.setTranslationY(40f);
            v.animate().alpha(1f).translationY(0f)
                    .setStartDelay(Math.min(i, 8) * 50L).setDuration(300).start();
        }
    }

    // ---------------------------------------------------------------------
    // Quick tiles
    // ---------------------------------------------------------------------

    private void buildTiles() {
        Object[][] tiles = {
                {"My day", R.drawable.ic_tile_day, R.color.home_classes, DayActivity.class},
                {"Planner", R.drawable.ic_tile_planner, R.color.home_deadline, ExamPlannerActivity.class},
                {"Focus", R.drawable.ic_tile_focus, R.color.study_teal, FocusActivity.class},
                {"Routine", R.drawable.ic_tile_routine, R.color.home_routine, RoutineActivity.class},
                {"Timetable", R.drawable.ic_tile_timetable, R.color.home_timetable, TimetableActivity.class},
                {"Calendar", R.drawable.ic_tile_calendar, R.color.home_calendar, CalendarActivity.class},
                {"Revision", R.drawable.ic_tile_revision, R.color.study_teal, RevisionActivity.class},
                {"Exams", R.drawable.ic_tile_exam, R.color.home_deadline, ExamsActivity.class},
                {"Recap", R.drawable.ic_tile_recap, R.color.home_attendance, RecapActivity.class},
                {"Resources", R.drawable.ic_tile_resources, R.color.home_notice, ResourcesActivity.class},
                {"Polls", R.drawable.ic_tile_poll, R.color.home_classes, PollsActivity.class},
                {"Statistics", R.drawable.ic_tile_stats, R.color.home_stats, StatsActivity.class},
                {"GPA", R.drawable.ic_tile_gpa, R.color.home_stats, GpaActivity.class},
                {"Badges", R.drawable.ic_tile_badge, R.color.home_notice, AchievementsActivity.class},
                {"Scan", R.drawable.ic_tile_scan, R.color.home_classes, ScanActivity.class},
        };
        b.tileGrid.removeAllViews();
        LinearLayout row = null;
        for (int i = 0; i < tiles.length; i++) {
            if (i % 3 == 0) {
                row = new LinearLayout(requireContext());
                row.setOrientation(LinearLayout.HORIZONTAL);
                b.tileGrid.addView(row, new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            }
            ItemTileBinding t = ItemTileBinding.inflate(LayoutInflater.from(requireContext()), row, false);
            t.tvTile.setText((String) tiles[i][0]);
            t.ivTile.setImageResource((Integer) tiles[i][1]);
            t.ivTile.setImageTintList(ColorStateList.valueOf(
                    ContextCompat.getColor(requireContext(), (Integer) tiles[i][2])));
            final Class<?> target = (Class<?>) tiles[i][3];
            t.tile.setOnClickListener(v -> {
                if (target == DeadlinesActivity.class) host().selectTab(R.id.nav_deadlines);
                else startActivity(new Intent(requireContext(), target));
            });
            row.addView(t.getRoot());
        }
    }
}
