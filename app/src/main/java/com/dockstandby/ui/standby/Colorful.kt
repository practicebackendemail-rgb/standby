package com.dockstandby.ui.standby

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dockstandby.data.CalEvent
import com.dockstandby.data.ChargingAnimation
import com.dockstandby.data.PowerSource
import com.dockstandby.data.Sky
import com.dockstandby.data.label
import com.dockstandby.ui.theme.C
import com.dockstandby.ui.theme.CssLine
import com.dockstandby.ui.theme.Inter
import com.dockstandby.ui.theme.Nunito
import com.dockstandby.ui.theme.chargeColors
import com.dockstandby.ui.theme.clock
import com.dockstandby.ui.theme.colors
import com.dockstandby.ui.theme.formatDuration
import com.dockstandby.ui.theme.formatMinutes
import com.dockstandby.ui.theme.longDate
import com.dockstandby.ui.theme.shortTime
import com.dockstandby.ui.theme.weekday
import kotlinx.coroutines.delay
import java.time.DayOfWeek
import java.time.LocalDateTime
import java.time.temporal.TemporalAdjusters

private val W = Color.White
private fun white(a: Float) = W.copy(alpha = a)

private fun eventColor(e: CalEvent, fallback: Color) = e.color?.let { Color(it) } ?: fallback

private fun CalEvent.timeRange(is24h: Boolean) =
    if (allDay) "All day" else start.shortTime(is24h) + if (end != start) " – " + end.shortTime(is24h) else ""

// ---------------- Clock ----------------

@Composable
fun ColorfulClock(d: StandByData) {
    // "Match colours to photo": use the last slideshow photo's colours instead of the palette.
    val photo by com.dockstandby.data.PhotoPalette.current.collectAsState()
    val p = photo?.takeIf { d.settings.matchPhotoColors }?.let {
        com.dockstandby.ui.theme.PaletteColors(Color(it.darkA), androidx.compose.ui.graphics.lerp(Color(it.darkA), Color(it.darkB), .5f), Color(it.darkB))
    } ?: d.settings.palette.colors
    val t = rememberInfiniteTransition(label = "drift")
    val a1 by t.pingPong(0f, 1f, 9000)
    val a2 by t.pingPong(0f, 1f, 11000)
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        Box(
            Modifier.offset((40 + 30 * a1).dp, (-60 - 20 * a1).dp).size(420.dp, 320.dp)
                .graphicsLayer { scaleX = 1 + .15f * a1; scaleY = scaleX }.glow(p.b, .55f, 90.dp),
        )
        Box(
            Modifier.offset((260 - 40 * a2).dp, (160 + 25 * a2).dp).size(360.dp, 280.dp)
                .graphicsLayer { scaleX = 1.1f - .15f * a2; scaleY = scaleX }.glow(p.c, .45f, 100.dp),
        )
        Row(
            Modifier.fillMaxSize().padding(horizontal = 44.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(28.dp),
        ) {
            Column(Modifier.weight(1f)) {
                RollingText(
                    d.now.clock(d.is24h),
                    style = TextStyle(lineHeightStyle = CssLine, fontFamily = Nunito, fontWeight = FontWeight.Black, fontSize = 188.sp, lineHeight = 170.sp, letterSpacing = (-6).sp),
                    brush = cssLinearGradient(120f, 0f to p.a, .55f to p.b, 1f to p.c),
                )
                Text(d.now.longDate(), Modifier.padding(top = 10.dp), color = white(.75f), fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 20.sp)
            }
            GlassDayCard(d)
        }
    }
}

