package com.dockstandby.data

import android.Manifest
import android.annotation.SuppressLint
import android.app.AlarmManager
import android.content.BroadcastReceiver
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.BatteryManager
import android.os.Build
import android.provider.CalendarContract
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

fun Context.hasPermission(p: String) =
    ContextCompat.checkSelfPermission(this, p) == PackageManager.PERMISSION_GRANTED

// ---------- Clock ----------
/** Current time, ticking on each second boundary (or each minute when [perMinute], to save power). */
/** Current time, ticking on each second boundary. */
@Composable
fun rememberNow(perMinute: Boolean = false): State<LocalDateTime> = produceState(LocalDateTime.now(), perMinute) {
    while (true) {
        value = LocalDateTime.now()
        val step = if (perMinute) 60_000L else 1000L
        delay(step - System.currentTimeMillis() % step)
    }
}

// ---------- Battery ----------

enum class PowerSource(val label: String) { None("On battery"), AC("Wired"), USB("USB"), Wireless("Wireless"), Dock("Dock") }

data class BatteryInfo(
    val level: Int = 100,
    val charging: Boolean = false,
    val full: Boolean = false,
    val source: PowerSource = PowerSource.None,
    val tempC: Float? = null,
    /** Estimated minutes until full, if the system provides it. */
    val minutesToFull: Int? = null,
    /** Charging power in watts, from current × voltage, when the phone reports both. */
    val watts: Float? = null,
)

@Composable
fun rememberBattery(): State<BatteryInfo> {
    val context = LocalContext.current
    val state = remember { mutableStateOf(BatteryInfo()) }
    DisposableEffect(context) {
        val bm = context.getSystemService(BatteryManager::class.java)
        fun parse(i: Intent) {
            val level = i.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
            val scale = i.getIntExtra(BatteryManager.EXTRA_SCALE, 100)
            val status = i.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
            val plugged = i.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0)
            val temp = i.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, Int.MIN_VALUE)
            val mV = i.getIntExtra(BatteryManager.EXTRA_VOLTAGE, 0)
            // CURRENT_NOW is µA on most phones, mA on some; its sign also varies by vendor.
            val now = kotlin.math.abs(bm?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW) ?: 0)
            val amps = if (now in 1 until 10_000) now / 1e3f else now / 1e6f
            val watts = (amps * mV / 1000f).takeIf { it in 0.5f..200f }
            val charging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                status == BatteryManager.BATTERY_STATUS_FULL
            val remainingMs = if (Build.VERSION.SDK_INT >= 28) bm?.computeChargeTimeRemaining() ?: -1 else -1
            state.value = BatteryInfo(
                level = if (level >= 0) level * 100 / scale else 0,
                charging = charging,
                full = status == BatteryManager.BATTERY_STATUS_FULL,
                source = when {
                    plugged and BatteryManager.BATTERY_PLUGGED_WIRELESS != 0 -> PowerSource.Wireless
                    plugged and BatteryManager.BATTERY_PLUGGED_AC != 0 -> PowerSource.AC
                    plugged and BatteryManager.BATTERY_PLUGGED_USB != 0 -> PowerSource.USB
                    Build.VERSION.SDK_INT >= 33 && plugged and BatteryManager.BATTERY_PLUGGED_DOCK != 0 -> PowerSource.Dock
                    else -> PowerSource.None
                },
                tempC = if (temp != Int.MIN_VALUE) temp / 10f else null,
                minutesToFull = if (remainingMs > 0) (remainingMs / 60_000).toInt() else null,
                watts = watts,
            )
        }
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context, i: Intent) = parse(i)
        }
        ContextCompat.registerReceiver(
            context, receiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED), ContextCompat.RECEIVER_NOT_EXPORTED,
        )?.let(::parse)
        onDispose { context.unregisterReceiver(receiver) }
    }
    return state
}

// ---------- Calendar ----------

data class CalEvent(val title: String, val start: LocalDateTime, val end: LocalDateTime, val allDay: Boolean, val color: Int?, val location: String?)

/** Today's events (and tomorrow's early ones), refreshed every 5 minutes. Empty without permission. */
@Composable
fun rememberEvents(): State<List<CalEvent>> {
    val context = LocalContext.current
    return produceState(emptyList<CalEvent>()) {
        while (true) {
            value = withContext(Dispatchers.IO) { queryEvents(context) }
            delay(5 * 60_000L)
        }
    }
}

