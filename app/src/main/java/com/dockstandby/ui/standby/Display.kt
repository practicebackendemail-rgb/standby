package com.dockstandby.ui.standby

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.os.SystemClock
import android.service.dreams.DreamService
import android.view.Window
import android.view.WindowManager
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dockstandby.data.StyleTheme
import com.dockstandby.ui.theme.C
import com.dockstandby.ui.theme.CssLine
import com.dockstandby.ui.theme.Inter
import com.dockstandby.ui.theme.Nunito
import com.dockstandby.ui.theme.clock
import com.dockstandby.ui.theme.colors
import com.dockstandby.ui.theme.longDate
import com.dockstandby.ui.theme.shortDate
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.log10
import kotlin.math.sin

/** What the screen is doing: full StandBy, the dim always-on clock, or the red night clock. */
enum class DisplayMode { Active, Glance, Night }

/** Tracks the last time someone touched or approached the phone. */
class Presence {
    var lastAt by mutableLongStateOf(SystemClock.elapsedRealtime())
        private set

    fun poke(reason: String) {
        lastAt = SystemClock.elapsedRealtime()
    }
}

/** Becomes true once [dimAfterMin] minutes pass without [Presence.poke]; 0 disables it. */
@Composable
fun rememberIdle(presence: Presence, dimAfterMin: Int): State<Boolean> {
    val idle = remember { mutableStateOf(false) }
    LaunchedEffect(presence.lastAt, dimAfterMin) {
        idle.value = false
        if (dimAfterMin <= 0) return@LaunchedEffect
        delay(dimAfterMin * 60_000L)
        idle.value = true
    }
    return idle
}

/** Any touch anywhere counts as presence; the touch is not consumed. */
fun Modifier.reportTouches(presence: Presence): Modifier = pointerInput(presence) {
    awaitPointerEventScope {
        while (true) {
            awaitPointerEvent(PointerEventPass.Initial)
            presence.poke("touch")
        }
    }
}

private fun Context.hostWindow(): Window? = when (this) {
    is Activity -> window
    is DreamService -> window
    is ContextWrapper -> baseContext.hostWindow()
    else -> null
}

/**
 * Drives the window brightness for [mode]: follows the room's light when active, drops to a low
 * glance level when idle, and to the minimum for night mode. Fades between levels and hands
 * control back to the system when StandBy closes.
 */
@Composable
fun BrightnessController(mode: DisplayMode, lux: Float?) {
    val window = LocalContext.current.hostWindow() ?: return
    // log curve: ~2% in the dark, ~35% at 10 lx (dim room), ~67% at 100 lx, full at 1000 lx+.
    // Only moves when the room changes by more than 5 points, so sensor jitter doesn't keep
    // restarting the fade (which rewrote the window ~70 times a second).
    val settled = remember { mutableStateOf<Float?>(null) }
    lux?.let { (log10(it + 1f) / 3f).coerceIn(.02f, 1f) }?.let { raw ->
        val s = settled.value
        if (s == null || kotlin.math.abs(raw - s) > .05f) settled.value = raw
    }
    // Before the first light reading, leave the system's own brightness alone.
    val ambient = settled.value ?: return
    val target = when (mode) {
        DisplayMode.Active -> ambient
        DisplayMode.Glance -> minOf(ambient, .04f)
        DisplayMode.Night -> .01f
    }
    val level by animateFloatAsState(target, tween(1500), label = "brightness")
    LaunchedEffect(level) {
        window.attributes = window.attributes.apply { screenBrightness = level }
    }
    DisposableEffect(window) {
        onDispose {
            window.attributes = window.attributes.apply {
                screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
            }
        }
    }
}

/**
 * Burn-in protection: every minute the whole screen drifts to a new point on a slow ±4 dp
 * Lissajous path (17 / 13 minute periods), slightly scaled up so edges never show black.
 */
@Composable
fun Modifier.burnInShift(enabled: Boolean): Modifier {
    if (!enabled) return this
    val minute by produceState(System.currentTimeMillis() / 60_000) {
        while (true) {
            delay(60_000 - System.currentTimeMillis() % 60_000)
            value = System.currentTimeMillis() / 60_000
        }
    }
    val x by animateFloatAsState(4f * sin(2 * PI * minute / 17).toFloat(), tween(3000), label = "shiftX")
    val y by animateFloatAsState(4f * sin(2 * PI * minute / 13).toFloat(), tween(3000), label = "shiftY")
    return graphicsLayer {
        translationX = x.dp.toPx()
        translationY = y.dp.toPx()
        scaleX = 1.025f
        scaleY = 1.025f
    }
}

/**
 * The dim always-on clock shown after the idle timeout. Deliberately static (no infinite
 * animations) so the GPU can rest; only the minute changes.
 */
@Composable
fun GlanceScreen(d: StandByData) {
    val colorful = d.settings.theme == StyleTheme.Colorful
    Box(Modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            if (colorful) {
                val p = d.settings.palette.colors
                Text(
                    d.now.clock(d.is24h),
                    style = TextStyle(
                        lineHeightStyle = CssLine, brush = cssLinearGradient(120f, 0f to p.a, .55f to p.b, 1f to p.c),
                        fontFamily = Nunito, fontWeight = FontWeight.Black, fontSize = 168.sp, lineHeight = 152.sp, letterSpacing = (-6).sp,
                    ),
                    maxLines = 1, softWrap = false,
                )
            } else {
                val (h, m) = d.now.clock(d.is24h).split(":")
                Text(
                    buildAnnotatedString { append(h); withStyle(SpanStyle(color = C.Orange)) { append(":") }; append(m) },
                    color = C.Text, fontFamily = Inter, fontWeight = FontWeight.Thin, fontSize = 200.sp, lineHeight = 190.sp,
                    letterSpacing = (-8).sp, maxLines = 1, softWrap = false,
                )
            }
            Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                val dim = Color.White.copy(alpha = .55f)
                Text(if (colorful) d.now.longDate() else d.now.shortDate().uppercase(), color = dim, fontFamily = Inter, fontSize = 17.sp, letterSpacing = if (colorful) 0.sp else 2.4.sp)
                Text("${d.battery.level}%", color = dim, fontFamily = Inter, fontSize = 17.sp)
            }
        }
    }
}
