package com.dockstandby.ui.standby

import android.graphics.Bitmap
import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dockstandby.data.AbstractStyle
import com.dockstandby.ui.theme.Inter
import com.dockstandby.ui.theme.colors
import com.dockstandby.ui.theme.longDate
import java.time.format.DateTimeFormatter
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/** Seconds since the page appeared, updated every frame (drives all abstract animation). */
@Composable
private fun rememberSeconds() = produceState(0f) {
    val start = withFrameNanosCompat { it }
    while (true) value = withFrameNanosCompat { (it - start) / 1e9f }
}

private suspend fun <R> withFrameNanosCompat(block: (Long) -> R): R = androidx.compose.runtime.withFrameNanos(block)

/** Ambient art; the time stays small in the corner so the picture is the point. */
@Composable
fun AbstractScreen(d: StandByData) {
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        when (d.settings.abstractStyle) {
            AbstractStyle.Blobs -> Blobs(d)
            AbstractStyle.Generative -> Generative(d)
            AbstractStyle.Particles -> Particles(d)
            AbstractStyle.Waves -> Waves(d)
        }
        Column(Modifier.align(Alignment.BottomStart).padding(start = 36.dp, bottom = 28.dp)) {
            Text(
                d.now.format(DateTimeFormatter.ofPattern(if (d.is24h) "HH:mm" else "h:mm")),
                color = Color.White.copy(alpha = .9f), fontFamily = Inter, fontWeight = FontWeight.Light, fontSize = 40.sp, lineHeight = 44.sp,
            )
            Text(d.now.longDate(), color = Color.White.copy(alpha = .6f), fontFamily = Inter, fontSize = 14.sp)
        }
    }
}

// ---------------- Gradient blobs ----------------

/** Five large blurred colour fields drifting on slow Lissajous paths, like a lava lamp. */
@Composable
private fun Blobs(d: StandByData) {
    val t by rememberSeconds()
    val p = d.settings.palette.colors
    val colors = listOf(p.a, p.b, p.c, lerp(p.a, p.c, .5f), lerp(p.b, Color(0xFF5E5CE6), .5f))
    Box(Modifier.fillMaxSize()) {
        colors.forEachIndexed { i, c ->
            val k = i + 1
            val x = 422 + 300 * sin(t * .05f * k + i)
            val y = 195 + 140 * cos(t * .037f * (6 - k) + i * 2)
            val s = 300 + 80 * sin(t * .08f + i)
            Box(Modifier.offset((x - s / 2).dp, (y - s / 2).dp).size(s.dp).glow(c, .75f, 90.dp))
        }
    }
}

// ---------------- Generative ----------------

/**
 * A new composition every minute, seeded by the time: hue follows the hour, shape count the
 * minute. Compositions crossfade.
 */
@Composable
private fun Generative(d: StandByData) {
    val minuteKey = d.now.hour * 60 + d.now.minute
    Crossfade(minuteKey, animationSpec = tween(2500), label = "gen") { key ->
        val hour = key / 60
        val minute = key % 60
        val baseHue = hour / 24f * 360f
        val shapes = remember(key) {
            val rnd = Random(key * 7919L + d.now.dayOfYear)
            List(8 + minute / 4) {
                GenShape(
                    kind = rnd.nextInt(3),
                    x = rnd.nextFloat(), y = rnd.nextFloat(),
                    size = .08f + rnd.nextFloat() * .35f,
                    hue = (baseHue + rnd.nextFloat() * 70f - 35f + 360f) % 360f,
                    rot = rnd.nextFloat() * 360f,
                    alpha = .35f + rnd.nextFloat() * .5f,
                )
            }
        }
        Canvas(Modifier.fillMaxSize()) {
            drawRect(Brush.verticalGradient(listOf(Color.hsv(baseHue, .55f, .14f), Color.Black)))
            shapes.forEach { s ->
                val c = Color.hsv(s.hue, .65f, .95f).copy(alpha = s.alpha)
                val center = Offset(s.x * size.width, s.y * size.height)
                val r = s.size * size.height
                when (s.kind) {
                    0 -> drawCircle(c, r, center)
                    1 -> rotate(s.rot, center) { drawRect(c, center - Offset(r, r / 3), Size(2 * r, 2 * r / 3)) }
                    else -> drawArc(c, s.rot, 200f, false, center - Offset(r, r), Size(2 * r, 2 * r), style = Stroke(r * .18f))
                }
            }
        }
    }
}

private class GenShape(val kind: Int, val x: Float, val y: Float, val size: Float, val hue: Float, val rot: Float, val alpha: Float)

// ---------------- Particles ----------------

private const val PARTICLES = 700

/**
 * Particles drift freely, then gather to spell the time for a few seconds, then scatter again.
 * Targets come from sampling the time rendered into a small offscreen bitmap.
 */
