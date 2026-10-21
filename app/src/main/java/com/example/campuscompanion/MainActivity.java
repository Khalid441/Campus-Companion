package com.example.campuscompanion;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.database.Cursor;
import android.location.Address;
import android.location.Geocoder;
import android.location.LocationManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.widget.CompoundButton;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.content.ContextCompat;

import com.example.campuscompanion.auth.UserSession;
import com.example.campuscompanion.data.local.AppDatabase;
import com.example.campuscompanion.data.remote.WeatherClient;
import com.example.campuscompanion.data.remote.WeatherResponse;
import com.example.campuscompanion.databinding.ActivityMainBinding;
import com.example.campuscompanion.notification.NotificationHelper;
import com.example.campuscompanion.provider.DeadlineProvider;
import com.example.campuscompanion.reminder.ReminderScheduler;
import com.example.campuscompanion.service.NextDeadlineService;
import com.example.campuscompanion.sync.SyncManager;
import com.example.campuscompanion.ui.AddDeadlineActivity;
import com.example.campuscompanion.ui.DeadlinesActivity;
import com.example.campuscompanion.ui.LoginActivity;
import com.example.campuscompanion.ui.RoutineActivity;
import com.example.campuscompanion.util.TimeUtil;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.Priority;
import com.google.android.gms.tasks.CancellationTokenSource;
import com.google.firebase.auth.FirebaseAuth;

