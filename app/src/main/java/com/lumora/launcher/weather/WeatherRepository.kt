package com.lumora.launcher.weather

import android.content.Context
import android.location.Geocoder
import android.location.Location
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.google.gson.Gson
import com.lumora.launcher.data.WeatherInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale

private val Context.weatherStore by preferencesDataStore("lumora_weather")

class WeatherRepository(private val context: Context) {

    private val gson = Gson()
    private val CACHE_KEY = stringPreferencesKey("weather_cache")
    private val CACHE_TIME_KEY = longPreferencesKey("weather_cache_time")
    private val CACHE_TTL_MS = 30 * 60 * 1000L // 30 minutes

    /**
     * Fetch weather by coordinates using Open-Meteo (no API key required for non-commercial use).
     */
    suspend fun fetchWeather(lat: Double, lon: Double, cityName: String = ""): Result<WeatherInfo> =
        withContext(Dispatchers.IO) {
            try {
                val url =
                    "https://api.open-meteo.com/v1/forecast?latitude=$lat&longitude=$lon&current=temperature_2m,weather_code&timezone=auto"
                val json = httpGet(url)
                val root = JSONObject(json)
                val current = root.getJSONObject("current")
                val temp = current.getDouble("temperature_2m")
                val code = current.getInt("weather_code")
                val (desc, icon) = mapWmoCode(code)
                val resolvedCity = cityName.ifBlank { reverseGeocode(lat, lon) ?: "Unknown" }
                val info = WeatherInfo(
                    temperature = temp,
                    weatherCode = code,
                    description = desc,
                    iconName = icon,
                    cityName = resolvedCity,
                    updatedAt = System.currentTimeMillis(),
                    isFromCache = false
                )
                cache(info)
                Result.success(info)
            } catch (e: Exception) {
                // Try cache
                getCached()?.let { Result.success(it.copy(isFromCache = true)) }
                    ?: Result.failure(e)
            }
        }

    /**
     * Search city name via Open-Meteo Geocoding API.
     */
    suspend fun searchCity(query: String): Result<Pair<Double, Double>> = withContext(Dispatchers.IO) {
        try {
            val encoded = java.net.URLEncoder.encode(query, "UTF-8")
            val url = "https://geocoding-api.open-meteo.com/v1/search?name=$encoded&count=1&language=en&format=json"
            val json = httpGet(url)
            val root = JSONObject(json)
            if (!root.has("results")) return@withContext Result.failure(Exception("City not found"))
            val results = root.getJSONArray("results")
            if (results.length() == 0) return@withContext Result.failure(Exception("City not found"))
            val first = results.getJSONObject(0)
            val lat = first.getDouble("latitude")
            val lon = first.getDouble("longitude")
            Result.success(lat to lon)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getCached(): WeatherInfo? = withContext(Dispatchers.IO) {
        try {
            val prefs = context.weatherStore.data.first()
            val json = prefs[CACHE_KEY] ?: return@withContext null
            val time = prefs[CACHE_TIME_KEY] ?: 0L
            if (System.currentTimeMillis() - time > CACHE_TTL_MS * 4) return@withContext null // allow stale up to 2h
            gson.fromJson(json, WeatherInfo::class.java)
        } catch (_: Exception) {
            null
        }
    }

    private suspend fun cache(info: WeatherInfo) {
        context.weatherStore.edit { prefs ->
            prefs[CACHE_KEY] = gson.toJson(info)
            prefs[CACHE_TIME_KEY] = System.currentTimeMillis()
        }
    }

    private fun httpGet(urlStr: String): String {
        val conn = (URL(urlStr).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 10000
            readTimeout = 10000
            setRequestProperty("User-Agent", "LumoraLauncher/1.0")
        }
        return conn.inputStream.bufferedReader().use { it.readText() }
    }

    private fun reverseGeocode(lat: Double, lon: Double): String? {
        return try {
            if (!Geocoder.isPresent()) return null
            val geocoder = Geocoder(context, Locale.getDefault())
            @Suppress("DEPRECATION")
            val list = geocoder.getFromLocation(lat, lon, 1)
            list?.firstOrNull()?.locality ?: list?.firstOrNull()?.subAdminArea
        } catch (_: Exception) {
            null
        }
    }

    /**
     * WMO Weather interpretation codes → description + icon name.
     * https://open-meteo.com/en/docs
     */
    private fun mapWmoCode(code: Int): Pair<String, String> = when (code) {
        0 -> "Clear sky" to "clear"
        1, 2, 3 -> "Partly cloudy" to "partly_cloudy"
        45, 48 -> "Fog" to "fog"
        51, 53, 55 -> "Drizzle" to "drizzle"
        56, 57 -> "Freezing drizzle" to "drizzle"
        61, 63, 65 -> "Rain" to "rain"
        66, 67 -> "Freezing rain" to "rain"
        71, 73, 75 -> "Snow" to "snow"
        77 -> "Snow grains" to "snow"
        80, 81, 82 -> "Rain showers" to "rain"
        85, 86 -> "Snow showers" to "snow"
        95 -> "Thunderstorm" to "thunder"
        96, 99 -> "Thunderstorm with hail" to "thunder"
        else -> "Unknown" to "unknown"
    }
}
