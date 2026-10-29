package com.dockstandby.ui.standby

import android.text.format.DateFormat
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import android.os.SystemClock
import com.dockstandby.data.PageId
import com.dockstandby.data.ClockFace
import com.dockstandby.data.pagesFor
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.dockstandby.data.PresenceSensors
import com.dockstandby.data.Prefs
import com.dockstandby.data.StyleTheme
import com.dockstandby.data.rememberBattery
import com.dockstandby.data.rememberEvents
import com.dockstandby.data.rememberIsDark
import com.dockstandby.data.rememberLux
import com.dockstandby.data.rememberNextAlarm
import com.dockstandby.data.rememberNow
import com.dockstandby.data.rememberNowPlaying
import com.dockstandby.data.rememberStepsToday
import com.dockstandby.data.rememberWeather
import kotlinx.coroutines.delay


/**
 * The landscape StandBy experience. Renders nothing (and runs no sensors, polling or animations)
 * unless the host is at least STARTED, i.e. actually on screen.
 *
 * Demo runs (`--ez demo true`) use the mockup data and never dim; `--ei page -1` previews the
 * night clock and `--ei page -2` the glance clock; `--es time 07:00` sets the demo clock and
 * turns on smart switching so its rules can be checked.
 */
@Composable
fun StandByRoot(demo: Boolean = false, startPage: Int = 0, demoTheme: String? = null, demoTime: String? = null) {
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val state by lifecycle.currentStateFlow.collectAsState()
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        if (state.isAtLeast(Lifecycle.State.STARTED)) StandByContent(demo, startPage, demoTheme, demoTime)
    }
}

@Composable
private fun StandByContent(demo: Boolean, startPage: Int, demoTheme: String?, demoTime: String?) {
    val context = LocalContext.current
    val prefs = remember { Prefs.get(context) }
    val stored by prefs.settings.collectAsState()
    // Demo runs may preview a theme without touching the saved setting.
    val settings = StyleTheme.entries.firstOrNull { demo && it.name == demoTheme }?.let { stored.copy(theme = it) } ?: stored
    val battery by rememberBattery()
    val events by rememberEvents()
    val alarm by rememberNextAlarm()
    val weather by rememberWeather()
    val nowPlaying by rememberNowPlaying()
    val lux by rememberLux(active = true)
    val dark by rememberIsDark(lux)

    val presence = remember { Presence() }
    val timerSheet = remember { mutableStateOf(false) }
    PresenceSensors(active = !demo) { presence.poke(it) }
    val idle by rememberIdle(presence, if (demo) 0 else settings.dimAfterMin)
    val mode = when {
        demo && startPage == -1 -> DisplayMode.Night
        demo && startPage == -2 -> DisplayMode.Glance
        demo -> DisplayMode.Active
        settings.nightMode && dark -> DisplayMode.Night
        idle -> DisplayMode.Glance
        else -> DisplayMode.Active
    }
    if (!demo) BrightnessController(mode, lux)
    // Glance and night only show minutes, so tick once a minute there.
    val now by rememberNow(perMinute = mode != DisplayMode.Active)

    val pages = settings.pagesFor(settings.theme)
    // Hoisted so returning from glance/night lands on the same page.
    val pager = rememberPagerState(startPage.coerceIn(0, pages.size - 1)) { pages.size }
    // Steps are only shown on Widgets, so the counter only runs while that page is up.
    val steps by rememberStepsToday(active = mode == DisplayMode.Active && pages.getOrNull(pager.currentPage) == PageId.Widgets)

    val data = if (demo) demoData(settings, now, demoTime) else StandByData(
        now = now,
        is24h = DateFormat.is24HourFormat(context),
        battery = battery,
        events = events,
        alarm = alarm,
        weather = weather,
        nowPlaying = nowPlaying,
        steps = steps,
        settings = settings,
    )

    if (!demo) {
        // Jump to the charging screen whenever power is connected.
        LaunchedEffect(battery.charging, settings.theme) {
            val i = pages.indexOf(PageId.Charging)
            if (battery.charging && i >= 0) pager.animateScrollToPage(i)
        }
        // And to Now Playing when music starts (also wakes the screen from glance).
        LaunchedEffect(nowPlaying?.title, nowPlaying?.playing == true) {
            val i = pages.indexOf(PageId.NowPlaying)
            if (nowPlaying?.playing == true) presence.poke("music")
            if (i >= 0 && nowPlaying?.playing == true) pager.animateScrollToPage(i)
        }
    }

    // Smart switching: move to the page that fits the time / calendar, but never for
    // SMART_PAUSE_MS after the user swiped by hand.
    val dragged by pager.interactionSource.collectIsDraggedAsState()
    val manualAt = remember { mutableLongStateOf(Long.MIN_VALUE / 2) }
    LaunchedEffect(dragged) { if (dragged) manualAt.longValue = SystemClock.elapsedRealtime() }
    val smartOn = settings.smartSwitch && (!demo || demoTime != null)
    val minute = data.now.withSecond(0).withNano(0)
    val smart = if (smartOn) remember(minute, data.events, pages) { smartPage(minute, data.events, pages) } else null
    LaunchedEffect(smart, mode == DisplayMode.Active) {
        if (smart == null || mode != DisplayMode.Active) return@LaunchedEffect
        val wait = manualAt.longValue + SMART_PAUSE_MS - SystemClock.elapsedRealtime()
        if (wait > 0) delay(wait)
        val i = pages.indexOf(smart)
        if (i >= 0 && i != pager.currentPage) pager.animateScrollToPage(i)
    }

    Box(Modifier.fillMaxSize().forceLandscape().reportTouches(presence).burnInShift(enabled = !demo)) {
        Crossfade(mode, animationSpec = tween(1200), label = "mode") { m ->
            when (m) {
                DisplayMode.Night -> DesignCanvas(844.dp, 390.dp) { NightScreen(data) }
                DisplayMode.Glance -> DesignCanvas(844.dp, 390.dp) { GlanceScreen(data) }
                DisplayMode.Active -> Pages(pages, pager, settings.theme, data, prefs) { timerSheet.value = true }
            }
        }
        // Overlays share the same landscape canvas so they line up with the pages.
        DesignCanvas(844.dp, 390.dp) {
            if (mode != DisplayMode.Night) BannerOverlay(data, presence)
            if (mode == DisplayMode.Active) TimerOverlay(data, prefs)
            if (timerSheet.value) QuickTimerSheet(
                onStart = { m ->
                    prefs.update { it.copy(timerEndAt = System.currentTimeMillis() + m * 60_000L) }
                    timerSheet.value = false
                },
                onDismiss = { timerSheet.value = false },
            )
            RingingOverlay(data, prefs, presence)
        }
    }
}