import java.util.Calendar;
import java.util.List;
import java.util.Locale;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class MainActivity extends AppCompatActivity {

    private ActivityMainBinding b;
    private SharedPreferences prefs;
    private FusedLocationProviderClient fusedClient;
    private Call<WeatherResponse> currentCall;

    private final ActivityResultLauncher<String> notificationPermission =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), granted -> { });

    // Location permission (fine + coarse together)
    private final ActivityResultLauncher<String[]> locationPermission =
            registerForActivityResult(new ActivityResultContracts.RequestMultiplePermissions(), result -> {
                if (hasLocationPermission()) {
                    loadWeather();
                } else {
                    loadWeatherDefaultCity();
                }
            });

    private final CompoundButton.OnCheckedChangeListener trackerListener = (button, on) -> {
        if (on) {
            Intent i = new Intent(this, NextDeadlineService.class)
                    .setAction(NextDeadlineService.ACTION_START);
            ContextCompat.startForegroundService(this, i);
        } else {
            stopService(new Intent(this, NextDeadlineService.class));
        }
        prefs.edit().putBoolean(NextDeadlineService.KEY_TRACKER_ON, on).apply();
    };

    private final CompoundButton.OnCheckedChangeListener darkListener = (button, on) -> {
        int mode = on ? AppCompatDelegate.MODE_NIGHT_YES : AppCompatDelegate.MODE_NIGHT_NO;
        getSharedPreferences(CampusApp.PREFS, Context.MODE_PRIVATE)
                .edit().putInt(CampusApp.KEY_NIGHT_MODE, mode).apply();
        AppCompatDelegate.setDefaultNightMode(mode); // recreates the screen with the new theme
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Not signed in: back to the login screen
        if (FirebaseAuth.getInstance().getCurrentUser() == null || UserSession.section(this) == null) {
            goToLogin();
            return;
        }

        b = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(b.getRoot());

        prefs = getSharedPreferences(NextDeadlineService.PREFS, Context.MODE_PRIVATE);
        NotificationHelper.createChannels(this);
        fusedClient = LocationServices.getFusedLocationProviderClient(this);

        if (Build.VERSION.SDK_INT >= 33 && !NotificationHelper.canNotify(this)) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS);
        }

        // Ask for location once when the screen opens
        if (!hasLocationPermission()) {
            locationPermission.launch(new String[]{
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
            });
        }

        String name = UserSession.name(this);
        int hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
        String hello = hour < 12 ? "Good morning" : hour < 17 ? "Good afternoon" : "Good evening";
        b.tvGreeting.setText(hello + (name.isEmpty() ? "" : ",\n" + name));
        b.tvSection.setText("Section " + UserSession.section(this)
                + (UserSession.isCr(this) ? "  \u2022  Class representative" : ""));

        // Real-time sync of shared deadlines + background fallback
        SyncManager.start(this, UserSession.section(this));
        SyncManager.schedulePeriodic(this);

        b.btnDeadlines.setOnClickListener(v -> startActivity(new Intent(this, DeadlinesActivity.class)));
        b.btnRoutine.setOnClickListener(v -> startActivity(new Intent(this, RoutineActivity.class)));

        b.btnPost.setVisibility(UserSession.isCr(this) ? View.VISIBLE : View.GONE);
        b.btnPost.setOnClickListener(v -> startActivity(
                new Intent(this, AddDeadlineActivity.class)
                        .putExtra(AddDeadlineActivity.EXTRA_POST_TO_SECTION, true)));

        b.btnExactAlarm.setOnClickListener(v -> {
            if (Build.VERSION.SDK_INT >= 31) {
                startActivity(new Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                        Uri.parse("package:" + getPackageName())));
            }
        });

        b.btnLogout.setOnClickListener(v -> logout());
        b.tvWeather.setOnClickListener(v -> {
            if (hasLocationPermission()) loadWeather(); else loadWeatherDefaultCity();
        });
        showTodaysClasses();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (b == null) return;

        b.swTracker.setOnCheckedChangeListener(null);
        b.swTracker.setChecked(prefs.getBoolean(NextDeadlineService.KEY_TRACKER_ON, false));
        b.swTracker.setOnCheckedChangeListener(trackerListener);

        boolean isDark = (getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK)
                == Configuration.UI_MODE_NIGHT_YES;
        b.swDark.setOnCheckedChangeListener(null);
        b.swDark.setChecked(isDark);
        b.swDark.setOnCheckedChangeListener(darkListener);

        b.btnExactAlarm.setVisibility(ReminderScheduler.canScheduleExact(this) ? View.GONE : View.VISIBLE);

        showProviderCount();

        // Only fetch here if permission is already granted; otherwise the
        // permission dialog result (or the fallback) handles it.
        if (hasLocationPermission()) {
            loadWeather();
        } else {
            loadWeatherDefaultCity();
        }
    }

    // ---------------------------------------------------------------------
    // Weather + location
    // ---------------------------------------------------------------------

    private boolean hasLocationPermission() {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED
                || ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION)
                == PackageManager.PERMISSION_GRANTED;
    }

    /** Gets the device location, then loads weather for exactly that spot. */
    @SuppressLint("MissingPermission")
    private void loadWeather() {
        LocationManager lm = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
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
                    if (isFinishing() || isDestroyed()) return;
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
                    if (!isFinishing() && !isDestroyed()) loadWeatherDefaultCity();
                });
    }

    /** Fallback: your original fixed-city weather. */
    private void loadWeatherDefaultCity() {
        if (b == null) return;
        fetchWeather(WeatherClient.fetchCurrent(), WeatherClient.CITY);
    }

    /** Lab 11: HTTP request + JSON parsing with Retrofit and Gson. */
    private void fetchWeather(Call<WeatherResponse> call, String label) {
        // Only the newest request may update the card
        if (currentCall != null) currentCall.cancel();
        currentCall = call;
        b.tvWeather.setText("Weather in " + label + ": loading...");

        call.enqueue(new Callback<WeatherResponse>() {
            @Override
            public void onResponse(Call<WeatherResponse> call, Response<WeatherResponse> response) {
                if (call.isCanceled() || isFinishing() || isDestroyed()) return;
                WeatherResponse body = response.body();
                if (response.isSuccessful() && body != null && body.current != null) {
                    boolean day = body.current.isDay == 1;

                    String desc = WeatherResponse.describe(body.current.weatherCode);
                    if (!day && body.current.weatherCode == 0) desc = "Clear night";

                    b.tvWeather.setText(String.format(Locale.getDefault(),
                            "%s  %s\n%.0f\u00B0C, %s\nWind %.0f km/h",
                            weatherEmoji(body.current.weatherCode, day), label,
                            body.current.temperature,
                            desc,
                            body.current.windSpeed));
                } else {
                    android.util.Log.e("WEATHER", "HTTP " + response.code() + " " + call.request().url());
                    b.tvWeather.setText("Weather unavailable right now");
                }
            }

            @Override
            public void onFailure(Call<WeatherResponse> call, Throwable t) {
                if (call.isCanceled() || isFinishing() || isDestroyed()) return;
                android.util.Log.e("WEATHER", "Request failed: " + call.request().url(), t);

                String msg;
                if (t instanceof java.net.UnknownHostException
                        || t instanceof java.net.ConnectException) {
                    msg = "\u2601\uFE0F No internet connection\nConnect to Wi-Fi or mobile data to see the weather.";
                } else if (t instanceof java.net.SocketTimeoutException) {
                    msg = "\u23F3 Weather is taking too long\nCheck your connection and try again.";
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
                List<Address> list = new Geocoder(this, Locale.getDefault()).getFromLocation(lat, lon, 1);
                if (list != null && !list.isEmpty()) {
                    Address a = list.get(0);
                    if (a.getLocality() != null) place = a.getLocality();
                    else if (a.getSubAdminArea() != null) place = a.getSubAdminArea();
                    else if (a.getAdminArea() != null) place = a.getAdminArea();
                }
            } catch (Exception ignored) { }
            final String result = place;
            runOnUiThread(() -> {
                if (!isFinishing() && !isDestroyed()) cb.onPlace(result);
            });
        });
    }

    // ---------------------------------------------------------------------

    private void showTodaysClasses() {
        int today = Calendar.getInstance().get(Calendar.DAY_OF_WEEK);
        AppDatabase.get(this).routineDao().getForDay(today).observe(this, list -> {
            if (list.isEmpty()) {
                b.tvToday.setText("No classes today");
                return;
            }
            StringBuilder sb = new StringBuilder();
            for (com.example.campuscompanion.data.local.RoutineEntry e : list) {
                if (sb.length() > 0) sb.append("\n");
                sb.append(TimeUtil.fmt(e.startMinutes)).append("  ").append(e.course);
                if (e.room != null && !e.room.isEmpty()) sb.append("  (").append(e.room).append(")");
            }
            b.tvToday.setText(sb);
        });
    }

    /** ContentProvider demo: count deadlines through a content:// URI. */
    private void showProviderCount() {
        AppDatabase.executor.execute(() -> {
            int count = 0;
            try (Cursor c = getContentResolver().query(
                    DeadlineProvider.CONTENT_URI, new String[]{"id"}, null, null, null)) {
                if (c != null) count = c.getCount();
            }
            final int n = count;
            runOnUiThread(() -> {
                if (!isFinishing() && !isDestroyed()) {
                    b.tvProviderCount.setText("Deadlines read via ContentProvider: " + n);
                }
            });
        });
    }

    private void logout() {
        stopService(new Intent(this, NextDeadlineService.class));
        prefs.edit().putBoolean(NextDeadlineService.KEY_TRACKER_ON, false).apply();
        SyncManager.stop(this);
        FirebaseAuth.getInstance().signOut();
        UserSession.clear(this);
        goToLogin();
    }

    private void goToLogin() {
        Intent i = new Intent(this, LoginActivity.class);
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(i);
        finish();
    }
    private String weatherEmoji(int code, boolean day) {
        if (code == 0 || code == 1) return day ? "\u2600\uFE0F" : "\uD83C\uDF19";   // sun / moon
        if (code <= 3) return day ? "\u26C5" : "\u2601\uFE0F";                      // partly cloudy / cloud
        if (code <= 48) return "\uD83C\uDF2B\uFE0F";                                // fog
        if (code <= 67) return "\uD83C\uDF27\uFE0F";                                // rain
        if (code <= 77) return "\u2744\uFE0F";                                      // snow
        if (code <= 82) return "\uD83C\uDF26\uFE0F";   // rain showers
        if (code <= 86) return "\u2744\uFE0F";         // snow showers
        return "\u26C8\uFE0F";                         // thunderstorm                                                     // thunderstorm
    }
}