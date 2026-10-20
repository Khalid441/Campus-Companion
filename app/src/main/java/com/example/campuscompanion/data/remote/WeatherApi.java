package com.example.campuscompanion.data.remote;

import retrofit2.Call;
import retrofit2.http.GET;
import retrofit2.http.Query;

public interface WeatherApi {
    @GET("v1/forecast")
    Call<WeatherResponse> current(
            @Query("latitude") double latitude,
            @Query("longitude") double longitude,
            @Query("current") String fields,
            @Query("timezone") String timezone);
}
