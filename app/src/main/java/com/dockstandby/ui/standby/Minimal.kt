package com.dockstandby.ui.standby

import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import com.dockstandby.ui.theme.CssLine
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dockstandby.data.ChargingAnimation
import com.dockstandby.data.label
import com.dockstandby.ui.theme.C
import com.dockstandby.ui.theme.Inter
import com.dockstandby.ui.theme.chargeColors
import com.dockstandby.ui.theme.formatMinutes
import com.dockstandby.ui.theme.hhmm24
import com.dockstandby.ui.theme.shortDate
import com.dockstandby.ui.theme.shortTime
import java.time.Duration
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
private fun Label(text: String) =
    Text(text.uppercase(), color = C.Gray, fontFamily = Inter, fontSize = 11.sp, letterSpacing = 2.4.sp)

// ---------------- Clock ----------------

@Composable
fun MinimalClock(d: StandByData) {
    val t = rememberInfiniteTransition(label = "blink")
    // steps(1): fully on for the first half, dim for the second.
    val colon by t.animateFloat(
        1f, 1f,
        infiniteRepeatable(keyframes { durationMillis = 2000; 1f at 0; 1f at 999; .25f at 1000; .25f at 1999 }),
        label = "colon",
    )
    val (h, m) = d.now.format(DateTimeFormatter.ofPattern(if (d.is24h) "HH:mm" else "h:mm")).split(":")
    Column(Modifier.fillMaxSize().background(Color.Black).padding(horizontal = 64.dp), verticalArrangement = Arrangement.Center) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(d.now.shortDate().uppercase(), color = C.Gray, fontFamily = Inter, fontSize = 13.sp, letterSpacing = 2.4.sp)
            Text("● ${d.battery.level}%", color = C.Orange, fontFamily = Inter, fontSize = 13.sp, letterSpacing = 2.4.sp)
        }
        RollingText(
            "$h:$m",
            style = TextStyle(lineHeightStyle = CssLine, color = C.Text, fontFamily = Inter, fontWeight = FontWeight.Thin, fontSize = 236.sp, lineHeight = 224.sp, letterSpacing = (-10).sp),
            modifier = Modifier.offset((-10).dp),
            colorAt = { _, c -> if (c == ':') C.Orange.copy(alpha = colon) else null },
        )
        Box(Modifier.padding(top = 4.dp).fillMaxWidth().height(1.dp).background(C.Hairline))
        Row(Modifier.padding(top = 14.dp).fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            val next = d.nextEvent
            Text(
                if (next != null) "Next · ${next.title} ${next.start.shortTime(d.is24h)}" else "Nothing else today",
                color = C.Gray, fontFamily = Inter, fontSize = 13.sp, letterSpacing = 1.sp, maxLines = 1,
            )
            d.weather?.let { Text("${it.temp}° ${it.city ?: ""}".trim(), color = C.Gray, fontFamily = Inter, fontSize = 13.sp, letterSpacing = 1.sp) }
        }
    }
}

// ---------------- Charging ----------------

