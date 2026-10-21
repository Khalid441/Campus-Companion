package com.example.campuscompanion.data.remote;

import com.google.gson.annotations.SerializedName;

public class WeatherResponse {

    @SerializedName("current")
    public Current current;

    public static class Current {
        @SerializedName("temperature_2m")
        public double temperature;

        @SerializedName("wind_speed_10m")
        public double windSpeed;

        @SerializedName("weather_code")
        public int weatherCode;
        @SerializedName("is_day")
        public int isDay;
    }

    /** Converts an Open-Meteo weather code into readable text. */
    public static String describe(int code) {
        switch (code) {
            case 0:  return "Clear sky";
            case 1:  return "Mainly clear";
            case 2:  return "Partly cloudy";
            case 3:  return "Overcast";
            case 45:
            case 48: return "Fog";
            case 51:
            case 53:
            case 55: return "Drizzle";
            case 56:
            case 57: return "Freezing drizzle";
            case 61: return "Light rain";
            case 63: return "Rain";
            case 65: return "Heavy rain";
            case 66:
            case 67: return "Freezing rain";
            case 71:
            case 73:
            case 75:
            case 77: return "Snow";
            case 80:
            case 81:
            case 82: return "Rain showers";
            case 85:
            case 86: return "Snow showers";
            case 95: return "Thunderstorm";
            case 96:
            case 99: return "Thunderstorm with hail";
            default: return "Unknown";
        }
    }
}