@Composable
private fun GlassDayCard(d: StandByData) {
    Column(
        Modifier.size(236.dp, 306.dp).clip(RoundedCornerShape(34.dp)).background(white(.08f))
            .border(1.dp, white(.14f), RoundedCornerShape(34.dp)).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(d.now.weekday(), color = C.Red, fontFamily = Inter, fontWeight = FontWeight.Bold, fontSize = 13.sp, letterSpacing = .6.sp)
        Text("${d.now.dayOfMonth}", color = W, fontFamily = Nunito, fontWeight = FontWeight.Black, fontSize = 64.sp, lineHeight = 64.sp)
        val events = d.upcoming.take(2)
        if (events.isEmpty()) {
            Text("No more events today", Modifier.padding(top = 12.dp), color = white(.6f), fontFamily = Inter, fontSize = 14.sp)
        }
        events.forEachIndexed { i, e ->
            EventLine(e, eventColor(e, if (i == 0) C.Orange else C.Sky), d.is24h, Modifier.padding(top = if (i == 0) 12.dp else 8.dp))
        }
        Spacer(Modifier.weight(1f))
        val b = d.battery
        val (c1, _) = chargeColors(b.level)
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(c1.copy(alpha = .16f)).padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (b.charging) BoltGlyph(c1) else BatteryGlyph(b.level, c1)
            Text(
                if (b.charging) "${b.level}% · ${b.source.label}" else "${b.level}%",
                color = c1, fontFamily = Inter, fontWeight = FontWeight.Bold, fontSize = 14.sp,
            )
        }
    }
}

@Composable
private fun EventLine(e: CalEvent, color: Color, is24h: Boolean, modifier: Modifier = Modifier) {
    Row(modifier.height(40.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(Modifier.width(4.dp).fillMaxHeight().clip(RoundedCornerShape(2.dp)).background(color))
        Column {
            Text(e.title, color = W, fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(e.timeRange(is24h), color = white(.6f), fontFamily = Inter, fontSize = 13.sp)
        }
    }
}

private fun boltPath(w: Float, h: Float) = Path().apply {
    val sx = w / 24f; val sy = h / 36f
    moveTo(14 * sx, 0f); lineTo(2 * sx, 20 * sy); lineTo(11 * sx, 20 * sy)
    lineTo(9 * sx, 36 * sy); lineTo(22 * sx, 14 * sy); lineTo(13 * sx, 14 * sy); close()
}

@Composable
private fun BoltGlyph(color: Color) {
    Canvas(Modifier.size(16.dp, 22.dp)) { drawPath(boltPath(size.width, size.height), color) }
}

@Composable
private fun BatteryGlyph(level: Int, color: Color) {
    Canvas(Modifier.size(26.dp, 14.dp)) {
        val body = Size(size.width - 3.dp.toPx(), size.height)
        drawRoundRect(color, size = body, cornerRadius = CornerRadius(3.dp.toPx()), style = Stroke(1.5.dp.toPx()))
        val inset = 2.5.dp.toPx()
        drawRoundRect(
            color, topLeft = Offset(inset, inset),
            size = Size((body.width - 2 * inset) * level / 100f, body.height - 2 * inset),
            cornerRadius = CornerRadius(1.5.dp.toPx()),
        )
        drawRoundRect(color, topLeft = Offset(body.width + 1.dp.toPx(), size.height * .3f), size = Size(2.dp.toPx(), size.height * .4f), cornerRadius = CornerRadius(1.dp.toPx()))
    }
}

// ---------------- Charging ----------------

@Composable
fun ColorfulCharging(d: StandByData) {
    val b = d.battery
    val (c1, c2) = chargeColors(b.level)
    val t = rememberInfiniteTransition(label = "charge")
    val pulse by t.pingPong(0f, 1f, 2200)
    val bolt by t.pingPong(1f, 1.12f, 1600)
    val wave by t.animateFloat(0f, 1f, infiniteRepeatable(tween(2400, easing = LinearEasing)), label = "wave")
    // Bubble clock in ms over one 3 s cycle; each bubble applies its own CSS delay.
    val rise by t.animateFloat(0f, 3000f, infiniteRepeatable(tween(3000, easing = LinearEasing)), label = "rise")
    val animate = b.charging && d.settings.chargingAnimation != ChargingAnimation.Off

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        Box(
            Modifier.offset(60.dp, 70.dp).size(400.dp, 250.dp)
                .graphicsLayer { scaleX = 1 + .08f * pulse; scaleY = scaleX }
                .glow(c2, .35f + .35f * pulse, 80.dp),
        )
        Row(
            Modifier.fillMaxSize().padding(horizontal = 60.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(54.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(
                    Modifier.size(380.dp, 196.dp).shadow(40.dp, RoundedCornerShape(52.dp))
                        .clip(RoundedCornerShape(52.dp))
                        .background(cssLinearGradient(135f, 0f to white(.35f), 1f to white(.08f)))
                        .padding(7.dp),
                ) {
                    val liquid = animate && d.settings.chargingAnimation == ChargingAnimation.Liquid
                    LiquidFill(b.level / 100f, c1, c2, if (liquid) wave else 0f, if (liquid) rise else -1f)
                    if (animate && d.settings.chargingAnimation == ChargingAnimation.Rings) ChargeRings(rise / 3000f)
                    if (b.charging) {
                        Canvas(Modifier.align(Alignment.Center).size(52.dp, 78.dp).graphicsLayer { scaleX = bolt; scaleY = bolt }) {
                            // filter: drop-shadow(0 4px 12px rgba(0,0,0,.35))
                            drawIntoCanvas { canvas ->
                                val paint = Paint().apply { color = W }
                                paint.asFrameworkPaint().setShadowLayer(12.dp.toPx(), 0f, 4.dp.toPx(), Color.Black.copy(alpha = .35f).toArgb())
                                canvas.drawPath(boltPath(size.width, size.height), paint)
                            }
                        }
                    }
                }
                Box(Modifier.size(14.dp, 70.dp).clip(RoundedCornerShape(topEnd = 10.dp, bottomEnd = 10.dp)).background(white(.3f)))
            }
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "${b.level}%",
                    style = TextStyle(lineHeightStyle = CssLine, 
                        brush = cssLinearGradient(120f, .2f to W, 1f to c2),
                        fontFamily = Nunito, fontWeight = FontWeight.Black, fontSize = 120.sp, lineHeight = 108.sp, letterSpacing = (-4).sp,
                    ),
                    maxLines = 1, softWrap = false,
                )
                Row(
                    Modifier.clip(CircleShape).background(white(.1f)).padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (b.source == PowerSource.Wireless) WirelessGlyph(c2) else PlugGlyph(c2)
                    Text(
                        b.source.label + (b.watts?.takeIf { b.charging }?.let { " · ${"%.0f".format(it)} W" } ?: ""),
                        color = W, fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 16.sp,
                    )
                }
                val sub = when {
                    b.full -> "Fully charged"
                    !b.charging -> "Not charging"
                    b.minutesToFull != null -> "Full in ${formatMinutes(b.minutesToFull)}"
                    else -> "Charging"
                }
                Text(sub, Modifier.padding(top = 4.dp), color = white(.65f), fontFamily = Inter, fontWeight = FontWeight.Medium, fontSize = 17.sp)
            }
        }
    }
}

