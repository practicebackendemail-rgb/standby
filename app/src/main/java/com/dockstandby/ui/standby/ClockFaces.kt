package com.dockstandby.ui.standby

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dockstandby.data.ClockFace
import com.dockstandby.data.StyleTheme
import com.dockstandby.ui.theme.C
import com.dockstandby.ui.theme.CssLine
import com.dockstandby.ui.theme.Inter
import com.dockstandby.ui.theme.Nunito
import com.dockstandby.ui.theme.colors
import com.dockstandby.ui.theme.longDate
import com.dockstandby.ui.theme.shortDate
import com.dockstandby.ui.theme.weekday
import kotlinx.coroutines.delay
import java.time.format.DateTimeFormatter
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/** The Clock page: the theme clock from the mockups, or one of the alternative faces. */
@Composable
fun ClockPage(d: StandByData) {
    when (d.settings.clockFace) {
        ClockFace.Theme -> if (d.settings.theme == StyleTheme.Colorful) ColorfulClock(d) else MinimalClock(d)
        ClockFace.Flip -> FlipClock(d)
        ClockFace.Word -> WordClock(d)
        ClockFace.Neon -> NeonClock(d)
        ClockFace.Orbit -> OrbitClock(d)
        ClockFace.DotMatrix -> DotMatrixClock(d)
        ClockFace.Stacked -> StackedClock(d)
    }
}

/** Hours (respecting 12/24 h, always two digits) and minutes. */
private fun StandByData.hm(): Pair<String, String> {
    val h = if (is24h) now.hour else (now.hour % 12).let { if (it == 0) 12 else it }
    return "%02d".format(h) to "%02d".format(now.minute)
}

private val accent get() = C.Orange

// ---------------- Flip ----------------

/** Split-flap cards; each card flips its top half down when its digit changes. */
@Composable
private fun FlipClock(d: StandByData) {
    val (h, m) = d.hm()
    val ampm = if (d.is24h) null else if (d.now.hour < 12) "AM" else "PM"
    Box(Modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
                FlipCard(h[0]); FlipCard(h[1])
                Column(Modifier.padding(horizontal = 6.dp), verticalArrangement = Arrangement.spacedBy(36.dp)) {
                    repeat(2) { Box(Modifier.size(14.dp).clip(RoundedCornerShape(7.dp)).background(Color(0xFF3A3A3C))) }
                }
                FlipCard(m[0]); FlipCard(m[1])
            }
            Row(Modifier.padding(top = 22.dp), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                Text(d.now.longDate(), color = C.Gray3, fontFamily = Inter, fontWeight = FontWeight.Medium, fontSize = 18.sp)
                ampm?.let { Text(it, color = accent, fontFamily = Inter, fontWeight = FontWeight.Bold, fontSize = 18.sp) }
            }
        }
    }
}

private val cardW = 150.dp
private val cardH = 230.dp

@Composable
private fun FlipCard(digit: Char) {
    var shown by remember { mutableStateOf(digit) }
    var previous by remember { mutableStateOf(digit) }
    val flip = remember { Animatable(1f) } // 0 → 0.5: old top folds down; 0.5 → 1: new bottom unfolds
    LaunchedEffect(digit) {
        if (digit == shown) return@LaunchedEffect
        previous = shown
        shown = digit
        flip.snapTo(0f)
        flip.animateTo(.5f, tween(220, easing = FastOutLinearInEasing))
        flip.animateTo(1f, tween(260, easing = LinearOutSlowInEasing))
    }
    val p = flip.value
    Box(Modifier.size(cardW, cardH)) {
        // Static halves: new digit on top, old digit at the bottom until the flap lands.
        Half(shown, top = true)
        Half(if (p < .5f) previous else shown, top = false)
        // The moving flap.
        if (p < .5f) {
            Half(previous, top = true, Modifier.graphicsLayer {
                transformOrigin = androidx.compose.ui.graphics.TransformOrigin(.5f, 1f)
                rotationX = -180f * p
                cameraDistance = 16 * density
            })
        } else if (p < 1f) {
            Half(shown, top = false, Modifier.graphicsLayer {
                transformOrigin = androidx.compose.ui.graphics.TransformOrigin(.5f, 0f)
                rotationX = 180f * (1f - p)
                cameraDistance = 16 * density
            })
        }
        // Hinge line.
        Box(Modifier.align(Alignment.Center).fillMaxWidth().height(3.dp).background(Color.Black))
    }
}

