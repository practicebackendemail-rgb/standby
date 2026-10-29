package com.dockstandby.data

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.location.Geocoder
import android.location.LocationManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.produceState
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.time.LocalDateTime
import java.util.Locale
import kotlin.math.roundToInt

enum class Sky { Clear, PartlyCloudy, Cloudy, Fog, Rain, Snow, Storm }

data class HourTemp(val time: LocalDateTime, val temp: Int)

data class Weather(
    val city: String?,
    val temp: Int,
    val high: Int,
    val low: Int,
    val rainChance: Int,
    val sky: Sky,
    val hourly: List<HourTemp>,
)

val Sky.label get() = when (this) {
    Sky.Clear -> "Clear"
    Sky.PartlyCloudy -> "Partly Cloudy"
    Sky.Cloudy -> "Cloudy"
    Sky.Fog -> "Fog"
    Sky.Rain -> "Rain"
    Sky.Snow -> "Snow"
    Sky.Storm -> "Thunderstorm"
}

/** Weather from Open-Meteo (no API key) for the last known coarse location. Null until loaded. */
@Composable
fun rememberWeather(): State<Weather?> {
    val context = LocalContext.current
    return produceState<Weather?>(null) {
        while (true) {
            withContext(Dispatchers.IO) { runCatching { fetchWeather(context) }.getOrNull() }
                ?.let { value = it }
            delay(if (value == null) 60_000L else 30 * 60_000L)
        }
    }
}

@SuppressLint("MissingPermission")
private fun fetchWeather(context: Context): Weather? {
    if (!context.hasPermission(Manifest.permission.ACCESS_COARSE_LOCATION)) return null
    val lm = context.getSystemService(LocationManager::class.java) ?: return null
    val loc = lm.getProviders(true)
        .mapNotNull { runCatching { lm.getLastKnownLocation(it) }.getOrNull() }
        .maxByOrNull { it.time } ?: return null

    val url = "https://api.open-meteo.com/v1/forecast?latitude=%.3f&longitude=%.3f".format(Locale.US, loc.latitude, loc.longitude) +
        "&current=temperature_2m,weather_code&hourly=temperature_2m" +
        "&daily=temperature_2m_max,temperature_2m_min,precipitation_probability_max" +
        "&timezone=auto&forecast_days=2"
    val conn = URL(url).openConnection() as HttpURLConnection
    conn.connectTimeout = 10_000
    conn.readTimeout = 10_000
    val json = try { JSONObject(conn.inputStream.bufferedReader().readText()) } finally { conn.disconnect() }

    val current = json.getJSONObject("current")
    val daily = json.getJSONObject("daily")
    val hourly = json.getJSONObject("hourly")
    val times = hourly.getJSONArray("time")
    val temps = hourly.getJSONArray("temperature_2m")
    val now = LocalDateTime.now().withMinute(0).withSecond(0).withNano(0)
    val hours = (0 until times.length())
        .map { HourTemp(LocalDateTime.parse(times.getString(it)), temps.getDouble(it).roundToInt()) }
        .filter { it.time.isAfter(now) }

    @Suppress("DEPRECATION")
    val city = runCatching {
        Geocoder(context).getFromLocation(loc.latitude, loc.longitude, 1)?.firstOrNull()
            ?.let { it.locality ?: it.subAdminArea ?: it.adminArea }
    }.getOrNull()

    return Weather(
        city = city,
        temp = current.getDouble("temperature_2m").roundToInt(),
        high = daily.getJSONArray("temperature_2m_max").getDouble(0).roundToInt(),
        low = daily.getJSONArray("temperature_2m_min").getDouble(0).roundToInt(),
        rainChance = daily.getJSONArray("precipitation_probability_max").optInt(0, 0),
        sky = skyFor(current.getInt("weather_code")),
        // "Now" plus four upcoming slots spaced a few hours apart, like the mockup.
        hourly = listOfNotNull(hours.getOrNull(1), hours.getOrNull(3), hours.getOrNull(6), hours.getOrNull(9)),
    )
}

/** WMO weather interpretation codes. */
private fun skyFor(code: Int) = when (code) {
    0 -> Sky.Clear
    1, 2 -> Sky.PartlyCloudy
    3 -> Sky.Cloudy
    45, 48 -> Sky.Fog
    in 51..67, in 80..82 -> Sky.Rain
    in 71..77, 85, 86 -> Sky.Snow
    in 95..99 -> Sky.Storm
    else -> Sky.Cloudy
}