@Composable
private fun Pages(pages: List<PageId>, pager: PagerState, theme: StyleTheme, data: StandByData, prefs: Prefs, onClockLongPress: () -> Unit) {
    Box(Modifier.fillMaxSize()) {
        HorizontalPager(pager, Modifier.fillMaxSize(), key = { pages[it] }) { i ->
            // Clock page: long-press for a quick timer, double-tap to try the next clock face.
            val clockPress = if (pages[i] == PageId.Clock) Modifier.pointerInput(Unit) {
                detectTapGestures(
                    onLongPress = { onClockLongPress() },
                    onDoubleTap = { prefs.update { s -> s.copy(clockFace = ClockFace.entries[(s.clockFace.ordinal + 1) % ClockFace.entries.size]) } },
                )
            } else Modifier
            // Depth: pages shrink a little and dim as they slide away.
            val depth = Modifier.graphicsLayer {
                val off = kotlin.math.abs((pager.currentPage - i) + pager.currentPageOffsetFraction).coerceIn(0f, 1f)
                alpha = 1f - .35f * off
                scaleX = 1f - .06f * off
                scaleY = scaleX
            }
            Box(clockPress.then(depth)) { DesignCanvas(844.dp, 390.dp) {
                Screen(pages[i], theme, data, prefs) { idx ->
                    prefs.update { s ->
                        s.copy(checklist = s.checklist.mapIndexed { j, it -> if (j == idx) it.copy(done = !it.done) else it })
                    }
                }
            } }
        }
        if (theme == StyleTheme.Colorful) {
            // Dots show while swiping and fade 5 s after, so they don't sit on the same pixels all night.
            val dotsVisible = remember { mutableStateOf(true) }
            LaunchedEffect(pager.currentPage, pager.isScrollInProgress) {
                dotsVisible.value = true
                if (!pager.isScrollInProgress) {
                    delay(5_000)
                    dotsVisible.value = false
                }
            }
            val alpha by animateFloatAsState(if (dotsVisible.value) 1f else 0f, tween(600), label = "dots")
            Row(
                Modifier.align(Alignment.BottomCenter).padding(bottom = 14.dp).graphicsLayer { this.alpha = alpha },
                horizontalArrangement = Arrangement.spacedBy(7.dp),
            ) {
                repeat(pages.size) { i ->
                    val on = i == pager.currentPage
                    Box(
                        Modifier.size(if (on) 18.dp else 6.dp, 6.dp).clip(CircleShape)
                            .background(if (on) Color.White else Color.White.copy(alpha = .3f)),
                    )
                }
            }
        }
    }
}

@Composable
private fun Screen(page: PageId, theme: StyleTheme, d: StandByData, prefs: Prefs, onToggleItem: (Int) -> Unit) {
    val colorful = theme == StyleTheme.Colorful
    when (page) {
        PageId.Clock -> ClockPage(d)
        PageId.Charging -> if (colorful) ColorfulCharging(d) else MinimalCharging(d)
        PageId.WeatherCalendar -> ColorfulWeatherCalendar(d)
        PageId.NowPlaying -> ColorfulNowPlaying(d)
        PageId.Widgets -> ColorfulWidgets(d, onToggleItem) { slot, type ->
            prefs.update { s -> s.copy(widgets = s.widgets.toMutableList().also { it[slot] = type }) }
        }
        PageId.Day -> MinimalDay(d)
        PageId.Analog -> MinimalAnalog(d)
        PageId.Photos -> PhotosScreen(d)
        PageId.Home -> HomeScreen(d)
        PageId.Abstract -> AbstractScreen(d)
    }
}