/** One half of a flap card: the digit drawn full size, clipped to the top or bottom half. */
@Composable
private fun Half(digit: Char, top: Boolean, modifier: Modifier = Modifier) {
    val shape = if (top) RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp) else RoundedCornerShape(bottomStart = 18.dp, bottomEnd = 18.dp)
    Box(
        modifier.size(cardW, cardH / 2).offset(y = if (top) 0.dp else cardH / 2).clip(shape)
            .background(if (top) Color(0xFF242426) else Color(0xFF1C1C1E)),
    ) {
        // Full-height digit box overflowing the half (requiredSize centres it), shifted so the
        // top half shows the upper part of the digit and the bottom half the lower part.
        Box(Modifier.requiredSize(cardW, cardH).offset(y = if (top) cardH / 4 else -cardH / 4), contentAlignment = Alignment.Center) {
            Text(
                digit.toString(),
                style = TextStyle(lineHeightStyle = CssLine, color = C.Text, fontFamily = Nunito, fontWeight = FontWeight.Black, fontSize = 190.sp, lineHeight = 200.sp),
            )
        }
    }
}

// ---------------- Word ----------------

/**
 * The time in words on a letter grid (an original layout). Lit words glow; the rest stay dim.
 * Minutes round down to five; dots in the corner count the extra 0–4 minutes.
 */
private val wordRows: List<List<Pair<String, String>>> = listOf(
    // (word, id) — ids disambiguate repeated words like FIVE and TEN.
    listOf("IT" to "it", "R" to "", "IS" to "is", "B" to "", "HALF" to "half", "Y" to "", "TEN" to "m10"),
    listOf("QUARTER" to "quarter", "O" to "", "TWENTY" to "twenty"),
    listOf("FIVE" to "m5", "L" to "", "MINUTES" to "minutes", "U" to "", "TO" to "to"),
    listOf("PAST" to "past", "N" to "", "ONE" to "h1", "TWO" to "h2", "THREE" to "h3"),
    listOf("FOUR" to "h4", "FIVE" to "h5", "Q" to "", "SIX" to "h6", "SEVEN" to "h7"),
    listOf("EIGHT" to "h8", "NINE" to "h9", "TEN" to "h10", "ELEVEN" to "h11"),
    listOf("TWELVE" to "h12", "D" to "", "O'CLOCK" to "oclock"),
)

private fun litWords(hour24: Int, minute: Int): Set<String> {
    val m = minute / 5 * 5
    val lit = mutableSetOf("it", "is")
    var h = hour24 % 12
    when (m) {
        0 -> lit += "oclock"
        5, 55 -> lit += setOf("m5", "minutes")
        10, 50 -> lit += setOf("m10", "minutes")
        15, 45 -> lit += "quarter"
        20, 40 -> lit += setOf("twenty", "minutes")
        25, 35 -> lit += setOf("twenty", "m5", "minutes")
        30 -> lit += "half"
    }
    if (m in 5..30) lit += "past" else if (m > 30) { lit += "to"; h += 1 }
    lit += "h${(h % 12).let { if (it == 0) 12 else it }}"
    return lit
}

@Composable
private fun WordClock(d: StandByData) {
    val lit = litWords(d.now.hour, d.now.minute)
    val extra = d.now.minute % 5
    val glow = d.settings.palette.colors.a
    Box(Modifier.fillMaxSize().background(Color.Black).padding(horizontal = 60.dp, vertical = 28.dp)) {
        Column(Modifier.align(Alignment.Center), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            wordRows.forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    row.forEach { (word, id) ->
                        val on = id in lit
                        Text(
                            word,
                            style = TextStyle(
                                lineHeightStyle = CssLine,
                                color = if (on) Color.White else Color(0xFF2A2A2C),
                                shadow = if (on) Shadow(glow, blurRadius = 28f) else null,
                                fontFamily = Inter, fontWeight = if (on) FontWeight.Bold else FontWeight.Medium,
                                fontSize = 34.sp, lineHeight = 44.sp, letterSpacing = 6.sp,
                            ),
                        )
                    }
                }
            }
        }
        Row(Modifier.align(Alignment.BottomEnd), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            repeat(4) { i -> Box(Modifier.size(8.dp).clip(RoundedCornerShape(4.dp)).background(if (i < extra) glow else Color(0xFF2A2A2C))) }
        }
    }
}

// ---------------- Neon ----------------

