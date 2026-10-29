package com.dockstandby.ui.standby

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dockstandby.data.WidgetSlot
import com.dockstandby.data.WidgetType
import com.dockstandby.data.widgetSlots
import com.dockstandby.ui.theme.C
import com.dockstandby.ui.theme.Inter
import com.dockstandby.ui.theme.Nunito
import com.dockstandby.ui.theme.chargeColors
import com.dockstandby.ui.theme.hhmm24
import com.dockstandby.ui.theme.shortTime
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit

private val W = Color.White
private fun white(a: Float) = W.copy(alpha = a)

/**
 * The widget stack: one large slot on the left, a wide slot and two small slots on the right.
 * Long-press any widget to choose what goes in that slot.
 */
@Composable
fun ColorfulWidgets(d: StandByData, onToggleItem: (Int) -> Unit, onChange: (slot: Int, WidgetType) -> Unit) {
    var picking by remember { mutableStateOf<Int?>(null) }
    val w = d.settings.widgets
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        Row(Modifier.fillMaxSize().padding(26.dp), horizontalArrangement = Arrangement.spacedBy(18.dp)) {
            Slot(0, w[0], d, onToggleItem, { picking = it }, Modifier.weight(1.15f).fillMaxHeight())
            Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(18.dp)) {
                Slot(1, w[1], d, onToggleItem, { picking = it }, Modifier.weight(1f).fillMaxWidth())
                Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                    Slot(2, w[2], d, onToggleItem, { picking = it }, Modifier.weight(1f).fillMaxHeight())
                    Slot(3, w[3], d, onToggleItem, { picking = it }, Modifier.weight(1f).fillMaxHeight())
                }
            }
        }
        picking?.let { slot ->
            WidgetPicker(current = w[slot], onPick = { onChange(slot, it); picking = null }, onDismiss = { picking = null })
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun Slot(
    index: Int,
    type: WidgetType,
    d: StandByData,
    onToggleItem: (Int) -> Unit,
    onLongPress: (Int) -> Unit,
    modifier: Modifier,
) {
    val slot = widgetSlots[index]
    Box(modifier.combinedClickable(onClick = {}, onLongClick = { onLongPress(index) })) {
        when (type) {
            WidgetType.Rings -> RingsWidget(d, slot)
            WidgetType.Battery -> BatteryWidget(d, slot)
            WidgetType.Alarm -> AlarmWidget(d, slot)
            WidgetType.NextEvent -> NextUpWidget(d, slot)
            WidgetType.WorldClock -> WorldClockWidget(d, slot)
            WidgetType.Countdown -> CountdownWidget(d, slot)
            WidgetType.Checklist -> ChecklistWidget(d, slot, onToggleItem)
            WidgetType.Quote -> QuoteWidget(d, slot)
        }
    }
}

/** Full-screen chooser shown over the stack after a long-press. */
@Composable
private fun WidgetPicker(current: WidgetType, onPick: (WidgetType) -> Unit, onDismiss: () -> Unit) {
    Box(
        Modifier.fillMaxSize().background(Color.Black.copy(alpha = .85f)).clickable(onClick = onDismiss),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier.width(520.dp).clip(RoundedCornerShape(28.dp)).background(C.Card).clickable(enabled = false) {}.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text("Choose a widget", color = W, fontFamily = Inter, fontWeight = FontWeight.Bold, fontSize = 18.sp)
            WidgetType.entries.chunked(2).forEach { pair ->
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    pair.forEach { t ->
                        val on = t == current
                        Text(
                            t.label,
                            Modifier.weight(1f).clip(RoundedCornerShape(14.dp))
                                .background(if (on) C.Blue else C.Separator).clickable { onPick(t) }
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            color = W, fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 15.sp,
                        )
                    }
                }
            }
        }
    }
}

// ---------------- shared card chrome ----------------

private val CardShape = RoundedCornerShape(30.dp)

