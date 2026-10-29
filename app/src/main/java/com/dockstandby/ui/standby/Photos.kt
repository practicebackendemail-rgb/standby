package com.dockstandby.ui.standby

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dockstandby.data.StyleTheme
import com.dockstandby.data.hasPhotoAccess
import com.dockstandby.data.Memory
import com.dockstandby.data.rememberSlideshow
import com.dockstandby.ui.theme.C
import com.dockstandby.ui.theme.CssLine
import com.dockstandby.ui.theme.Inter
import com.dockstandby.ui.theme.Nunito
import com.dockstandby.ui.theme.chargeColors
import com.dockstandby.ui.theme.clock
import com.dockstandby.ui.theme.colors
import com.dockstandby.ui.theme.longDate
import com.dockstandby.ui.theme.shortDate

/** Full-bleed photo slideshow (slow zoom + crossfade) with the time over a soft bottom scrim. */
@Composable
fun PhotosScreen(d: StandByData) {
    val slideMs = d.settings.slideSeconds * 1000
    val slide by rememberSlideshow(slideMs.toLong(), d.settings.memoriesFirst)
    val colorful = d.settings.theme == StyleTheme.Colorful
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        Crossfade(slide, animationSpec = tween(1200), label = "photo") { s ->
            if (s == null) {
                EmptyPhotos(colorful, d)
            } else {
                val bmp = s.bitmap
                // Ken Burns: each photo slowly zooms in, panning one way or the other.
                val zoom = remember(bmp) { Animatable(1f) }
                LaunchedEffect(bmp) { zoom.animateTo(1.1f, tween(slideMs + 1200, easing = LinearEasing)) }
                val dir = if (bmp.generationId % 2 == 0) 1f else -1f
                Image(
                    bmp.asImageBitmap(), null,
                    Modifier.fillMaxSize().graphicsLayer {
                        scaleX = zoom.value; scaleY = zoom.value
                        translationX = dir * (zoom.value - 1f) * size.width * .3f
                    },
                    contentScale = ContentScale.Crop,
                )
            }
        }
        Box(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(200.dp)
                .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = .6f)))),
        )
        BatteryChip(d, Modifier.align(Alignment.TopEnd).padding(top = 24.dp, end = 28.dp))
        slide?.memory?.let { MemoryCaption(it, Modifier.align(Alignment.TopStart).padding(top = 24.dp, start = 28.dp)) }
        Column(Modifier.align(Alignment.BottomStart).padding(start = 44.dp, bottom = 34.dp)) {
            // On a light spot the date goes dark and the glow shadow is dropped.
            val light = slide?.colors?.lightBackground == true
            val shadow = if (light) null else Shadow(Color.Black.copy(alpha = .4f), blurRadius = 24f)
            val ink = if (light) Color(0xFF1C1C1E) else Color.White
            // Clock colours come from the photo (already lifted to stay readable on the scrim).
            val a = slide?.colors?.a?.let(::Color) ?: Color.White
            val b = slide?.colors?.b?.let(::Color) ?: Color.White
            if (colorful) {
                Text(
                    d.now.clock(d.is24h),
                    style = TextStyle(lineHeightStyle = CssLine, brush = cssLinearGradient(120f, 0f to a, 1f to b), shadow = shadow, fontFamily = Nunito, fontWeight = FontWeight.Black, fontSize = 96.sp, lineHeight = 88.sp, letterSpacing = (-3).sp),
                )
                Text(d.now.longDate(), style = TextStyle(lineHeightStyle = CssLine, color = ink.copy(alpha = .85f), shadow = shadow, fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 18.sp))
            } else {
                Text(
                    d.now.clock(d.is24h),
                    style = TextStyle(lineHeightStyle = CssLine, color = a, shadow = shadow, fontFamily = Inter, fontWeight = FontWeight.Thin, fontSize = 120.sp, lineHeight = 114.sp, letterSpacing = (-5).sp),
                )
                Text(d.now.shortDate().uppercase(), style = TextStyle(lineHeightStyle = CssLine, color = ink.copy(alpha = .8f), shadow = shadow, fontFamily = Inter, fontSize = 13.sp, letterSpacing = 2.4.sp))
            }
        }
    }
}

@Composable
private fun MemoryCaption(m: Memory, modifier: Modifier) {
    Text(
        when (m) {
            is Memory.OnThisDay -> "On this day · ${m.year}"
            Memory.Favourite -> "♥ Favourite"
        },
        modifier.clip(CircleShape).background(Color.Black.copy(alpha = .35f)).padding(horizontal = 14.dp, vertical = 6.dp),
        color = Color.White, fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 14.sp,
    )
}

@Composable
private fun BatteryChip(d: StandByData, modifier: Modifier) {
    val (c1, _) = chargeColors(d.battery.level)
    Row(
        modifier.clip(CircleShape).background(Color.Black.copy(alpha = .35f)).padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(Modifier.padding(1.dp).clip(CircleShape).background(if (d.battery.charging) c1 else Color.White).padding(4.dp))
        Text("${d.battery.level}%", color = Color.White, fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
    }
}

/** No photos yet: the palette glow with a hint, so the page never looks broken. */
@Composable
private fun EmptyPhotos(colorful: Boolean, d: StandByData) {
    val p = d.settings.palette.colors
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        if (colorful) {
            Box(Modifier.padding(start = 120.dp, bottom = 60.dp).fillMaxSize(.6f).glow(p.b, .5f, 90.dp))
            Box(Modifier.padding(start = 360.dp, top = 120.dp).fillMaxSize(.5f).glow(p.c, .45f, 100.dp))
        }
        Text(
            if (LocalContext.current.hasPhotoAccess()) "No photos on this phone yet"
            else "Allow Photos in setup to show your pictures here",
            Modifier.padding(bottom = 80.dp),
            color = if (colorful) Color.White.copy(alpha = .8f) else C.Gray, fontFamily = Inter, fontWeight = FontWeight.Medium, fontSize = 17.sp,
        )
    }
}
