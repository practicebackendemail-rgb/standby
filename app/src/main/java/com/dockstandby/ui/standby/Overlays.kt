package com.dockstandby.ui.standby

import android.app.PendingIntent
import android.media.AudioAttributes
import android.media.Ringtone
import android.media.RingtoneManager
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dockstandby.data.Banner
import com.dockstandby.data.ClockTimer
import com.dockstandby.data.NotificationRepo
import com.dockstandby.data.Prefs
import com.dockstandby.data.Ringing
import com.dockstandby.data.mayShowNotificationContent
import com.dockstandby.ui.theme.C
import com.dockstandby.ui.theme.Inter
import com.dockstandby.ui.theme.Nunito
import kotlinx.coroutines.delay

private const val BANNER_MS = 8_000L

/** Current wall-clock time, ticking every second (overlays need seconds even when pages don't). */
@Composable
private fun rememberMillis() = produceState(System.currentTimeMillis()) {
    while (true) {
        value = System.currentTimeMillis()
        delay(1000 - value % 1000)
    }
}

/**
 * The newest message as a banner at the top of the screen for 8 s (calls stay while ringing).
 * Muted apps never show; while locked only the app name shows unless the user opted in.
 * A new banner also wakes the screen from the glance clock.
 */
@Composable
fun BannerOverlay(d: StandByData, presence: Presence, modifier: Modifier = Modifier) {
    val all by NotificationRepo.banners.collectAsState()
    val now by rememberMillis()
    val banner = all.firstOrNull { it.pkg !in d.settings.mutedApps && (it.isCall || now - it.postedAt < BANNER_MS) }
    LaunchedEffect(banner?.key) { if (banner != null) presence.poke("notification") }
    val context = LocalContext.current
    val showContent = context.mayShowNotificationContent(d.settings.showContentLocked)
    Box(modifier.fillMaxSize().padding(top = 18.dp), contentAlignment = Alignment.TopCenter) {
        AnimatedVisibility(
            banner != null,
            enter = slideInVertically { -it } + fadeIn(),
            exit = slideOutVertically { -it } + fadeOut(),
        ) {
            // Keep the last banner around while it animates out.
            val last = remember { arrayOfNulls<Banner>(1) }
            val b = banner ?: last[0] ?: return@AnimatedVisibility
            last[0] = b
            BannerCard(b, showContent) { NotificationRepo.dismissBanner(b.key) }
        }
    }
}