private fun queryEvents(context: Context): List<CalEvent> {
    if (!context.hasPermission(Manifest.permission.READ_CALENDAR)) return emptyList()
    val zone = ZoneId.systemDefault()
    val start = LocalDate.now().atStartOfDay(zone).toInstant().toEpochMilli()
    val end = start + 24 * 3600_000L
    val uri = CalendarContract.Instances.CONTENT_URI.buildUpon().also {
        ContentUris.appendId(it, start)
        ContentUris.appendId(it, end)
    }.build()
    val proj = arrayOf(
        CalendarContract.Instances.TITLE,
        CalendarContract.Instances.BEGIN,
        CalendarContract.Instances.END,
        CalendarContract.Instances.ALL_DAY,
        CalendarContract.Instances.DISPLAY_COLOR,
        CalendarContract.Instances.EVENT_LOCATION,
    )
    val out = mutableListOf<CalEvent>()
    runCatching {
        context.contentResolver.query(uri, proj, null, null, "${CalendarContract.Instances.BEGIN} ASC")?.use { c ->
            while (c.moveToNext()) {
                fun t(ms: Long) = LocalDateTime.ofInstant(Instant.ofEpochMilli(ms), zone)
                out += CalEvent(
                    title = c.getString(0) ?: "(No title)",
                    start = t(c.getLong(1)),
                    end = t(c.getLong(2)),
                    allDay = c.getInt(3) == 1,
                    color = if (c.isNull(4)) null else c.getInt(4),
                    location = c.getString(5)?.takeIf { it.isNotBlank() },
                )
            }
        }
    }
    return out
}

// ---------- Alarm ----------

/** Next system alarm clock, if any. */
@Composable
fun rememberNextAlarm(): State<LocalDateTime?> {
    val context = LocalContext.current
    val state = remember { mutableStateOf<LocalDateTime?>(null) }
    DisposableEffect(context) {
        val am = context.getSystemService(AlarmManager::class.java)
        fun read() {
            state.value = am?.nextAlarmClock?.triggerTime?.let {
                LocalDateTime.ofInstant(Instant.ofEpochMilli(it), ZoneId.systemDefault())
            }
        }
        val r = object : BroadcastReceiver() {
            override fun onReceive(c: Context, i: Intent) = read()
        }
        ContextCompat.registerReceiver(
            context, r, IntentFilter(AlarmManager.ACTION_NEXT_ALARM_CLOCK_CHANGED), ContextCompat.RECEIVER_EXPORTED,
        )
        read()
        onDispose { context.unregisterReceiver(r) }
    }
    return state
}

// ---------- Ambient light ----------

/**
 * True when the room is dark, from [lux] with hysteresis so it doesn't flicker at the threshold.
 * Falls back to 22:00–06:00 when there is no light sensor (lux == null).
 */
@Composable
fun rememberIsDark(lux: Float?): State<Boolean> {
    val state = remember { mutableStateOf(false) }
    if (lux == null) {
        LaunchedEffect(Unit) {
            while (true) {
                val h = LocalDateTime.now().hour
                state.value = h >= 22 || h < 6
                delay(60_000)
            }
        }
    } else if (lux < 3f) state.value = true else if (lux > 10f) state.value = false
    return state
}

// ---------- Steps ----------

/** Steps today from the hardware step counter. Null if unavailable or not permitted. */
@SuppressLint("ApplySharedPref")
@Composable
fun rememberStepsToday(active: Boolean = true): State<Int?> {
    val context = LocalContext.current
    val state = remember { mutableStateOf<Int?>(null) }
    val allowed = Build.VERSION.SDK_INT < 29 || context.hasPermission(Manifest.permission.ACTIVITY_RECOGNITION)
    val sm = remember { context.getSystemService(SensorManager::class.java) }
    val sensor = remember { sm?.getDefaultSensor(Sensor.TYPE_STEP_COUNTER) }
    if (allowed && sensor != null && active) {
        DisposableEffect(sensor) {
            val sp = Prefs.get(context).raw
            val l = object : SensorEventListener {
                override fun onSensorChanged(e: SensorEvent) {
                    // The counter is cumulative since boot; keep a baseline per day.
                    val total = e.values[0].toLong()
                    val today = LocalDate.now().toEpochDay()
                    var base = sp.getLong("stepBase", -1)
                    if (sp.getLong("stepDay", -1) != today || base < 0 || total < base) {
                        base = total
                        sp.edit().putLong("stepDay", today).putLong("stepBase", base).commit()
                    }
                    state.value = (total - base).toInt()
                }
                override fun onAccuracyChanged(s: Sensor?, a: Int) {}
            }
            sm!!.registerListener(l, sensor, SensorManager.SENSOR_DELAY_UI)
            onDispose { sm.unregisterListener(l) }
        }
    }
    return state
}