@Composable
private fun Card(bg: Brush, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Column(
        Modifier.fillMaxSize().then(modifier).clip(CardShape).background(bg).padding(horizontal = 18.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.SpaceBetween,
    ) { content() }
}

private fun solid(c: Color) = Brush.linearGradient(listOf(c, c))

@Composable
private fun Label(text: String, color: Color = white(.6f)) =
    Text(text.uppercase(), color = color, fontFamily = Inter, fontWeight = FontWeight.Bold, fontSize = 12.sp, letterSpacing = .8.sp)

@Composable
private fun Big(text: String, size: TextUnit, color: Color = W) =
    Text(text, color = color, fontFamily = Nunito, fontWeight = FontWeight.Black, fontSize = size, lineHeight = size, maxLines = 1, overflow = TextOverflow.Ellipsis)

private fun bigSize(slot: WidgetSlot) = when (slot) {
    WidgetSlot.Large -> 56.sp
    WidgetSlot.Wide -> 34.sp
    WidgetSlot.Small -> 24.sp
}

// ---------------- widgets ----------------

private const val STEP_GOAL = 10_000

/** Three flat-ended rings (like the mockup's conic gradients): steps, battery, day elapsed. */
@Composable
private fun RingsWidget(d: StandByData, slot: WidgetSlot) {
    val steps = d.steps
    val dayFrac = (d.now.hour * 60 + d.now.minute) / 1440f
    val rings = listOf(
        Color(0xFFFA114F) to ((steps ?: 0) / STEP_GOAL.toFloat()).coerceIn(0f, 1f),
        Color(0xFFA6FF00) to d.battery.level / 100f,
        Color(0xFF00E5FF) to dayFrac,
    )
    val ringSize = when (slot) { WidgetSlot.Large -> 190.dp; WidgetSlot.Wide -> 120.dp; WidgetSlot.Small -> 104.dp }
    Row(
        Modifier.fillMaxSize().clip(RoundedCornerShape(if (slot == WidgetSlot.Large) 36.dp else 30.dp)).background(C.Card)
            .padding(if (slot == WidgetSlot.Large) 24.dp else 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(22.dp, if (slot == WidgetSlot.Small) Alignment.CenterHorizontally else Alignment.Start),
    ) {
        Canvas(Modifier.size(ringSize)) {
            // Mockup: 190px rings, each 21px wide, 3px apart.
            val k = size.width / 190f
            rings.forEachIndexed { i, (color, frac) ->
                val inset = (i * 24 + 10.5f) * k
                val sz = Size(size.width - 2 * inset, size.height - 2 * inset)
                val tl = Offset(inset, inset)
                drawArc(color.copy(alpha = .22f), 0f, 360f, false, tl, sz, style = Stroke(21 * k))
                drawArc(color, -90f, 360f * frac, false, tl, sz, style = Stroke(21 * k))
            }
        }
        if (slot != WidgetSlot.Small) Column(verticalArrangement = Arrangement.spacedBy(if (slot == WidgetSlot.Large) 10.dp else 4.dp)) {
            if (slot == WidgetSlot.Large) Label("Today", C.Gray2)
            RingStat("Steps", if (steps != null) "%,d".format(steps) else "—", if (slot == WidgetSlot.Large) "/${"%,d".format(STEP_GOAL)}" else "", rings[0].first)
            RingStat("Battery", "${d.battery.level}", "%", rings[1].first)
            if (slot == WidgetSlot.Large) RingStat("Day", "${(dayFrac * 100).toInt()}", "%", rings[2].first)
        }
    }
}

@Composable
private fun RingStat(label: String, value: String, unit: String, color: Color) {
    Column {
        Text(label, color = W, fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
        Row(verticalAlignment = Alignment.Bottom) {
            Text(value, color = color, fontFamily = Nunito, fontWeight = FontWeight.Black, fontSize = 26.sp, lineHeight = 28.sp)
            Text(unit, Modifier.padding(bottom = 3.dp), color = color, fontFamily = Nunito, fontWeight = FontWeight.Black, fontSize = 15.sp)
        }
    }
}

@Composable
private fun BatteryWidget(d: StandByData, slot: WidgetSlot) {
    val b = d.battery
    val (c1, c2) = chargeColors(b.level)
    Card(solid(C.Card)) {
        Label(if (b.charging) "Charging · ${b.source.label}" else "Battery")
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Big("${b.level}%", bigSize(slot), c2)
            Box(Modifier.fillMaxWidth().padding(end = 4.dp).clip(RoundedCornerShape(6.dp)).background(white(.12f))) {
                Box(Modifier.fillMaxWidth(b.level / 100f).padding(vertical = 5.dp).clip(RoundedCornerShape(6.dp)).background(Brush.horizontalGradient(listOf(c1, c2))))
            }
        }
    }
}

@Composable
private fun AlarmWidget(d: StandByData, slot: WidgetSlot) {
    Card(Brush.linearGradient(listOf(Color(0xFF5E5CE6), Color(0xFFBF5AF2)))) {
        Canvas(Modifier.size(26.dp)) {
            val s = size.width / 24f
            val st = Stroke(2 * s, cap = StrokeCap.Round)
            drawCircle(W, 8 * s, Offset(12 * s, 13 * s), style = st)
            drawLine(W, Offset(12 * s, 9 * s), Offset(12 * s, 13 * s), st.width, StrokeCap.Round)
            drawLine(W, Offset(12 * s, 13 * s), Offset(14.5f * s, 15 * s), st.width, StrokeCap.Round)
            drawLine(W, Offset(9 * s, 2 * s), Offset(15 * s, 2 * s), st.width, StrokeCap.Round)
        }
        Column {
            Text("Alarm", color = white(.8f), fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
            Big(d.alarm?.shortTime(d.is24h) ?: "None", bigSize(slot))
            if (slot != WidgetSlot.Small) d.alarm?.let {
                val days = ChronoUnit.DAYS.between(d.now.toLocalDate(), it.toLocalDate())
                Text(if (days == 0L) "Today" else if (days == 1L) "Tomorrow" else it.dayOfWeek.name.lowercase().replaceFirstChar(Char::uppercase), color = white(.8f), fontFamily = Inter, fontSize = 13.sp)
            }
        }
    }
}

@Composable
private fun NextUpWidget(d: StandByData, slot: WidgetSlot) {
    val upcoming = d.upcoming.filter { !it.allDay }.take(if (slot == WidgetSlot.Large) 4 else if (slot == WidgetSlot.Wide) 2 else 1)
    Card(
        solid(white(.04f)),
        Modifier.drawWithContent {
            drawContent()
            drawRoundRect(
                white(.25f), style = Stroke(2.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f))),
                cornerRadius = CornerRadius(30.dp.toPx()),
            )
        },
    ) {
        Label("Next up")
        if (upcoming.isEmpty()) {
            Column {
                Text("Free", color = W, fontFamily = Inter, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Text("Rest of day", color = white(.6f), fontFamily = Inter, fontSize = 13.sp)
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            upcoming.forEach { e ->
                Column {
                    Text(e.title, color = W, fontFamily = Inter, fontWeight = FontWeight.Bold, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(e.start.shortTime(d.is24h), color = if (e.startsSoon(d.now)) C.Orange else white(.6f), fontFamily = Inter, fontSize = 13.sp)
                }
            }
        }
    }
}

private val worldCities = listOf("Europe/London" to "London", "America/New_York" to "New York", "Asia/Tokyo" to "Tokyo")

@Composable
private fun WorldClockWidget(d: StandByData, slot: WidgetSlot) {
    val local = d.now.atZone(ZoneId.systemDefault())
    val cities = worldCities.filter { it.first != ZoneId.systemDefault().id }
        .take(when (slot) { WidgetSlot.Large -> 3; WidgetSlot.Wide -> 2; WidgetSlot.Small -> 1 })
    Card(Brush.linearGradient(listOf(Color(0xFF0A84FF), Color(0xFF5E5CE6)))) {
        Label("World clock", white(.75f))
        Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
            cities.forEach { (id, name) ->
                Column {
                    Text(name, color = white(.8f), fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                    Big(local.withZoneSameInstant(ZoneId.of(id)).toLocalDateTime().shortTime(d.is24h), if (slot == WidgetSlot.Large) 34.sp else 24.sp)
                }
            }
        }
    }
}

@Composable
private fun CountdownWidget(d: StandByData, slot: WidgetSlot) {
    val day = d.settings.countdownDay
    Card(Brush.linearGradient(listOf(Color(0xFFFF2D55), Color(0xFFFF9F0A)))) {
        Label(d.settings.countdownLabel.ifBlank { "Countdown" }, white(.8f))
        if (day == null) {
            Text("Set a date in setup", color = W, fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
        } else {
            val days = ChronoUnit.DAYS.between(d.now.toLocalDate(), LocalDate.ofEpochDay(day))
            Column {
                Big(if (days >= 0) "$days" else "${-days}", bigSize(slot))
                Text(
                    when { days == 0L -> "Today!"; days > 0 -> if (days == 1L) "day to go" else "days to go"; else -> "days ago" },
                    color = white(.85f), fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 13.sp,
                )
            }
        }
    }
}

@Composable
private fun ChecklistWidget(d: StandByData, slot: WidgetSlot, onToggle: (Int) -> Unit) {
    val ink = Color(0xFF3A2600)
    val items = d.settings.checklist
    val open = items.withIndex().filter { !it.value.done }
    val done = items.withIndex().filter { it.value.done }
    Card(Brush.linearGradient(listOf(Color(0xFFFFD60A), Color(0xFFFF9F0A)))) {
        Label("List", ink.copy(alpha = .7f))
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            val maxOpen = when (slot) { WidgetSlot.Large -> 6; WidgetSlot.Wide -> 4; WidgetSlot.Small -> 2 }
            if (slot == WidgetSlot.Wide) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    open.take(maxOpen).forEach { (i, item) -> CheckItem(item.text, false, ink) { onToggle(i) } }
                }
            } else {
                open.take(maxOpen).forEach { (i, item) -> CheckItem(item.text, false, ink) { onToggle(i) } }
            }
            if (slot != WidgetSlot.Small) done.take(2).forEach { (i, item) -> CheckItem(item.text, true, ink) { onToggle(i) } }
            Spacer(Modifier.size(0.dp))
        }
    }
}

@Composable
private fun CheckItem(text: String, done: Boolean, ink: Color, onClick: () -> Unit) {
    Text(
        (if (done) "● " else "○ ") + text,
        Modifier.clickable(onClick = onClick).graphicsLayer { alpha = if (done) .5f else 1f },
        color = ink, fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, maxLines = 1,
        textDecoration = if (done) TextDecoration.LineThrough else null,
    )
}

/** Short, original lines; one per day of the year, cycling. */
private val quotes = listOf(
    "Small steps still move you forward.",
    "Rest is part of the work.",
    "Do the next right thing.",
    "Today is a fresh page.",
    "Slow is smooth, smooth is fast.",
    "Be kind to future you.",
    "Done is better than perfect.",
    "Notice one good thing today.",
    "Breathe in. Begin again.",
    "Make it simple, then make it better.",
    "Your pace is still progress.",
    "Curiosity first, judgement later.",
    "One thing at a time.",
    "Leave it a little better than you found it.",
)

@Composable
private fun QuoteWidget(d: StandByData, slot: WidgetSlot) {
    val q = quotes[d.now.dayOfYear % quotes.size]
    Card(Brush.linearGradient(listOf(Color(0xFF30D158), Color(0xFF30D5C8)))) {
        Label("Today", white(.8f))
        Text(
            q, color = W, fontFamily = Nunito, fontWeight = FontWeight.ExtraBold,
            fontSize = when (slot) { WidgetSlot.Large -> 30.sp; WidgetSlot.Wide -> 20.sp; WidgetSlot.Small -> 15.sp },
            lineHeight = when (slot) { WidgetSlot.Large -> 36.sp; WidgetSlot.Wide -> 25.sp; WidgetSlot.Small -> 19.sp },
            maxLines = if (slot == WidgetSlot.Small) 4 else 3, overflow = TextOverflow.Ellipsis,
        )
    }
}