/** Glowing neon tubes with a rare flicker and a faint reflection below. */
@Composable
private fun NeonClock(d: StandByData) {
    val (h, m) = d.hm()
    val p = d.settings.palette.colors
    // A brief flicker every ~20–40 s, like a real tube.
    val flicker by produceState(1f) {
        val rnd = Random(System.nanoTime())
        while (true) {
            delay(20_000L + rnd.nextLong(20_000))
            repeat(3) { value = .45f; delay(60); value = 1f; delay(90) }
        }
    }
    val neon = TextStyle(
        lineHeightStyle = CssLine, color = Color.White, fontFamily = Nunito, fontWeight = FontWeight.Light,
        fontSize = 170.sp, lineHeight = 170.sp, letterSpacing = 4.sp,
    )
    Box(Modifier.fillMaxSize().background(Color(0xFF07040C)), contentAlignment = Alignment.Center) {
        Box(Modifier.size(620.dp, 220.dp).glow(p.c, .25f, 120.dp))
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.graphicsLayer { alpha = flicker }) {
                // Outer glow layers, then the white-hot core.
                Text("$h:$m", style = neon.copy(color = p.c, shadow = Shadow(p.c, blurRadius = 90f)))
                Text("$h:$m", style = neon.copy(color = p.b, shadow = Shadow(p.b, blurRadius = 40f)))
                Text("$h:$m", style = neon.copy(color = Color.White.copy(alpha = .92f), shadow = Shadow(p.a, blurRadius = 12f)))
            }
            // Reflection on a glossy surface.
            Box(
                Modifier.height(70.dp).graphicsLayer { scaleY = -1f; alpha = .18f * flicker }
                    .drawWithContent {
                        drawContent()
                        drawRect(Brush.verticalGradient(listOf(Color.Transparent, Color(0xFF07040C))), blendMode = androidx.compose.ui.graphics.BlendMode.DstOut)
                    },
            ) {
                Text("$h:$m", style = neon.copy(color = p.b))
            }
            Text(d.now.weekday() + "  ·  " + d.now.format(DateTimeFormatter.ofPattern("d MMMM")).uppercase(), color = p.a.copy(alpha = .8f), fontFamily = Inter, fontSize = 15.sp, letterSpacing = 4.sp)
        }
    }
}

// ---------------- Solar orbit ----------------

/** Hours, minutes and seconds as planets circling a sun; the month's days ring the outside. */
@Composable
private fun OrbitClock(d: StandByData) {
    val t = d.now
    val sec = t.second + t.nano / 1e9f
    val min = t.minute + sec / 60f
    val hr = t.hour % 12 + min / 60f
    val p = d.settings.palette.colors
    val days = t.toLocalDate().lengthOfMonth()
    Row(Modifier.fillMaxSize().background(Color.Black), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(60.dp, Alignment.CenterHorizontally)) {
        Canvas(Modifier.size(360.dp)) {
            val c = center
            val u = size.width / 360f
            fun at(r: Float, deg: Float) = Offset(c.x + r * u * sin(deg * PI / 180).toFloat(), c.y - r * u * cos(deg * PI / 180).toFloat())
            // Day-of-month ring.
            for (i in 1..days) {
                val on = i == t.dayOfMonth
                drawCircle(if (on) p.a else Color(0xFF2A2A2C), (if (on) 5f else 2.5f) * u, at(172f, (i - 1) * 360f / days))
            }
            // Orbits.
            listOf(70f, 110f, 150f).forEach { drawCircle(Color(0xFF1C1C1E), it * u, c, style = Stroke(1.5f * u)) }
            // Sun.
            drawCircle(Brush.radialGradient(listOf(Color(0xFFFFE066), p.b.copy(alpha = .0f)), c, 60 * u), 60 * u, c)
            drawCircle(Color(0xFFFFE066), 22 * u, c)
            // Planets: hour (big), minute, second (small, fast).
            drawCircle(p.c, 15 * u, at(70f, hr * 30f))
            drawCircle(p.b, 10 * u, at(110f, min * 6f))
            drawCircle(Color.White, 5 * u, at(150f, sec * 6f))
        }
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(t.format(DateTimeFormatter.ofPattern(if (d.is24h) "HH:mm" else "h:mm")), color = C.Text, fontFamily = Inter, fontWeight = FontWeight.ExtraLight, fontSize = 72.sp, lineHeight = 76.sp)
            Text(t.longDate(), color = C.Gray3, fontFamily = Inter, fontSize = 16.sp)
            Row(Modifier.padding(top = 18.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Legend(p.c, "Hour"); Legend(p.b, "Minute"); Legend(Color.White, "Second")
            }
        }
    }
}

@Composable
private fun Legend(c: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(Modifier.size(8.dp).clip(RoundedCornerShape(4.dp)).background(c))
        Text(label, color = C.Gray, fontFamily = Inter, fontSize = 12.sp)
    }
}

// ---------------- Dot matrix ----------------

