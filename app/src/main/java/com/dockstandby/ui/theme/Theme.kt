package com.dockstandby.ui.theme

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import com.dockstandby.R
import com.dockstandby.data.Palette
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

@OptIn(ExperimentalTextApi::class)
private fun variable(res: Int, vararg weights: Int) = FontFamily(
    weights.map { w ->
        Font(res, FontWeight(w), variationSettings = FontVariation.Settings(FontVariation.weight(w)))
    },
)

val Inter = variable(R.font.inter, 100, 200, 300, 400, 500, 600, 700, 800)
val Nunito = variable(R.font.nunito, 300, 600, 700, 800, 900)

object C {
    val Text = Color(0xFFF5F5F7)
    val Gray = Color(0xFF6E6E73)
    val Gray2 = Color(0xFF8E8E93)
    val Gray3 = Color(0xFFA1A1A6)
    val Hairline = Color(0xFF1C1C1E)
    val Card = Color(0xFF1C1C1E)
    val Separator = Color(0xFF2C2C2E)
    val Orange = Color(0xFFFF9F0A)
    val Red = Color(0xFFFF453A)
    val CalRed = Color(0xFFFF3B30)
    val Green = Color(0xFF30D158)
    val Teal = Color(0xFF30D5C8)
    val Blue = Color(0xFF0A84FF)
    val Sky = Color(0xFF64D2FF)
}

/** Three-stop gradient + the two glow colours behind the clock. */
data class PaletteColors(val a: Color, val b: Color, val c: Color) {
    val textBrush get() = Brush.linearGradient(listOf(a, b, c))
}

val Palette.colors get() = when (this) {
    Palette.Sunset -> PaletteColors(Color(0xFFFFD60A), Color(0xFFFF7A45), Color(0xFFFF2D92))
    Palette.Ocean -> PaletteColors(Color(0xFF64D2FF), Color(0xFF0A84FF), Color(0xFF5E5CE6))
    Palette.Aurora -> PaletteColors(Color(0xFF30D158), Color(0xFF30D5C8), Color(0xFFBF5AF2))
    Palette.Candy -> PaletteColors(Color(0xFFFF9FF3), Color(0xFFBF5AF2), Color(0xFF64D2FF))
}

/** Charging fill colours by level, per the design notes. */
fun chargeColors(level: Int): Pair<Color, Color> = when {
    level < 20 -> Color(0xFFFF2D55) to Color(0xFFFF6B2C)
    level < 50 -> Color(0xFFFF9F0A) to Color(0xFFFFD60A)
    else -> Color(0xFF30D158) to Color(0xFF30D5C8)
}

// ---- formatting ----

private fun fmt(p: String) = DateTimeFormatter.ofPattern(p, Locale.getDefault())

fun LocalDateTime.clock(is24h: Boolean): String =
    format(fmt(if (is24h) "HH:mm" else "h:mm"))
fun LocalDateTime.hhmm24(): String = format(fmt("HH:mm"))
fun LocalDateTime.shortTime(is24h: Boolean): String =
    format(fmt(if (is24h) "HH:mm" else "h:mm a"))
fun LocalDateTime.longDate(): String = format(fmt("EEEE, d MMMM"))
fun LocalDateTime.weekday(): String = format(fmt("EEEE")).uppercase()
fun LocalDateTime.shortDate(): String = format(fmt("EEE d MMM"))

fun formatDuration(ms: Long): String {
    val s = (ms / 1000).coerceAtLeast(0)
    return "%d:%02d".format(s / 60, s % 60)
}

fun formatMinutes(min: Int): String =
    if (min < 60) "$min min" else "${min / 60} h ${min % 60} min"

/** CSS line boxes: line-height applies to the first/last line too, glyphs centred in the box. */
val CssLine = androidx.compose.ui.text.style.LineHeightStyle(
    androidx.compose.ui.text.style.LineHeightStyle.Alignment.Center,
    androidx.compose.ui.text.style.LineHeightStyle.Trim.None,
)

/** Makes [CssLine] the default for every Text below, so sizes match the HTML mockups. */
@androidx.compose.runtime.Composable
fun CssText(content: @androidx.compose.runtime.Composable () -> Unit) =
    androidx.compose.material3.ProvideTextStyle(androidx.compose.ui.text.TextStyle(lineHeightStyle = CssLine), content)