@Composable
fun MinimalCharging(d: StandByData) {
    val b = d.battery
    val t = rememberInfiniteTransition(label = "breathe")
    val breathe by t.pingPong(.35f, 1f, 2400)
    val (c1, _) = chargeColors(b.level)
    Row(
        Modifier.fillMaxSize().background(Color.Black),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(70.dp, Alignment.CenterHorizontally),
    ) {
        Box(Modifier.size(300.dp), contentAlignment = Alignment.Center) {
            val orbit by t.animateFloat(0f, 360f, infiniteRepeatable(tween(4000, easing = LinearEasing)), label = "orbit")
            Canvas(Modifier.fillMaxSize()) {
                val u = size.width / 300f
                val r = 136 * u
                drawCircle(C.Hairline, r, style = Stroke(2 * u))
                drawArc(
                    c1, -90f, 360f * b.level / 100f, false,
                    topLeft = Offset(150 * u - r, 150 * u - r), size = androidx.compose.ui.geometry.Size(2 * r, 2 * r),
                    style = Stroke(2 * u, cap = StrokeCap.Round),
                )
                if (b.charging && d.settings.chargingAnimation != ChargingAnimation.Off) rotate(orbit) {
                    drawCircle(c1, 4 * u, Offset(150 * u, 14 * u))
                }
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("${b.level}", color = C.Text, fontFamily = Inter, fontWeight = FontWeight.Thin, fontSize = 104.sp, lineHeight = 104.sp, letterSpacing = (-4).sp)
                val animate = b.charging && d.settings.chargingAnimation != ChargingAnimation.Off
                Text(
                    when { b.full -> "FULL"; b.charging -> "CHARGING"; else -> "BATTERY" },
                    Modifier.padding(top = 6.dp).graphicsLayer { alpha = if (animate) breathe else 1f },
                    color = if (b.charging) c1 else C.Gray, fontFamily = Inter, fontSize = 12.sp, letterSpacing = 3.sp,
                )
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(22.dp)) {
            Stat("Source", b.source.label)
            Stat("Full in", when { b.full -> "Full"; !b.charging -> "—"; else -> b.minutesToFull?.let(::formatMinutes) ?: "—" })
            Stat("Temp", b.tempC?.let { "%.1f°".format(it) } ?: "—")
        }
    }
}

@Composable
private fun Stat(label: String, value: String) {
    Column {
        Label(label)
        Text(value, Modifier.padding(top = 4.dp), color = C.Text, fontFamily = Inter, fontWeight = FontWeight.Light, fontSize = 22.sp)
    }
}

// ---------------- Day ----------------

@Composable
fun MinimalDay(d: StandByData) {
    val w = d.weather
    Row(Modifier.fillMaxSize().background(Color.Black).padding(horizontal = 64.dp, vertical = 50.dp)) {
        Column(Modifier.weight(1f).fillMaxHeight().padding(end = 48.dp)) {
            Label(w?.city ?: "Weather")
            Text(w?.let { "${it.temp}°" } ?: "—", Modifier.padding(top = 8.dp), color = C.Text, fontFamily = Inter, fontWeight = FontWeight.Thin, fontSize = 140.sp, lineHeight = 140.sp, letterSpacing = (-6).sp)
            Text(w?.sky?.label ?: "Allow location in setup", Modifier.padding(top = 6.dp), color = C.Gray3, fontFamily = Inter, fontWeight = FontWeight.Light, fontSize = 17.sp)
            Spacer(Modifier.weight(1f))
            if (w != null) Row(horizontalArrangement = Arrangement.spacedBy(22.dp)) {
                listOf("H ${w.high}°", "L ${w.low}°", "Rain ${w.rainChance}%").forEach {
                    Text(it, color = C.Gray, fontFamily = Inter, fontWeight = FontWeight.Light, fontSize = 13.sp)
                }
            }
        }
        Box(Modifier.width(1.dp).fillMaxHeight().background(C.Hairline))
        Column(Modifier.weight(1f).padding(start = 64.dp), verticalArrangement = Arrangement.spacedBy(22.dp)) {
            Label("Today")
            val nextIdx = d.events.indexOfFirst { !it.allDay && it.end.isAfter(d.now) }
            d.events.take(4).forEachIndexed { i, e ->
                val past = !e.allDay && !e.end.isAfter(d.now)
                val next = i == nextIdx
                AgendaRow(
                    if (e.allDay) "All day" else e.start.shortTime(d.is24h), e.title,
                    timeColor = if (next) C.Orange else C.Gray,
                    titleColor = if (past) C.Gray else C.Text,
                    bold = next, struck = past,
                )
            }
            if (d.events.size < 4) d.alarm?.let { AgendaRow(it.shortTime(d.is24h), "Alarm", C.Gray, C.Gray3) }
            if (d.events.isEmpty() && d.alarm == null) {
                Text("Nothing scheduled", color = C.Gray, fontFamily = Inter, fontWeight = FontWeight.Light, fontSize = 19.sp)
            }
        }
    }
}

@Composable
private fun AgendaRow(time: String, title: String, timeColor: Color, titleColor: Color, bold: Boolean = false, struck: Boolean = false) {
    Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(18.dp)) {
        Text(time, Modifier.width(84.dp), color = timeColor, fontFamily = Inter, fontWeight = if (bold) FontWeight.Normal else FontWeight.Light, fontSize = 14.sp)
        Text(
            title, color = titleColor, fontFamily = Inter, fontWeight = if (bold) FontWeight.Normal else FontWeight.Light, fontSize = 19.sp, maxLines = 1,
            textDecoration = if (struck) TextDecoration.LineThrough else null,
        )
    }
}

// ---------------- Analog ----------------

private val worldClocks = listOf("Europe/London" to "London", "America/New_York" to "New York")

