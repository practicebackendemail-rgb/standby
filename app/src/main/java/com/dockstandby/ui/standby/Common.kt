package com.dockstandby.ui.standby

import android.os.Build
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.InfiniteTransition
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.StartOffset
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.State
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import com.dockstandby.data.BatteryInfo
import com.dockstandby.data.CalEvent
import com.dockstandby.data.NowPlaying
import com.dockstandby.data.Settings
import com.dockstandby.data.Weather
import java.time.LocalDateTime
import kotlin.math.cos
import kotlin.math.sin

/** Everything a StandBy screen may show, gathered once at the root. */
class StandByData(
    val now: LocalDateTime,
    val is24h: Boolean,
    val battery: BatteryInfo,
    val events: List<CalEvent>,
    val alarm: LocalDateTime?,
    val weather: Weather?,
    val nowPlaying: NowPlaying?,
    val steps: Int?,
    val settings: Settings,
) {
    /** Events that haven't ended yet, all-day ones first. */
    val upcoming get() = events.filter { it.allDay || it.end.isAfter(now) }
    val nextEvent get() = events.firstOrNull { !it.allDay && it.end.isAfter(now) }
}

/**
 * Lays out [content] on a fixed design canvas (dp == mockup px) and scales it to fit the
 * available space, so every screen matches its mockup on any phone or tablet.
 */
@Composable
fun DesignCanvas(width: Dp, height: Dp, content: @Composable () -> Unit) {
    BoxWithConstraints(Modifier, contentAlignment = Alignment.Center) {
        val scale = min(maxWidth / width.value, maxHeight / height.value).value
        val base = LocalDensity.current
        CompositionLocalProvider(LocalDensity provides Density(base.density * scale, 1f)) {
            Box(Modifier.requiredSize(width, height)) { content() }
        }
    }
}

/**
 * Always lays [content] out in landscape. If the window is portrait (e.g. the screen saver on a
 * phone with rotation locked) the content is measured with swapped sides and rotated 90°.
 */
fun Modifier.forceLandscape(): Modifier = layout { measurable, c ->
    if (c.maxWidth >= c.maxHeight) {
        val p = measurable.measure(c)
        layout(p.width, p.height) { p.place(0, 0) }
    } else {
        val p = measurable.measure(Constraints.fixed(c.maxHeight, c.maxWidth))
        layout(c.maxWidth, c.maxHeight) {
            p.placeWithLayer((c.maxWidth - p.width) / 2, (c.maxHeight - p.height) / 2) { rotationZ = 90f }
        }
    }
}

/**
 * Soft blurred blob, the equivalent of CSS `border-radius:50%; filter: blur(sigma)`.
 * Uses a real Gaussian blur on API 31+, and a radial-gradient approximation below.
 */
fun Modifier.glow(color: Color, alpha: Float = 1f, sigma: Dp = 90.dp): Modifier =
    if (Build.VERSION.SDK_INT >= 31) {
        // RenderEffect radius r maps to sigma ≈ 0.57735·r + 0.5.
        this.blur(sigma / 0.57735f, BlurredEdgeTreatment.Unbounded).background(color.copy(alpha = alpha), CircleShape)
    } else drawBehind {
        val r = size.minDimension / 2
        scale(size.width / (2 * r), size.height / (2 * r), pivot = center) {
            drawCircle(
                Brush.radialGradient(
                    0f to color.copy(alpha = alpha),
                    0.5f to color.copy(alpha = alpha * 0.6f),
                    1f to color.copy(alpha = 0f),
                    center = center,
                    radius = r * 1.6f,
                ),
                radius = r * 1.6f,
                center = center,
            )
        }
    }

/** CSS `linear-gradient(<deg>, ...)` over a box: the gradient line runs corner-to-corner along [deg]. */
fun cssLinearGradient(deg: Float, vararg stops: Pair<Float, Color>) = object : androidx.compose.ui.graphics.ShaderBrush() {
    override fun createShader(size: androidx.compose.ui.geometry.Size): androidx.compose.ui.graphics.Shader {
        val rad = Math.toRadians(deg.toDouble())
        val dx = sin(rad).toFloat()
        val dy = -cos(rad).toFloat()
        val half = (kotlin.math.abs(size.width * dx) + kotlin.math.abs(size.height * dy)) / 2
        val c = Offset(size.width / 2, size.height / 2)
        return androidx.compose.ui.graphics.LinearGradientShader(
            from = c - Offset(dx, dy) * half,
            to = c + Offset(dx, dy) * half,
            colors = stops.map { it.second },
            colorStops = stops.map { it.first },
        )
    }
}

val CssEase = CubicBezierEasing(.25f, .1f, .25f, 1f)
val CssEaseInOut = CubicBezierEasing(.42f, 0f, .58f, 1f)
val CssEaseIn = CubicBezierEasing(.42f, 0f, 1f, 1f)

/**
 * A CSS `0%,100%{from} 50%{to}` ease-in-out keyframe loop with full period [periodMs].
 * [delayMs] behaves like CSS animation-delay (only once, not every cycle).
 */
@Composable
fun InfiniteTransition.pingPong(from: Float, to: Float, periodMs: Int, delayMs: Int = 0): State<Float> =
    animateFloat(
        from, to,
        infiniteRepeatable(tween(periodMs / 2, easing = CssEaseInOut), RepeatMode.Reverse, StartOffset(delayMs)),
        label = "pp",
    )

