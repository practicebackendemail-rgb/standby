package com.dockstandby.ui.setup

import android.Manifest
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.dockstandby.AutoLaunchService
import com.dockstandby.data.Prefs
import com.dockstandby.data.hasNotificationAccess
import com.dockstandby.data.hasPermission
import com.dockstandby.data.hasPhotoAccess
import com.dockstandby.data.photoPermissions
import com.dockstandby.ui.theme.C
import com.dockstandby.ui.theme.CssLine
import com.dockstandby.ui.theme.Inter
import com.dockstandby.ui.theme.Nunito
import com.dockstandby.ui.theme.colors

/** One walkthrough card: what it's for, and how to grant it (null = nothing to grant). */
private class Step(
    val title: String,
    val body: String,
    val granted: () -> Boolean = { false },
    val grant: (() -> Unit)? = null,
    val grantLabel: String = "Allow",
)

/**
 * First-run walkthrough: explains each feature and asks for its permission at that moment.
 * Every step can be skipped; everything stays available later in setup.
 */
@Composable
fun Onboarding(onDone: () -> Unit) {
    val context = LocalContext.current
    val prefs = remember { Prefs.get(context) }
    var index by remember { mutableIntStateOf(0) }
    // Re-check grants when returning from a system settings screen or a permission dialog.
    var tick by remember { mutableIntStateOf(0) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { tick++ }
    val one = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { tick++ }
    val many = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { tick++ }

    val steps = remember {
        buildList {
            add(Step("Welcome to StandBy", "Turn your phone into a bedside or desk clock whenever it charges. A few quick permissions make it useful, and each one is optional."))
            add(
                Step(
                    "Your day at a glance", "Calendar shows today's events. Location gives local weather.",
                    granted = { context.hasPermission(Manifest.permission.READ_CALENDAR) && context.hasPermission(Manifest.permission.ACCESS_COARSE_LOCATION) },
                    grant = { many.launch(arrayOf(Manifest.permission.READ_CALENDAR, Manifest.permission.ACCESS_COARSE_LOCATION)) },
                ),
            )
            add(
                Step(
                    "Music, messages and timers",
                    "Notification access lets StandBy show what's playing, quiet message banners (text hidden while locked) and your Clock timers.",
                    granted = { context.hasNotificationAccess() },
                    grant = { context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) }, grantLabel = "Open settings",
                ),
            )
            add(
                Step(
                    "Your photos", "Show a slideshow of the pictures on this phone, with memories from this day in past years.",
                    granted = { context.hasPhotoAccess() },
                    grant = { many.launch(photoPermissions) },
                ),
            )
            if (Build.VERSION.SDK_INT >= 33) add(
                Step(
                    "Background notification", "Auto-open keeps a small, silent notification so Android lets StandBy react when you plug in.",
                    granted = { context.hasPermission(Manifest.permission.POST_NOTIFICATIONS) },
                    grant = { one.launch(Manifest.permission.POST_NOTIFICATIONS) },
                ),
            )
            add(
                Step(
                    "Open when you dock", "Allow StandBy to appear over other apps so it can open the moment charging starts.",
                    granted = { AutoLaunchService.canAutoLaunch(context) },
                    grant = {
                        prefs.update { it.copy(autoLaunch = true) }
                        context.openOverlaySettings()
                    },
                    grantLabel = "Open settings",
                ),
            )
            add(
                Step(
                    "Also as a screen saver", "Optional: pick StandBy as the screen saver so it also appears after the screen times out while charging.",
                    granted = { context.isOurScreensaver() },
                    grant = { context.openDreamSettings() }, grantLabel = "Open settings",
                ),
            )
            add(Step("You're all set", "Place your phone on its charger sideways. Long-press the clock for a timer, long-press a widget to change it, and fine-tune everything in setup."))
        }
    }
    val step = steps[index]
    @Suppress("UNUSED_EXPRESSION") tick // read so grants refresh after returning
    val done = step.granted()
    val p = prefs.settings.value.palette.colors
    fun next() {
        if (index < steps.lastIndex) index++ else {
            AutoLaunchService.sync(context)
            prefs.update { it.copy(onboarded = true) }
            onDone()
        }
    }

    Box(Modifier.fillMaxSize().background(Color.Black).windowInsetsPadding(WindowInsets.safeDrawing), contentAlignment = Alignment.Center) {
        Column(
            Modifier.widthIn(max = 520.dp).verticalScroll(rememberScrollState()).padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            AnimatedContent(index, transitionSpec = { (slideInHorizontally { it / 3 } + fadeIn()) togetherWith fadeOut() }, label = "step") { i ->
                val s = steps[i]
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        s.title,
                        style = TextStyle(
                            lineHeightStyle = CssLine, brush = Brush.linearGradient(listOf(p.a, p.b, p.c)),
                            fontFamily = Nunito, fontWeight = FontWeight.Black, fontSize = 32.sp, lineHeight = 36.sp, textAlign = TextAlign.Center,
                        ),
                    )
                    Text(s.body, color = C.Gray3, fontFamily = Inter, fontSize = 16.sp, lineHeight = 22.sp, textAlign = TextAlign.Center)
                }
            }
            val grant = step.grant
            if (grant != null) {
                if (done) Text("✓ Allowed", color = C.Green, fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                else Pill(step.grantLabel, C.Blue, grant)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
                if (grant != null && !done) {
                    Text("Skip", Modifier.clip(CircleShape).clickable { next() }.padding(horizontal = 18.dp, vertical = 12.dp), color = C.Gray2, fontFamily = Inter, fontSize = 16.sp)
                }
                Pill(if (index == steps.lastIndex) "Start" else "Next", Color.White, ::next, ink = Color.Black)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                steps.indices.forEach { i ->
                    Box(Modifier.size(if (i == index) 16.dp else 6.dp, 6.dp).clip(CircleShape).background(if (i == index) Color.White else C.Separator))
                }
            }
        }
    }
}

@Composable
private fun Pill(label: String, bg: Color, onClick: () -> Unit, ink: Color = Color.White) {
    Text(
        label,
        Modifier.clip(CircleShape).background(bg).clickable(onClick = onClick).padding(horizontal = 28.dp, vertical = 14.dp),
        color = ink, fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 17.sp,
    )
}