@Composable
private fun Particles(d: StandByData) {
    val t by rememberSeconds()
    val label = d.now.format(DateTimeFormatter.ofPattern(if (d.is24h) "HH:mm" else "h:mm"))
    val targets = remember(label) { textTargets(label, PARTICLES) }
    val seeds = remember { val r = Random(42); List(PARTICLES) { floatArrayOf(r.nextFloat(), r.nextFloat(), r.nextFloat() * 6.28f, .3f + r.nextFloat()) } }
    val p = d.settings.palette.colors
    Canvas(Modifier.fillMaxSize()) {
        // 14 s cycle: drift (0–5), gather (5–7), hold (7–11), scatter (11–14).
        val cycle = t % 14f
        val gather = when {
            cycle < 5f -> 0f
            cycle < 7f -> smooth((cycle - 5f) / 2f)
            cycle < 11f -> 1f
            else -> 1f - smooth((cycle - 11f) / 3f)
        }
        for (i in 0 until PARTICLES) {
            val s = seeds[i]
            val fx = (s[0] + .02f * sin(t * .3f * s[3] + s[2])) * size.width
            val fy = (s[1] + .03f * cos(t * .25f * s[3] + s[2])) * size.height
            val tg = targets[i]
            val tx = size.width * (.18f + .64f * tg.x)
            val ty = size.height * (.22f + .5f * tg.y)
            val x = fx + (tx - fx) * gather
            val y = fy + (ty - fy) * gather
            val c = lerp(p.a, p.c, s[0])
            drawCircle(c.copy(alpha = .35f + .5f * gather), 2.2f + 1.3f * s[3], Offset(x, y))
        }
    }
}

private fun smooth(x: Float) = x * x * (3 - 2 * x)

/** [count] points (0..1 in both axes) sampled from the lit pixels of [text]. */
private fun textTargets(text: String, count: Int): List<Offset> {
    val w = 220; val h = 80
    val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ALPHA_8)
    val canvas = android.graphics.Canvas(bmp)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 74f; typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD); textAlign = Paint.Align.CENTER
    }
    canvas.drawText(text, w / 2f, h * .82f, paint)
    val lit = ArrayList<Offset>()
    for (y in 0 until h step 2) for (x in 0 until w step 2) {
        if (bmp.getPixel(x, y) ushr 24 > 128) lit += Offset(x / w.toFloat(), y / h.toFloat())
    }
    bmp.recycle()
    if (lit.isEmpty()) return List(count) { Offset(.5f, .5f) }
    val r = Random(text.hashCode())
    return List(count) { lit[r.nextInt(lit.size)] }
}

// ---------------- Light waves ----------------

/** Sky colours through the day: night, dawn, day, dusk. */
private fun skyColors(hour: Float): List<Color> {
    val night = listOf(Color(0xFF0B1A3A), Color(0xFF2E1F6B), Color(0xFF5E5CE6))
    val dawn = listOf(Color(0xFFFF7A45), Color(0xFFFF2D92), Color(0xFFFFD60A))
    val day = listOf(Color(0xFF0A84FF), Color(0xFF64D2FF), Color(0xFF30D5C8))
    val dusk = listOf(Color(0xFFBF5AF2), Color(0xFFFF2D92), Color(0xFFFF9F0A))
    val stops = listOf(0f to night, 6f to dawn, 9f to day, 17f to day, 19.5f to dusk, 22f to night, 24f to night)
    val i = stops.indexOfLast { it.first <= hour }.coerceIn(0, stops.size - 2)
    val (h0, c0) = stops[i]
    val (h1, c1) = stops[i + 1]
    val f = if (h1 > h0) ((hour - h0) / (h1 - h0)).coerceIn(0f, 1f) else 0f
    return c0.indices.map { lerp(c0[it], c1[it], f) }
}

/** Layered translucent sine ribbons drifting sideways; colours follow the time of day. */
@Composable
private fun Waves(d: StandByData) {
    val t by rememberSeconds()
    val colors = skyColors(d.now.hour + d.now.minute / 60f)
    Canvas(Modifier.fillMaxSize()) {
        drawRect(Brush.verticalGradient(listOf(colors[0].copy(alpha = .35f), Color.Black)))
        for (layer in 0 until 5) {
            val c = colors[layer % colors.size]
            val amp = size.height * (.05f + .025f * layer)
            val base = size.height * (.35f + .1f * layer)
            val k = (1.2f + .4f * layer) * 2 * PI.toFloat() / size.width
            val speed = .15f + .07f * layer
            val path = Path().apply {
                moveTo(0f, size.height)
                var x = 0f
                while (x <= size.width) {
                    val y = base + amp * sin(k * x + t * speed + layer) + amp * .4f * sin(k * 2.3f * x - t * speed * .7f)
                    lineTo(x, y)
                    x += 8f
                }
                lineTo(size.width, size.height); close()
            }
            drawPath(path, Brush.verticalGradient(listOf(c.copy(alpha = .45f), c.copy(alpha = .05f)), startY = base - amp, endY = size.height))
        }
    }
}