/** 5×7 dot-matrix glyphs (rows top to bottom, 5 bits each, MSB = left). */
private val dotFont: Map<Char, IntArray> = mapOf(
    '0' to intArrayOf(0b01110, 0b10001, 0b10011, 0b10101, 0b11001, 0b10001, 0b01110),
    '1' to intArrayOf(0b00100, 0b01100, 0b00100, 0b00100, 0b00100, 0b00100, 0b01110),
    '2' to intArrayOf(0b01110, 0b10001, 0b00001, 0b00010, 0b00100, 0b01000, 0b11111),
    '3' to intArrayOf(0b11110, 0b00001, 0b00001, 0b01110, 0b00001, 0b00001, 0b11110),
    '4' to intArrayOf(0b00010, 0b00110, 0b01010, 0b10010, 0b11111, 0b00010, 0b00010),
    '5' to intArrayOf(0b11111, 0b10000, 0b11110, 0b00001, 0b00001, 0b10001, 0b01110),
    '6' to intArrayOf(0b00110, 0b01000, 0b10000, 0b11110, 0b10001, 0b10001, 0b01110),
    '7' to intArrayOf(0b11111, 0b00001, 0b00010, 0b00100, 0b01000, 0b01000, 0b01000),
    '8' to intArrayOf(0b01110, 0b10001, 0b10001, 0b01110, 0b10001, 0b10001, 0b01110),
    '9' to intArrayOf(0b01110, 0b10001, 0b10001, 0b01111, 0b00001, 0b00010, 0b01100),
    ':' to intArrayOf(0b0, 0b00100, 0b00100, 0b0, 0b00100, 0b00100, 0b0),
    ' ' to intArrayOf(0, 0, 0, 0, 0, 0, 0),
)

/** An LED board: every dot drawn, lit ones glowing amber. The colon pulses with the seconds. */
@Composable
private fun DotMatrixClock(d: StandByData) {
    val (h, m) = d.hm()
    val colonOn = d.now.second % 2 == 0
    val text = h + (if (colonOn) ":" else " ") + m
    val litColor = Color(0xFFFFB020)
    Column(Modifier.fillMaxSize().background(Color.Black), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Canvas(Modifier.size(700.dp, 220.dp)) {
            val cols = text.length * 6 - 1
            val pitch = minOf(size.width / cols, size.height / 7)
            val r = pitch * .38f
            val x0 = (size.width - cols * pitch) / 2
            text.forEachIndexed { ci, ch ->
                val glyph = dotFont[ch] ?: return@forEachIndexed
                for (row in 0 until 7) for (col in 0 until 5) {
                    val on = glyph[row] shr (4 - col) and 1 == 1
                    val center = Offset(x0 + (ci * 6 + col) * pitch + pitch / 2, row * pitch + pitch / 2)
                    if (on) drawCircle(litColor.copy(alpha = .25f), r * 1.9f, center)
                    drawCircle(if (on) litColor else Color(0xFF1E1A12), r, center)
                }
            }
        }
        Text(d.now.shortDate().uppercase(), Modifier.padding(top = 24.dp), color = litColor.copy(alpha = .7f), fontFamily = Inter, fontWeight = FontWeight.Medium, fontSize = 16.sp, letterSpacing = 5.sp)
    }
}

// ---------------- Stacked ----------------

/** Huge hours stacked over minutes on the left; the day reads down the right edge. */
@Composable
private fun StackedClock(d: StandByData) {
    val (h, m) = d.hm()
    val p = d.settings.palette.colors
    val colorful = d.settings.theme == StyleTheme.Colorful
    Row(Modifier.fillMaxSize().background(Color.Black).padding(horizontal = 56.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy((-34).dp)) {
            val style = TextStyle(lineHeightStyle = CssLine, fontFamily = Nunito, fontWeight = FontWeight.Black, fontSize = 168.sp, lineHeight = 168.sp, letterSpacing = (-6).sp)
            Text(h, style = style.copy(color = if (colorful) p.a else C.Text))
            Text(m, style = style.copy(color = if (colorful) p.c else C.Gray3))
        }
        Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(d.now.weekday(), color = if (colorful) p.b else accent, fontFamily = Inter, fontWeight = FontWeight.Bold, fontSize = 26.sp, letterSpacing = 3.sp)
            Text(d.now.format(DateTimeFormatter.ofPattern("d MMMM")), color = C.Gray3, fontFamily = Inter, fontSize = 20.sp)
            if (!d.is24h) Text(if (d.now.hour < 12) "AM" else "PM", color = C.Gray, fontFamily = Inter, fontSize = 16.sp, letterSpacing = 3.sp)
            Box(Modifier.width(60.dp).height(2.dp).background(Color(0xFF2C2C2E)))
            Text("${d.battery.level}%", color = C.Gray, fontFamily = Inter, fontSize = 16.sp)
        }
    }
}
