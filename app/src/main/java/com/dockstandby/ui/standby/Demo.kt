package com.dockstandby.ui.standby

import android.os.SystemClock
import com.dockstandby.data.BatteryInfo
import com.dockstandby.data.CalEvent
import com.dockstandby.data.HourTemp
import com.dockstandby.data.NowPlaying
import com.dockstandby.data.PowerSource
import com.dockstandby.data.Settings
import com.dockstandby.data.Sky
import com.dockstandby.data.Weather
import java.time.LocalDateTime

/**
 * The exact sample content from the mockups (Wed 28 Oct, 10:42, 72% wireless…), used by
 * `adb shell am start -n com.dockstandby/.StandByActivity --ez demo true --ei page N`
 * to compare each screen against its mockup. Seconds keep ticking so animations run.
 */
fun demoData(settings: Settings, realNow: LocalDateTime, time: String? = null): StandByData {
    val day = LocalDateTime.of(2026, 10, 28, 0, 0)
    fun at(h: Int, m: Int = 0) = day.withHour(h).withMinute(m)
    return StandByData(
        now = (time?.let { val (h, m) = it.split(":").map(String::toInt); at(h, m) } ?: at(10, 42)).withSecond(realNow.second).withNano(realNow.nano),
        is24h = false,
        battery = BatteryInfo(level = 72, charging = true, source = PowerSource.Wireless, tempC = 31.4f, minutesToFull = 48, watts = 15f),
        events = listOf(
            CalEvent("Standup", at(11), at(11, 15), false, null, null),
            CalEvent("Design review", at(14), at(15), false, null, "Meet"),
            CalEvent("Gym", at(19), at(19), false, null, null),
        ),
        alarm = day.plusDays(1).withHour(7),
        weather = Weather(
            city = "Mumbai", temp = 29, high = 32, low = 25, rainChance = 10, sky = Sky.PartlyCloudy,
            hourly = listOf(HourTemp(at(13), 31), HourTemp(at(15), 32), HourTemp(at(18), 29), HourTemp(at(21), 27)),
        ),
        nowPlaying = NowPlaying(
            title = "[Song title]", artist = "[Artist]", art = null, appName = "Spotify", playing = true,
            durationMs = 211_000, positionMs = 84_000, positionAt = SystemClock.elapsedRealtime(), speed = 0f, controller = null,
        ),
        steps = 6240,
        settings = settings,
    )
}