@Composable
fun MinimalAnalog(d: StandByData) {
    val t = d.now
    val zone = ZoneId.systemDefault()
    val local = t.atZone(zone)
    Row(
        Modifier.fillMaxSize().background(Color.Black),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(80.dp, Alignment.CenterHorizontally),
    ) {
        val sweep by rememberInfiniteTransition(label = "sweep").animateFloat(
            0f, 360f, infiniteRepeatable(tween(60_000, easing = LinearEasing)), label = "sec",
        )
        // Start the sweep at the current second so the hand shows real time.
        val secStart = remember { t.second * 6f + t.nano / 1e9f * 6f }
        Canvas(Modifier.size(340.dp)) {
            val u = size.width / 340f
            val c = Offset(170 * u, 170 * u)
            drawCircle(Color(0xFF0B0B0C), 164 * u, c)
            drawCircle(C.Hairline, 164 * u, c, style = Stroke(1.5f * u))
            // 12, 3, 6, 9: bold. Other hours: thin, half opacity.
            for (i in 0 until 12) rotate(i * 30f, c) {
                val major = i % 3 == 0
                if (major) drawLine(C.Text, Offset(170 * u, 18 * u), Offset(170 * u, 40 * u), 3 * u, StrokeCap.Round)
                else drawLine(C.Text.copy(alpha = .5f), Offset(170 * u, 18.4f * u), Offset(170 * u, 30 * u), 1.5f * u, StrokeCap.Round)
            }
            val min = t.minute + t.second / 60f
            val hr = t.hour % 12 + min / 60f
            rotate(hr * 30f, c) { drawLine(C.Text, c, Offset(170 * u, (170 - 75) * u), 7 * u, StrokeCap.Round) }
            rotate(min * 6f, c) { drawLine(C.Text, c, Offset(170 * u, (170 - 86) * u), 4 * u, StrokeCap.Round) }
            rotate(secStart + sweep, c) { drawLine(C.Orange, Offset(170 * u, 196 * u), Offset(170 * u, 40 * u), 1.5f * u, StrokeCap.Round) }
            drawCircle(C.Orange, 5 * u, c)
        }
        Column(verticalArrangement = Arrangement.spacedBy(26.dp)) {
            WorldClock(d.weather?.city ?: zone.id.substringAfter('/').replace('_', ' '), t.shortTime(d.is24h))
            worldClocks.filter { it.first != zone.id }.take(2).forEach { (id, name) ->
                val other = local.withZoneSameInstant(ZoneId.of(id))
                val diff = Duration.ofSeconds((other.offset.totalSeconds - local.offset.totalSeconds).toLong())
                val sign = if (diff.isNegative) "−" else "+"
                val abs = diff.abs()
                WorldClock("$name · $sign${abs.toHours()}:%02d".format(Locale.US, abs.toMinutes() % 60), other.toLocalDateTime().shortTime(d.is24h))
            }
        }
    }
}

@Composable
private fun WorldClock(label: String, time: String) {
    Column {
        Label(label)
        Text(time, Modifier.padding(top = 2.dp), color = C.Text, fontFamily = Inter, fontWeight = FontWeight.ExtraLight, fontSize = 32.sp)
    }
}

// ---------------- Night ----------------

@Composable
fun NightScreen(d: StandByData) {
    // Burn-in: the red digits breathe between 85% and 100% over 10 minutes, stepping once a minute
    // (a short fade, not a continuous animation, so the GPU stays idle).
    val breathe by animateFloatAsState(.925f + .075f * kotlin.math.sin(d.now.minute * kotlin.math.PI / 5).toFloat(), tween(3000), label = "breathe")
    Box(Modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) {
        Box(Modifier.size(520.dp, 200.dp).glow(Color(0xFF5A0B06), .5f, 110.dp))
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                d.now.format(DateTimeFormatter.ofPattern(if (d.is24h) "H:mm" else "h:mm")),
                Modifier.graphicsLayer { alpha = breathe },
                color = Color(0xFFC2281D), fontFamily = com.dockstandby.ui.theme.Nunito, fontWeight = FontWeight.Light,
                fontSize = 210.sp, lineHeight = 200.sp, letterSpacing = (-6).sp, maxLines = 1, softWrap = false,
            )
            Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(26.dp)) {
                val dim = Color(0xFF7A1A14)
                Text(d.now.shortDate(), color = dim, fontFamily = Inter, fontWeight = FontWeight.Light, fontSize = 15.sp, letterSpacing = .5.sp)
                d.alarm?.let { Text("Alarm ${it.shortTime(d.is24h)}", color = dim, fontFamily = Inter, fontWeight = FontWeight.Light, fontSize = 15.sp, letterSpacing = .5.sp) }
                Text("${d.battery.level}%", color = dim, fontFamily = Inter, fontWeight = FontWeight.Light, fontSize = 15.sp, letterSpacing = .5.sp)
            }
        }
    }
}