/** "Rings" charging animation: three soft rings ripple out from the bolt, staggered by a third. */
@Composable
private fun ChargeRings(phase: Float) {
    Canvas(Modifier.fillMaxSize()) {
        for (i in 0..2) {
            val p = (phase + i / 3f) % 1f
            val e = CssEaseIn.transform(p)
            drawCircle(
                W.copy(alpha = .55f * (1 - p)),
                radius = (30 + 170 * e).dp.toPx(),
                style = Stroke((3 - 2 * p).dp.toPx()),
            )
        }
    }
}

private class Bubble(val top: Float, val size: Float, val alpha: Float, val delayMs: Float)

private val bubbles = listOf(
    Bubble(34f, 12f, .85f, 0f),
    Bubble(96f, 8f, .85f, 800f),
    Bubble(140f, 14f, .7f, 1600f),
    Bubble(64f, 7f, .85f, 2300f),
    Bubble(120f, 10f, .8f, 1200f),
)

/** The liquid inside the battery: gradient fill, a wavy leading edge, bubbles drifting right. */
@Composable
private fun LiquidFill(fraction: Float, c1: Color, c2: Color, wave: Float, riseMs: Float) {
    Canvas(Modifier.fillMaxSize().clip(RoundedCornerShape(45.dp)).background(Color(0xFF111111))) {
        val edge = size.width * fraction
        drawRect(Brush.horizontalGradient(listOf(c1, c2), endX = edge.coerceAtLeast(1f)), size = Size(edge, size.height))
        // Wavy edge: the mockup's 32×400 SVG strip (bulges every 50px), scrolling up by 200px per cycle.
        val u = 1.dp.toPx()
        clipRect(left = edge - 16 * u, right = edge + 16 * u) {
            translate(edge - 16 * u, -200 * u * wave) {
                val path = Path().apply {
                    moveTo(0f, 0f)
                    cubicTo(32 * u, 25 * u, 32 * u, 25 * u, 16 * u, 50 * u)
                    for (k in 1 until 8) {
                        val x = if (k % 2 == 1) 0f else 32 * u
                        cubicTo(x, (50 * k + 25) * u, x, (50 * k + 25) * u, 16 * u, 50 * (k + 1) * u)
                    }
                    lineTo(0f, 400 * u); close()
                }
                drawPath(path, c2)
            }
        }
        // Glassy top highlight.
        drawRect(Brush.verticalGradient(0f to white(.28f), .4f to white(0f)))
        if (riseMs >= 0f) {
            for (b in bubbles) {
                val p = (((riseMs - b.delayMs) % 3000f + 3000f) % 3000f) / 3000f
                // opacity keyframes 0 → .9 (0–15%) → 0, each segment eased like CSS ease-in.
                val alpha = if (p < .15f) .9f * CssEaseIn.transform(p / .15f) else .9f * (1 - CssEaseIn.transform((p - .15f) / .85f))
                val e = CssEaseIn.transform(p)
                val r = b.size * u / 2
                drawCircle(
                    W.copy(alpha = b.alpha * alpha),
                    radius = r * (.6f + .5f * e),
                    center = Offset((6 + 300 * e) * u + r, b.top * u + r),
                )
            }
        }
    }
}

