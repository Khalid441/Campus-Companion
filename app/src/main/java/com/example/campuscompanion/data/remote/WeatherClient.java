package com.example.campuscompanion.data.remote;

import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;
import retrofit2.Call;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

public class WeatherClient {

    // Fallback location (used when GPS is denied, off, or unavailable)
    public static final String CITY = "Sherpur";
    private static final double DEFAULT_LAT = 25.0189;
    private static final double DEFAULT_LON = 90.0175;

    private static final String FIELDS = "temperature_2m,wind_speed_10m,weather_code";
    private static final String TIMEZONE = "auto";

    private static WeatherApi api;

    private static WeatherApi api() {
        if (api == null) {
            OkHttpClient http = new OkHttpClient.Builder()
                    .connectTimeout(15, TimeUnit.SECONDS)
                    .readTimeout(15, TimeUnit.SECONDS)
                    .retryOnConnectionFailure(true)
                    .build();
            api = new Retrofit.Builder()
                    .baseUrl("https://api.open-meteo.com/")
                    .client(http)
                    .addConverterFactory(GsonConverterFactory.create())
                    .build()
                    .create(WeatherApi.class);
        }
        return api;
    }

    /** Fallback: fixed default city. */
    public static Call<WeatherResponse> fetchCurrent() {
        return fetchCurrent(DEFAULT_LAT, DEFAULT_LON);
    }

    /** Weather for exact coordinates (device location). */
    public static Call<WeatherResponse> fetchCurrent(double lat, double lon) {
        return api().current(lat, lon, FIELDS, TIMEZONE);
    }
}