@Composable
private fun BannerCard(b: Banner, showContent: Boolean, onDismiss: () -> Unit) {
    Row(
        Modifier.widthIn(max = 560.dp).shadow(24.dp, RoundedCornerShape(26.dp)).clip(RoundedCornerShape(26.dp))
            .background(if (b.isCall) C.Green else Color(0xF22C2C2E)).clickable(onClick = onDismiss)
            .padding(horizontal = 16.dp, vertical = if (b.isCall) 16.dp else 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        b.icon?.let { Image(it.asImageBitmap(), null, Modifier.size(if (b.isCall) 44.dp else 36.dp).clip(RoundedCornerShape(10.dp))) }
        Column(Modifier.weight(1f, fill = false)) {
            Text(
                if (b.isCall) "Incoming call" else b.appName,
                color = Color.White.copy(alpha = .7f), fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 12.sp,
            )
            if (showContent || b.isCall) {
                Text(b.title, color = Color.White, fontFamily = Inter, fontWeight = FontWeight.Bold, fontSize = if (b.isCall) 22.sp else 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (!b.isCall && b.text.isNotBlank()) Text(b.text, color = Color.White.copy(alpha = .85f), fontFamily = Inter, fontSize = 14.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
            } else {
                Text("New notification", color = Color.White, fontFamily = Inter, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            }
        }
    }
}

/** Big timer chips (Clock-app timers and StandBy's own) in the top-right corner. */
@Composable
fun TimerOverlay(d: StandByData, prefs: Prefs, modifier: Modifier = Modifier) {
    val clockTimers by NotificationRepo.timers.collectAsState()
    val now by rememberMillis()
    val own = d.settings.timerEndAt?.takeIf { it > now }
    if (clockTimers.isEmpty() && own == null) return
    Column(modifier.fillMaxSize().padding(top = 18.dp, end = 22.dp), horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        // Tap StandBy's own timer to cancel it.
        own?.let { TimerChip("Timer · tap to cancel", formatClock(it - now)) { prefs.update { s -> s.copy(timerEndAt = null) } } }
        clockTimers.take(2).forEach { t -> TimerChip(t.label, t.display(now)) }
    }
}

private fun ClockTimer.display(now: Long): String = when {
    base == null -> text.ifBlank { "Paused" }
    countDown -> formatClock(base - now)
    else -> formatClock(now - base)
}

private fun formatClock(ms: Long): String {
    val s = (ms.coerceAtLeast(0) + 999) / 1000
    val h = s / 3600
    return if (h > 0) "%d:%02d:%02d".format(h, s / 60 % 60, s % 60) else "%d:%02d".format(s / 60, s % 60)
}

@Composable
private fun TimerChip(label: String, time: String, onClick: (() -> Unit)? = null) {
    Column(
        Modifier.clip(RoundedCornerShape(22.dp)).background(Brush.linearGradient(listOf(Color(0xFFFF9F0A), Color(0xFFFF6B2C))))
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 18.dp, vertical = 10.dp),
        horizontalAlignment = Alignment.End,
    ) {
        Text(label, color = Color.White.copy(alpha = .85f), fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 12.sp, maxLines = 1)
        Text(time, color = Color.White, fontFamily = Nunito, fontWeight = FontWeight.Black, fontSize = 40.sp, lineHeight = 42.sp)
    }
}

/**
 * Full-screen Snooze / Stop panel for a ringing alarm or Clock timer (using the notification's own
 * actions), or for StandBy's own timer when it ends (plays the default alarm sound).
 * Returns true when a panel is showing, so the caller can wake the screen.
 */
@Composable
fun RingingOverlay(d: StandByData, prefs: Prefs, presence: Presence): Boolean {
    val ringing by NotificationRepo.ringing.collectAsState()
    val now by rememberMillis()
    val ownDone = d.settings.timerEndAt?.let { it <= now } == true
    val r = ringing
    when {
        r != null -> RingingPanel(r.title, r.text, onSnooze = r.snooze?.let { { send(it) } }, onStop = { r.stop?.let(::send) })
        ownDone -> {
            OwnTimerSound()
            RingingPanel(
                "Timer", "Time's up",
                onSnooze = { prefs.update { it.copy(timerEndAt = System.currentTimeMillis() + 60_000) } },
                snoozeLabel = "+1 min",
                onStop = { prefs.update { it.copy(timerEndAt = null) } },
            )
        }
        else -> return false
    }
    LaunchedEffect(r?.key, ownDone) { presence.poke("ringing") }
    return true
}

private fun send(pi: PendingIntent) = runCatching { pi.send() }

/** Loops the default alarm sound while composed. */
@Composable
private fun OwnTimerSound() {
    val context = LocalContext.current
    DisposableEffect(Unit) {
        val uri = RingtoneManager.getActualDefaultRingtoneUri(context, RingtoneManager.TYPE_ALARM)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
        val tone: Ringtone? = RingtoneManager.getRingtone(context, uri)?.apply {
            audioAttributes = AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).build()
            if (android.os.Build.VERSION.SDK_INT >= 28) isLooping = true
            play()
        }
        onDispose { tone?.stop() }
    }
}

@Composable
private fun RingingPanel(title: String, text: String, onSnooze: (() -> Unit)?, onStop: () -> Unit, snoozeLabel: String = "Snooze") {
    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = .92f)).clickable(enabled = false) {}, contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(title, color = Color.White, fontFamily = Nunito, fontWeight = FontWeight.Black, fontSize = 64.sp, lineHeight = 66.sp, maxLines = 1)
            if (text.isNotBlank()) Text(text, color = Color.White.copy(alpha = .7f), fontFamily = Inter, fontSize = 18.sp, maxLines = 1)
            Row(Modifier.padding(top = 26.dp), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                onSnooze?.let { PanelButton(snoozeLabel, C.Orange, it) }
                PanelButton("Stop", C.Red, onStop)
            }
        }
    }
}

@Composable
private fun PanelButton(label: String, color: Color, onClick: () -> Unit) {
    Box(
        Modifier.width(200.dp).clip(CircleShape).background(color).clickable(onClick = onClick).padding(vertical = 20.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = Color.White, fontFamily = Inter, fontWeight = FontWeight.Bold, fontSize = 22.sp)
    }
}

/** Quick timer presets, opened by long-pressing the Clock page. */
@Composable
fun QuickTimerSheet(onStart: (minutes: Int) -> Unit, onDismiss: () -> Unit) {
    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = .85f)).clickable(onClick = onDismiss), contentAlignment = Alignment.Center) {
        Column(
            Modifier.clip(RoundedCornerShape(28.dp)).background(C.Card).clickable(enabled = false) {}.padding(22.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text("Start a timer", color = Color.White, fontFamily = Inter, fontWeight = FontWeight.Bold, fontSize = 18.sp)
            listOf(listOf(1, 3, 5, 10), listOf(15, 20, 30, 60)).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    row.forEach { m ->
                        Box(
                            Modifier.size(96.dp, 64.dp).clip(RoundedCornerShape(18.dp)).background(C.Separator).clickable { onStart(m) },
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(if (m == 60) "1 h" else "$m min", color = Color.White, fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 18.sp)
                        }
                    }
                }
            }
        }
    }
}