@Composable
private fun WirelessGlyph(color: Color) {
    Canvas(Modifier.size(18.dp)) {
        val s = size.width / 24f
        val st = Stroke(2.2f * s, cap = StrokeCap.Round)
        fun arc(r: Float, cy: Float) = drawArc(color, 225f, 90f, false, topLeft = Offset(12 * s - r, cy - r), size = Size(2 * r, 2 * r), style = st)
        arc(10 * s, 16 * s); arc(5.7f * s, 17.5f * s); arc(1.6f * s, 17.5f * s)
    }
}

@Composable
private fun PlugGlyph(color: Color) {
    Canvas(Modifier.size(18.dp)) {
        val s = size.width / 24f
        val st = Stroke(2.2f * s, cap = StrokeCap.Round)
        drawLine(color, Offset(9 * s, 3 * s), Offset(9 * s, 8 * s), st.width, StrokeCap.Round)
        drawLine(color, Offset(15 * s, 3 * s), Offset(15 * s, 8 * s), st.width, StrokeCap.Round)
        drawRoundRect(color, Offset(6 * s, 8 * s), Size(12 * s, 7 * s), CornerRadius(3 * s), style = st)
        drawLine(color, Offset(12 * s, 15 * s), Offset(12 * s, 21 * s), st.width, StrokeCap.Round)
    }
}

// ---------------- Weather + Calendar ----------------

@Composable
fun ColorfulWeatherCalendar(d: StandByData) {
    Row(Modifier.fillMaxSize().background(Color.Black).padding(26.dp), horizontalArrangement = Arrangement.spacedBy(18.dp)) {
        WeatherCard(d, Modifier.weight(1f).fillMaxHeight())
        CalendarCard(d, Modifier.weight(1f).fillMaxHeight())
    }
}

private fun skyGradient(sky: Sky?) = when (sky) {
    Sky.Rain, Sky.Storm -> listOf(Color(0xFF3A4A6B), Color(0xFF52627F), Color(0xFF7A8AA3))
    Sky.Cloudy, Sky.Fog -> listOf(Color(0xFF5B7A99), Color(0xFF7B97B3), Color(0xFFB0C2D4))
    Sky.Snow -> listOf(Color(0xFF7FA7D9), Color(0xFFA9C6E8), Color(0xFFE6F0FA))
    else -> listOf(Color(0xFF2F8CFF), Color(0xFF4FA6FF), Color(0xFFFFB36B))
}

