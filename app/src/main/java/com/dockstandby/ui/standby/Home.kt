package com.dockstandby.ui.standby

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dockstandby.data.HaEntity
import com.dockstandby.data.HomeAssistant
import com.dockstandby.data.StyleTheme
import com.dockstandby.data.rememberHomeAssistant
import com.dockstandby.ui.theme.C
import com.dockstandby.ui.theme.Inter
import com.dockstandby.ui.theme.Nunito
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Home Assistant tiles (up to 8, 4 × 2). Tap toggles a device or runs a scene/script. */
@Composable
fun HomeScreen(d: StandByData) {
    val s = d.settings
    val refresh = remember { mutableIntStateOf(0) }
    val snap by rememberHomeAssistant(s, intervalMs = 2_000, refreshKey = refresh.intValue)
    val scope = rememberCoroutineScope()
    val colorful = s.theme == StyleTheme.Colorful
    Column(Modifier.fillMaxSize().background(Color.Black).padding(26.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                if (colorful) "Home" else "HOME", Modifier.weight(1f),
                color = if (colorful) Color.White else C.Gray,
                fontFamily = if (colorful) Nunito else Inter,
                fontWeight = if (colorful) FontWeight.Black else FontWeight.Normal,
                fontSize = if (colorful) 30.sp else 13.sp, letterSpacing = if (colorful) 0.sp else 2.4.sp,
            )
            snap.error?.let { Text(it, color = C.Orange, fontFamily = Inter, fontSize = 14.sp) }
        }
        val tiles = snap.entities.take(8)
        if (tiles.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    if (!snap.loaded) "Loading…" else if (s.haEntities.isEmpty()) "Choose devices in setup › Home Assistant" else "No devices found",
                    color = C.Gray, fontFamily = Inter, fontSize = 17.sp,
                )
            }
            return@Column
        }
        tiles.chunked(4).forEach { row ->
            Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                row.forEach { e ->
                    HaTile(e, colorful, Modifier.weight(1f).fillMaxHeight()) {
                        scope.launch {
                            withContext(Dispatchers.IO) { runCatching { HomeAssistant(s.haUrl, s.haToken).activate(e) } }
                            refresh.intValue++
                        }
                    }
                }
                repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
        if (tiles.size <= 4) Spacer(Modifier.weight(1f))
    }
}

@Composable
private fun HaTile(e: HaEntity, colorful: Boolean, modifier: Modifier, onTap: () -> Unit) {
    val accent = when (e.domain) {
        "light" -> Color(0xFFFFD60A)
        "fan" -> Color(0xFF64D2FF)
        "scene", "script" -> Color(0xFFBF5AF2)
        "lock" -> Color(0xFF30D158)
        else -> Color(0xFFFF9F0A)
    }
    val on = e.isOn
    val bg = when {
        colorful && on -> Brush.linearGradient(listOf(accent, accent.copy(alpha = .7f)))
        on -> Brush.linearGradient(listOf(C.Separator, C.Separator))
        else -> Brush.linearGradient(listOf(C.Card, C.Card))
    }
    Column(
        modifier.clip(RoundedCornerShape(26.dp)).background(bg).clickable(onClick = onTap).padding(16.dp),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Box(Modifier.size(14.dp).clip(CircleShape).background(if (on) (if (colorful) Color.White else accent) else Color(0xFF48484A)))
        Column {
            Text(
                e.name, color = if (on && colorful) Color(0xFF1C1C1E) else Color.White,
                fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, maxLines = 2, overflow = TextOverflow.Ellipsis,
            )
            Text(
                if (e.isAction) "Tap to run" else e.state.replaceFirstChar(Char::uppercase),
                color = (if (on && colorful) Color(0xFF1C1C1E) else Color.White).copy(alpha = .6f),
                fontFamily = Inter, fontSize = 13.sp,
            )
        }
    }
}