@Composable
private fun WeatherCard(d: StandByData, modifier: Modifier) {
    val w = d.weather
    val sky = w?.sky ?: Sky.PartlyCloudy
    val t = rememberInfiniteTransition(label = "sun")
    val spin by t.animateFloat(0f, 360f, infiniteRepeatable(tween(24_000, easing = LinearEasing)), label = "spin")
    val float by t.pingPong(0f, 12f, 6000)
    val g = skyGradient(w?.sky)
    Box(modifier.clip(RoundedCornerShape(36.dp)).background(cssLinearGradient(165f, 0f to g[0], .45f to g[1], 1f to g[2]))) {
        if (sky == Sky.Clear || sky == Sky.PartlyCloudy) {
            Canvas(Modifier.align(Alignment.TopEnd).padding(top = 14.dp, end = 20.dp).size(120.dp).graphicsLayer { rotationZ = spin }) {
                val s = size.width / 120f
                val sun = Color(0xFFFFE066)
                drawCircle(sun, 28 * s)
                for (i in 0 until 8) rotate(i * 45f) {
                    drawLine(sun, Offset(60 * s, 8 * s), Offset(60 * s, 22 * s), 5 * s, StrokeCap.Round)
                }
            }
        }
        if (sky != Sky.Clear) Cloud(
            if (sky == Sky.Rain || sky == Sky.Storm) Color(0xFFDDE6F0) else white(.95f),
            Modifier.align(Alignment.TopEnd).padding(top = 66.dp, end = 54.dp).offset(float.dp, 0.dp),
        )
        Column(Modifier.fillMaxSize().padding(horizontal = 26.dp, vertical = 24.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(w?.city ?: "Weather", color = W, fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 17.sp)
                if (w?.city != null) Canvas(Modifier.size(12.dp)) {
                    val s = size.width / 24f
                    drawPath(Path().apply { moveTo(3 * s, 11 * s); lineTo(21 * s, 3 * s); lineTo(13 * s, 21 * s); lineTo(11 * s, 13 * s); close() }, W)
                }
            }
            if (w == null) {
                Text("—", Modifier.padding(top = 4.dp), color = W, fontFamily = Nunito, fontWeight = FontWeight.Black, fontSize = 92.sp, lineHeight = 92.sp)
                Text("Allow location in setup", color = W, fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                return@Column
            }
            Text("${w.temp}°", Modifier.padding(top = 4.dp), color = W, fontFamily = Nunito, fontWeight = FontWeight.Black, fontSize = 92.sp, lineHeight = 92.sp, letterSpacing = (-3).sp)
            Text(w.sky.label, color = W, fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
            Text("H:${w.high}° L:${w.low}°", color = white(.85f), fontFamily = Inter, fontWeight = FontWeight.Medium, fontSize = 15.sp)
            Spacer(Modifier.weight(1f))
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(white(.18f)).padding(horizontal = 6.dp, vertical = 10.dp)) {
                val slots = listOf("Now" to w.temp) + w.hourly.map { it.time.shortTime(d.is24h).replace(":00", "").replace(" ", "") to it.temp }
                slots.forEach { (label, temp) ->
                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(label, color = W, fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, lineHeight = 16.sp)
                        Text("$temp°", color = W, fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, lineHeight = 19.sp)
                    }
                }
            }
        }
    }
}

/** 110×44 pill plus the CSS box-shadow copy (offset 30,−14, spread −4) that forms the cloud's top. */
@Composable
private fun Cloud(color: Color, modifier: Modifier) {
    Canvas(modifier.size(110.dp, 44.dp)) {
        val u = 1.dp.toPx()
        drawRoundRect(color, Offset(34 * u, -10 * u), Size(102 * u, 36 * u), CornerRadius(18 * u))
        drawRoundRect(color, cornerRadius = CornerRadius(22 * u))
    }
}

@Composable
private fun CalendarCard(d: StandByData, modifier: Modifier) {
    val ink = Color(0xFF1C1C1E)
    Column(
        modifier.clip(RoundedCornerShape(36.dp)).background(W).padding(horizontal = 26.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom) {
            Column {
                Text(d.now.weekday(), color = C.CalRed, fontFamily = Inter, fontWeight = FontWeight.Bold, fontSize = 14.sp, letterSpacing = .4.sp)
                Text("${d.now.dayOfMonth}", color = ink, fontFamily = Nunito, fontWeight = FontWeight.Black, fontSize = 54.sp, lineHeight = 54.sp)
            }
            WeekStrip(d.now)
        }
        val tints = listOf(Color(0xFF0A84FF) to Color(0xFFE8F2FF), C.Orange to Color(0xFFFFF2E3), C.Green to Color(0xFFE6F8EC))
        val events = d.upcoming.take(3)
        if (events.isEmpty()) {
            Text("Nothing else on the calendar today.", color = C.Gray, fontFamily = Inter, fontSize = 15.sp, modifier = Modifier.padding(top = 8.dp))
        }
        events.forEachIndexed { i, e ->
            val (bar, bg) = tints[i % tints.size]
            Row(
                Modifier.fillMaxWidth().height(58.dp).clip(RoundedCornerShape(16.dp)).background(bg)
                    .then(if (e.startsSoon(d.now)) Modifier.border(2.dp, bar, RoundedCornerShape(16.dp)) else Modifier).padding(horizontal = 12.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Box(Modifier.width(4.dp).fillMaxHeight().clip(RoundedCornerShape(2.dp)).background(bar))
                Column {
                    Text(e.title, color = ink, fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(e.timeRange(d.is24h) + (e.location?.let { " · $it" } ?: ""), color = C.Gray, fontFamily = Inter, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

@Composable
private fun WeekStrip(now: LocalDateTime) {
    val monday = now.toLocalDate().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            "MTWTFSS".forEach { Text("$it", Modifier.width(18.dp), color = C.Gray2, fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 11.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center) }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            for (i in 0..6) {
                val day = monday.plusDays(i.toLong())
                val today = day == now.toLocalDate()
                Box(
                    Modifier.width(18.dp).clip(RoundedCornerShape(9.dp)).background(if (today) C.CalRed else Color.Transparent),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        "${day.dayOfMonth}",
                        color = when { today -> W; day.isBefore(now.toLocalDate()) -> C.Gray2; else -> Color(0xFF1C1C1E) },
                        fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 11.sp,
                    )
                }
            }
        }
    }
}

// ---------------- Now Playing ----------------

@Composable
fun ColorfulNowPlaying(d: StandByData) {
    val np = d.nowPlaying
    Box(Modifier.fillMaxSize().background(Color(0xFF1A0B2E))) {
        Box(Modifier.offset((-80).dp, (-120).dp).size(520.dp).glow(Color(0xFFFF4F8B), .7f, 110.dp))
        Box(Modifier.align(Alignment.BottomEnd).offset(60.dp, 160.dp).size(520.dp, 460.dp).glow(Color(0xFF6E4BFF), .8f, 110.dp))
        Box(Modifier.align(Alignment.TopEnd).offset((-160).dp, (-100).dp).size(300.dp, 260.dp).glow(Color(0xFFFFB347), .45f, 110.dp))
        Row(
            Modifier.fillMaxSize().padding(horizontal = 52.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(40.dp),
        ) {
            val artShape = RoundedCornerShape(28.dp)
            Box(
                Modifier.size(270.dp).shadow(50.dp, artShape).clip(artShape)
                    .background(Brush.linearGradient(listOf(Color(0xFFFFB347), Color(0xFFFF4F8B), Color(0xFF6E4BFF)))),
            ) {
                np?.art?.let { Image(it.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                if (np == null) {
                    Text("Nothing playing", color = W, fontFamily = Inter, fontWeight = FontWeight.ExtraBold, fontSize = 34.sp)
                    Text("Start music in any app — or allow notification access in setup.", color = white(.75f), fontFamily = Inter, fontWeight = FontWeight.Medium, fontSize = 18.sp)
                    return@Column
                }
                Row(Modifier.height(22.dp), verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                    Equalizer(np.playing)
                    Text("Playing on ${np.appName}", Modifier.padding(start = 8.dp).graphicsLayer { alpha = .7f }, color = W, fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                }
                Text(np.title, Modifier.padding(top = 4.dp), color = W, fontFamily = Inter, fontWeight = FontWeight.ExtraBold, fontSize = 34.sp, letterSpacing = (-.6).sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                np.artist?.let { Text(it, color = white(.75f), fontFamily = Inter, fontWeight = FontWeight.Medium, fontSize = 20.sp, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                // Re-read position a few times a second while playing.
                val tick = remember { mutableLongStateOf(0L) }
                LaunchedEffect(np) { while (np.playing) { delay(500); tick.longValue++ } }
                tick.longValue
                val pos = np.positionNow()
                val frac = if (np.durationMs > 0) pos.toFloat() / np.durationMs else 0f
                Box(Modifier.padding(top = 22.dp).fillMaxWidth().height(6.dp).clip(CircleShape).background(white(.25f))) {
                    Box(Modifier.fillMaxWidth(frac.coerceIn(0f, 1f)).fillMaxHeight().clip(CircleShape).background(W))
                }
                Row(Modifier.fillMaxWidth().graphicsLayer { alpha = .7f }, horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(formatDuration(pos), color = W, fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                    if (np.durationMs > 0) Text("−" + formatDuration(np.durationMs - pos), color = W, fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                }
                val tc = np.controller?.transportControls
                Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(30.dp)) {
                    TransportButton(52, { tc?.skipToPrevious() }) { SkipGlyph(forward = false) }
                    Box(
                        Modifier.size(72.dp).shadow(20.dp, CircleShape).clip(CircleShape).background(white(.95f)).clickable { if (np.playing) tc?.pause() else tc?.play() },
                        contentAlignment = Alignment.Center,
                    ) { PlayPauseGlyph(np.playing) }
                    TransportButton(52, { tc?.skipToNext() }) { SkipGlyph(forward = true) }
                }
            }
        }
    }
}

@Composable
private fun TransportButton(size: Int, onClick: () -> Unit, content: @Composable () -> Unit) {
    Box(Modifier.size(size.dp).clip(CircleShape).clickable(onClick = onClick), contentAlignment = Alignment.Center) { content() }
}

@Composable
private fun SkipGlyph(forward: Boolean) {
    Canvas(Modifier.size(36.dp).graphicsLayer { scaleX = if (forward) 1f else -1f }) {
        val s = size.width / 24f
        drawPath(Path().apply { moveTo(13 * s, 6 * s); lineTo(13 * s, 18 * s); lineTo(21 * s, 12 * s); close() }, W)
        drawPath(Path().apply { moveTo(4 * s, 6 * s); lineTo(4 * s, 18 * s); lineTo(12 * s, 12 * s); close() }, W)
    }
}

@Composable
private fun PlayPauseGlyph(playing: Boolean) {
    val ink = Color(0xFF2A1450)
    Canvas(Modifier.size(30.dp)) {
        val s = size.width / 24f
        if (playing) {
            drawRoundRect(ink, Offset(6 * s, 4 * s), Size(4 * s, 16 * s), CornerRadius(1.2f * s))
            drawRoundRect(ink, Offset(14 * s, 4 * s), Size(4 * s, 16 * s), CornerRadius(1.2f * s))
        } else {
            drawPath(Path().apply { moveTo(7 * s, 4 * s); lineTo(7 * s, 20 * s); lineTo(20 * s, 12 * s); close() }, ink)
        }
    }
}

@Composable
private fun Equalizer(playing: Boolean) {
    val t = rememberInfiniteTransition(label = "eq")
    for (i in 0..2) {
        val h by t.animateFloat(6f, 22f, infiniteRepeatable(tween(450, easing = CssEaseInOut), RepeatMode.Reverse, androidx.compose.animation.core.StartOffset(i * 300)), label = "eq$i")
        Box(Modifier.width(4.dp).height((if (playing) h else 6f).dp).clip(RoundedCornerShape(2.dp)).background(W))
    }
}

