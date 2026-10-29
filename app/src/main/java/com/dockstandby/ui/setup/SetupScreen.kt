package com.dockstandby.ui.setup

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.dockstandby.AutoLaunchService
import com.dockstandby.StandByActivity
import com.dockstandby.StandByDreamService
import com.dockstandby.data.ChargingAnimation
import com.dockstandby.data.Palette
import com.dockstandby.data.Prefs
import com.dockstandby.data.StyleTheme
import com.dockstandby.data.hasNotificationAccess
import com.dockstandby.data.hasPermission
import com.dockstandby.data.hasPhotoAccess
import com.dockstandby.data.photoPermissions
import com.dockstandby.ui.standby.glow
import com.dockstandby.data.PageId
import com.dockstandby.data.WidgetType
import androidx.compose.foundation.text.BasicTextField
import com.dockstandby.ui.theme.C
import com.dockstandby.ui.theme.CssLine
import com.dockstandby.ui.theme.Inter
import com.dockstandby.ui.theme.Nunito
import com.dockstandby.ui.theme.colors

internal fun Context.isOurScreensaver(): Boolean {
    val v = Settings.Secure.getString(contentResolver, "screensaver_components") ?: return false
    val me = ComponentName(this, StandByDreamService::class.java)
    return v.split(',').any { ComponentName.unflattenFromString(it) == me }
}

internal fun Context.openDreamSettings() = runCatching {
    startActivity(Intent(Settings.ACTION_DREAM_SETTINGS))
}.onFailure { startActivity(Intent(Settings.ACTION_DISPLAY_SETTINGS)) }

internal fun Context.openOverlaySettings() = startActivity(
    Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, android.net.Uri.parse("package:$packageName")),
)

@Composable
fun SetupScreen() {
    val context = LocalContext.current
    val prefs = remember { Prefs.get(context) }
    val s by prefs.settings.collectAsState()

    // Bumped on resume / permission results so the permission rows re-check.
    val refresh = remember { mutableIntStateOf(0) }
    // Coming back from a system settings page may have granted the overlay permission.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { refresh.intValue++; AutoLaunchService.sync(context) }
    val askPhotos = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { refresh.intValue++ }
    val askNotifications =rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { AutoLaunchService.sync(context) }
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { refresh.intValue++ }
    refresh.intValue

    val appearance: @Composable ColumnScope.() -> Unit = {
        Text("StandBy", Modifier.padding(horizontal = 4.dp), color = Color.White, fontFamily = Inter, fontWeight = FontWeight.Bold, fontSize = 34.sp, letterSpacing = (-.6).sp)
        SectionLabel("THEME")
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            ThemeCard("Colorful", s.theme == StyleTheme.Colorful, Modifier.weight(1f), { prefs.update { it.copy(theme = StyleTheme.Colorful) } }) {
                val p = s.palette.colors
                Box(Modifier.size(140.dp, 80.dp).glow(p.c, .6f, 30.dp))
                Text("10:42", style = TextStyle(lineHeightStyle = CssLine, brush = p.textBrush, fontFamily = Nunito, fontWeight = FontWeight.Black, fontSize = 40.sp))
            }
            ThemeCard("Minimal", s.theme == StyleTheme.Minimal, Modifier.weight(1f), { prefs.update { it.copy(theme = StyleTheme.Minimal) } }) {
                Text(
                    buildAnnotatedString { append("10"); withStyle(SpanStyle(color = C.Orange)) { append(":") }; append("42") },
                    color = C.Text, fontFamily = Inter, fontWeight = FontWeight.Thin, fontSize = 46.sp, letterSpacing = (-2).sp,
                )
            }
        }
        if (s.theme == StyleTheme.Colorful) Row(
            Modifier.padding(horizontal = 6.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Palette", Modifier.weight(1f), color = C.Gray2, fontFamily = Inter, fontSize = 14.sp)
            Palette.entries.forEach { pal ->
                val c = pal.colors
                val selected = pal == s.palette
                Box(
                    Modifier.size(30.dp).then(if (selected) Modifier.border(2.dp, Color.White, CircleShape) else Modifier)
                        .padding(4.dp).clip(CircleShape).background(Brush.linearGradient(listOf(c.a, c.c)))
                        .clickable { prefs.update { it.copy(palette = pal) } },
                )
            }
        }

        Group {
            // On when the user asked for it and Android allows it (overlay permission granted).
            val autoOn = s.autoLaunch && AutoLaunchService.canAutoLaunch(context)
            fun setAuto(on: Boolean) {
                prefs.update { it.copy(autoLaunch = on) }
                if (on) {
                    if (Build.VERSION.SDK_INT >= 33) askNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
                    if (!AutoLaunchService.canAutoLaunch(context)) context.openOverlaySettings()
                }
                AutoLaunchService.sync(context)
            }
            Item("Auto-open when docked", onClick = { setAuto(!autoOn) }) {
                Switch(
                    autoOn, { setAuto(it) },
                    colors = SwitchDefaults.colors(checkedTrackColor = C.Green, checkedThumbColor = Color.White),
                )
            }
            Item("Night mode (red)", divider = true, onClick = { prefs.update { it.copy(nightMode = !it.nightMode) } }) {
                Switch(
                    s.nightMode, { v -> prefs.update { it.copy(nightMode = v) } },
                    colors = SwitchDefaults.colors(checkedTrackColor = C.Green, checkedThumbColor = Color.White),
                )
            }
            Item("Charging animation", divider = true, onClick = {
                prefs.update { val all = ChargingAnimation.entries; it.copy(chargingAnimation = all[(it.chargingAnimation.ordinal + 1) % all.size]) }
            }) {
                Text("${s.chargingAnimation.label} ›", color = C.Gray2, fontFamily = Inter, fontSize = 15.sp)
            }
            // Cycles Off → 5 → 10 → 20 → 30 minutes.
            Item("Dim when idle", divider = true, onClick = {
                prefs.update { val opts = listOf(0, 5, 10, 20, 30); it.copy(dimAfterMin = opts[(opts.indexOf(it.dimAfterMin) + 1) % opts.size]) }
            }) {
                Text(if (s.dimAfterMin == 0) "Off ›" else "After ${s.dimAfterMin} min ›", color = C.Gray2, fontFamily = Inter, fontSize = 15.sp)
            }
        }
        LayoutSections(s, prefs)
    }
    val access: @Composable ColumnScope.() -> Unit = {
        val perms = buildList {
            add(Perm("Calendar", context.hasPermission(Manifest.permission.READ_CALENDAR)) { ask.launch(Manifest.permission.READ_CALENDAR) })
            add(Perm("Location (weather)", context.hasPermission(Manifest.permission.ACCESS_COARSE_LOCATION)) { ask.launch(Manifest.permission.ACCESS_COARSE_LOCATION) })
            add(Perm("Now Playing (notification access)", context.hasNotificationAccess()) {
                context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
            })
            if (Build.VERSION.SDK_INT >= 29) add(Perm("Physical activity (steps)", context.hasPermission(Manifest.permission.ACTIVITY_RECOGNITION)) {
                ask.launch(Manifest.permission.ACTIVITY_RECOGNITION)
            })
            add(Perm("Photos (slideshow)", context.hasPhotoAccess()) { askPhotos.launch(photoPermissions) })
            add(Perm("Open over other apps (auto-open)", AutoLaunchService.canAutoLaunch(context)) { context.openOverlaySettings() })
            add(Perm("Screen saver set to StandBy", context.isOurScreensaver()) { context.openDreamSettings() })
        }
        SectionLabel("PERMISSIONS · ${perms.count { it.granted }} OF ${perms.size}")
        Group {
            perms.forEachIndexed { i, p ->
                Item(p.label, divider = i > 0, leading = {
                    Box(Modifier.size(8.dp).clip(CircleShape).background(if (p.granted) C.Green else C.Orange))
                }) {
                    if (!p.granted) Text(
                        "Allow",
                        Modifier.clip(CircleShape).background(C.Blue).clickable(onClick = p.request).padding(horizontal = 14.dp, vertical = 6.dp),
                        color = Color.White, fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 14.sp,
                    )
                }
            }
        }
        Text(
            "On Xiaomi, Realme, Oppo and similar phones, also allow autostart for StandBy in the system app settings.",
            Modifier.padding(horizontal = 6.dp), color = C.Gray, fontFamily = Inter, fontSize = 12.sp,
        )

        Spacer(Modifier.height(8.dp))
        Box(
            Modifier.fillMaxWidth().height(54.dp).clip(RoundedCornerShape(16.dp)).background(Color.White)
                .clickable { context.startActivity(Intent(context, StandByActivity::class.java)) },
            contentAlignment = Alignment.Center,
        ) {
            Text("Preview StandBy", color = Color.Black, fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 17.sp)
        }
    }

    // Follows the phone's rotation: one column in portrait (as in the mockup), two side by side in landscape.
    BoxWithConstraints(Modifier.fillMaxSize().background(Color.Black).windowInsetsPadding(WindowInsets.safeDrawing)) {
        val column = Modifier.verticalScroll(rememberScrollState())
        if (maxWidth > maxHeight) {
            Row(Modifier.fillMaxSize().padding(horizontal = 18.dp), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                Column(Modifier.weight(1f).fillMaxHeight().then(column).padding(vertical = 20.dp), verticalArrangement = Arrangement.spacedBy(12.dp), content = appearance)
                Column(Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState()).padding(vertical = 20.dp), verticalArrangement = Arrangement.spacedBy(12.dp), content = access)
            }
        } else {
            Column(
                Modifier.fillMaxSize().then(column).padding(start = 18.dp, end = 18.dp, top = 36.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                appearance()
                access()
            }
        }
    }
}

private class Perm(val label: String, val granted: Boolean, val request: () -> Unit)

@Composable
private fun SectionLabel(text: String) =
    Text(text, Modifier.padding(start = 6.dp, end = 6.dp, top = 8.dp), color = C.Gray2, fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, letterSpacing = .4.sp)

@Composable
private fun ThemeCard(name: String, selected: Boolean, modifier: Modifier, onClick: () -> Unit, preview: @Composable () -> Unit) {
    val shape = RoundedCornerShape(22.dp)
    Column(
        modifier.clip(shape).border(if (selected) 3.dp else 1.dp, if (selected) C.Blue else C.Separator, shape)
            .background(Color.Black).clickable(onClick = onClick),
    ) {
        Box(Modifier.fillMaxWidth().height(96.dp), contentAlignment = Alignment.Center) { preview() }
        Text(
            name, Modifier.fillMaxWidth().background(C.Card).padding(10.dp), color = Color.White,
            fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
    }
}

@Composable
private fun Group(content: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(C.Card)) { content() }
}

@Composable
private fun Item(
    label: String,
    divider: Boolean = false,
    onClick: (() -> Unit)? = null,
    leading: (@Composable () -> Unit)? = null,
    trailing: @Composable () -> Unit,
) {
    Column {
        if (divider) Box(Modifier.fillMaxWidth().height(1.dp).background(C.Separator))
        Row(
            Modifier.fillMaxWidth().heightIn(min = 50.dp).then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (leading != null) { leading(); Spacer(Modifier.width(10.dp)) }
            Text(label, Modifier.weight(1f), color = Color.White, fontFamily = Inter, fontSize = 16.sp)
            trailing()
        }
    }
}


/** Pages (order, visibility, smart switching), widget slots and the countdown target. */
@Composable
private fun LayoutSections(s: com.dockstandby.data.Settings, prefs: Prefs) {
    val context = LocalContext.current
    // Pages that exist in the current theme: enabled ones in their saved order, then the rest.
    val inTheme = PageId.entries.filter { s.theme in it.themes }
    val enabled = s.pages.filter { it in inTheme }
    val rows = enabled + inTheme.filter { it !in enabled }

    SectionLabel("PAGES")
    Group {
        Item("Smart switching", onClick = { prefs.update { it.copy(smartSwitch = !it.smartSwitch) } }) {
            Switch(
                s.smartSwitch, { v -> prefs.update { it.copy(smartSwitch = v) } },
                colors = SwitchDefaults.colors(checkedTrackColor = C.Green, checkedThumbColor = Color.White),
            )
        }
        rows.forEach { page ->
            val on = page in enabled
            val pos = enabled.indexOf(page)
            Item(page.title, divider = true) {
                if (on) {
                    Arrow("▲", pos > 0) { prefs.update { it.copy(pages = it.pages.moveWithin(page, -1, inTheme)) } }
                    Arrow("▼", pos < enabled.size - 1) { prefs.update { it.copy(pages = it.pages.moveWithin(page, +1, inTheme)) } }
                }
                Switch(
                    on,
                    { v ->
                        prefs.update {
                            when {
                                v -> it.copy(pages = it.pages + page)
                                it.pages.count { p -> p in inTheme } > 1 -> it.copy(pages = it.pages - page)
                                else -> it // keep at least one page
                            }
                        }
                    },
                    colors = SwitchDefaults.colors(checkedTrackColor = C.Green, checkedThumbColor = Color.White),
                )
            }
        }
    }
    Text(
        "Smart switching shows the agenda in the morning and 10 min before events, Photos in the evening, " +
            "and the clock otherwise. A swipe pauses it for 10 minutes.",
        Modifier.padding(horizontal = 6.dp), color = C.Gray, fontFamily = Inter, fontSize = 12.sp,
    )

    if (s.theme == StyleTheme.Colorful) {
        SectionLabel("WIDGETS · LONG-PRESS ONE IN STANDBY TO CHANGE IT")
        Group {
            val names = listOf("Large", "Top right", "Bottom left", "Bottom right")
            s.widgets.forEachIndexed { slot, type ->
                Item(names[slot], divider = slot > 0, onClick = {
                    prefs.update { st ->
                        val all = WidgetType.entries
                        st.copy(widgets = st.widgets.toMutableList().also { it[slot] = all[(type.ordinal + 1) % all.size] })
                    }
                }) {
                    Text("${type.label} ›", color = C.Gray2, fontFamily = Inter, fontSize = 15.sp)
                }
            }
        }
    }

    SectionLabel("COUNTDOWN")
    Group {
        Row(Modifier.fillMaxWidth().heightIn(min = 50.dp).padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Name", Modifier.width(90.dp), color = Color.White, fontFamily = Inter, fontSize = 16.sp)
            BasicTextField(
                s.countdownLabel,
                { v -> prefs.update { it.copy(countdownLabel = v.take(24)) } },
                Modifier.weight(1f),
                singleLine = true,
                textStyle = TextStyle(color = Color.White, fontFamily = Inter, fontSize = 16.sp),
                cursorBrush = androidx.compose.ui.graphics.SolidColor(C.Blue),
                decorationBox = { inner ->
                    if (s.countdownLabel.isEmpty()) Text("e.g. Holiday", color = C.Gray, fontFamily = Inter, fontSize = 16.sp)
                    inner()
                },
            )
        }
        val date = s.countdownDay?.let { java.time.LocalDate.ofEpochDay(it) }
        Item("Date", divider = true, onClick = {
            val d = date ?: java.time.LocalDate.now().plusDays(7)
            android.app.DatePickerDialog(context, { _, y, m, day ->
                prefs.update { it.copy(countdownDay = java.time.LocalDate.of(y, m + 1, day).toEpochDay()) }
            }, d.year, d.monthValue - 1, d.dayOfMonth).show()
        }) {
            Text((date?.toString() ?: "Not set") + " ›", color = C.Gray2, fontFamily = Inter, fontSize = 15.sp)
        }
    }
    NotificationSection(s, prefs)
    FaceSection(s, prefs)
    PhotoSection(s, prefs)
    DockingSection(s, prefs)
    HomeAssistantSection(s, prefs)
}

@Composable
private fun Arrow(glyph: String, enabled: Boolean, onClick: () -> Unit) {
    Text(
        glyph,
        Modifier.clip(CircleShape).clickable(enabled = enabled, onClick = onClick).padding(horizontal = 10.dp, vertical = 6.dp),
        color = if (enabled) Color.White else C.Separator, fontSize = 14.sp,
    )
}

/** Moves [page] one step ([dir] = ±1) among the pages of [group], leaving other pages in place. */
private fun List<PageId>.moveWithin(page: PageId, dir: Int, group: List<PageId>): List<PageId> {
    val slots = indices.filter { this[it] in group }
    val at = slots.indexOf(indexOf(page))
    val to = at + dir
    if (at < 0 || to !in slots.indices) return this
    return toMutableList().also {
        val a = slots[at]; val b = slots[to]
        it[a] = this[b]; it[b] = this[a]
    }
}

/** Banner privacy and the per-app mute list (apps that posted a banner recently, plus muted ones). */
@Composable
private fun NotificationSection(s: com.dockstandby.data.Settings, prefs: Prefs) {
    val seen by com.dockstandby.data.NotificationRepo.seenApps.collectAsState()
    SectionLabel("NOTIFICATION BANNERS")
    Group {
        Item("Show message text when locked", onClick = { prefs.update { it.copy(showContentLocked = !it.showContentLocked) } }) {
            Switch(
                s.showContentLocked, { v -> prefs.update { it.copy(showContentLocked = v) } },
                colors = SwitchDefaults.colors(checkedTrackColor = C.Green, checkedThumbColor = Color.White),
            )
        }
        val apps = (seen.keys + s.mutedApps).distinct()
        apps.forEach { pkg ->
            val muted = pkg in s.mutedApps
            Item(seen[pkg] ?: pkg, divider = true) {
                Text(
                    if (muted) "Muted" else "Shown",
                    Modifier.clip(CircleShape).background(if (muted) C.Separator else C.Green.copy(alpha = .25f))
                        .clickable { prefs.update { it.copy(mutedApps = if (muted) it.mutedApps - pkg else it.mutedApps + pkg) } }
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    color = Color.White, fontFamily = Inter, fontSize = 14.sp,
                )
            }
        }
    }
    Text(
        "Banners follow Do Not Disturb and are hidden in night mode. Apps appear here after their first banner; tap to mute.",
        Modifier.padding(horizontal = 6.dp), color = C.Gray, fontFamily = Inter, fontSize = 12.sp,
    )
}

/** URL + long-lived token, then pick up to 8 devices for the Home page. */
@Composable
private fun HomeAssistantSection(s: com.dockstandby.data.Settings, prefs: Prefs) {
    val scope = rememberCoroutineScope()
    var url by remember(s.haUrl) { mutableStateOf(s.haUrl) }
    var token by remember(s.haToken) { mutableStateOf(s.haToken) }
    var status by remember { mutableStateOf<String?>(null) }
    var available by remember { mutableStateOf<List<com.dockstandby.data.HaEntity>>(emptyList()) }

    SectionLabel("HOME ASSISTANT")
    Group {
        Field("URL", url, "http://homeassistant.local:8123") { url = it.trim() }
        Field("Token", token, "Long-lived access token", secret = true, divider = true) { token = it.trim() }
        Item(status ?: "Connect and load devices", divider = true, onClick = {
            prefs.update { it.copy(haUrl = url, haToken = token) }
            status = "Connecting…"
            scope.launch {
                val result = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    runCatching { com.dockstandby.data.HomeAssistant(url, token).states() }
                }
                result.onSuccess { available = it; status = "Connected · ${it.size} devices" }
                    .onFailure { status = it.message ?: "Can't connect" }
            }
        }) {
            Text("›", color = C.Gray2, fontFamily = Inter, fontSize = 18.sp)
        }
        // Chosen devices first (in order), then the rest of what Home Assistant offered.
        val chosen = s.haEntities
        val names = available.associate { it.id to it.name }
        (chosen + available.map { it.id }.filter { it !in chosen }).forEach { id ->
            val on = id in chosen
            Item(names[id] ?: id, divider = true, onClick = {
                prefs.update {
                    when {
                        on -> it.copy(haEntities = it.haEntities - id)
                        it.haEntities.size < 8 -> it.copy(haEntities = it.haEntities + id)
                        else -> it
                    }
                }
            }) {
                Text(if (on) "✓" else "", color = C.Green, fontFamily = Inter, fontWeight = FontWeight.Bold, fontSize = 18.sp)
            }
        }
    }
    Text(
        "Create a token in Home Assistant under your profile › Security › Long-lived access tokens. " +
            "Pick up to 8 devices, then turn on the Home page above. The token is stored only on this phone.",
        Modifier.padding(horizontal = 6.dp), color = C.Gray, fontFamily = Inter, fontSize = 12.sp,
    )
}

@Composable
private fun Field(label: String, value: String, hint: String, secret: Boolean = false, divider: Boolean = false, onChange: (String) -> Unit) {
    Column {
        if (divider) Box(Modifier.fillMaxWidth().height(1.dp).background(C.Separator))
        Row(Modifier.fillMaxWidth().heightIn(min = 50.dp).padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(label, Modifier.width(90.dp), color = Color.White, fontFamily = Inter, fontSize = 16.sp)
            BasicTextField(
                value, onChange, Modifier.weight(1f),
                singleLine = true,
                textStyle = TextStyle(color = Color.White, fontFamily = Inter, fontSize = 15.sp),
                cursorBrush = androidx.compose.ui.graphics.SolidColor(C.Blue),
                visualTransformation = if (secret) androidx.compose.ui.text.input.PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None,
                decorationBox = { inner ->
                    if (value.isEmpty()) Text(hint, color = C.Gray, fontFamily = Inter, fontSize = 15.sp, maxLines = 1)
                    inner()
                },
            )
        }
    }
}

/** Slideshow speed, memories ordering, and whether the Colorful clock borrows the photo's colours. */
@Composable
private fun PhotoSection(s: com.dockstandby.data.Settings, prefs: Prefs) {
    SectionLabel("PHOTOS")
    Group {
        Item("Change photo every", onClick = {
            prefs.update { val opts = listOf(8, 12, 20, 30, 60); it.copy(slideSeconds = opts[(opts.indexOf(it.slideSeconds) + 1) % opts.size]) }
        }) {
            Text("${s.slideSeconds} s ›", color = C.Gray2, fontFamily = Inter, fontSize = 15.sp)
        }
        Item("Memories first", divider = true, onClick = { prefs.update { it.copy(memoriesFirst = !it.memoriesFirst) } }) {
            Switch(
                s.memoriesFirst, { v -> prefs.update { it.copy(memoriesFirst = v) } },
                colors = SwitchDefaults.colors(checkedTrackColor = C.Green, checkedThumbColor = Color.White),
            )
        }
        Item("Match clock colours to photo", divider = true, onClick = { prefs.update { it.copy(matchPhotoColors = !it.matchPhotoColors) } }) {
            Switch(
                s.matchPhotoColors, { v -> prefs.update { it.copy(matchPhotoColors = v) } },
                colors = SwitchDefaults.colors(checkedTrackColor = C.Green, checkedThumbColor = Color.White),
            )
        }
    }
    Text(
        "Memories first shows photos from this day in past years, then favourites. The Photos page always " +
            "tints its clock from the photo; the switch also applies it to the Colorful clock page.",
        Modifier.padding(horizontal = 6.dp), color = C.Gray, fontFamily = Inter, fontSize = 12.sp,
    )
}

/** What happens when the phone is placed on the dock. */
@Composable
private fun DockingSection(s: com.dockstandby.data.Settings, prefs: Prefs) {
    SectionLabel("WHEN DOCKED")
    Group {
        Item("Haptic tap", onClick = { prefs.update { it.copy(dockHaptic = !it.dockHaptic) } }) {
            Switch(s.dockHaptic, { v -> prefs.update { it.copy(dockHaptic = v) } }, colors = SwitchDefaults.colors(checkedTrackColor = C.Green, checkedThumbColor = Color.White))
        }
        Item("Chime", divider = true, onClick = { prefs.update { it.copy(dockChime = !it.dockChime) } }) {
            Switch(s.dockChime, { v -> prefs.update { it.copy(dockChime = v) } }, colors = SwitchDefaults.colors(checkedTrackColor = C.Green, checkedThumbColor = Color.White))
        }
    }
    Text(
        "Plays when auto-open is on and you plug in. Silent in Do Not Disturb, between 22:00 and 07:00, and when the phone is on silent.",
        Modifier.padding(horizontal = 6.dp), color = C.Gray, fontFamily = Inter, fontSize = 12.sp,
    )
}

/** Clock face for the Clock page, and the Abstract page's style. */
@Composable
private fun FaceSection(s: com.dockstandby.data.Settings, prefs: Prefs) {
    SectionLabel("CLOCK FACE")
    Group {
        Item("Clock page", onClick = {
            prefs.update { val all = com.dockstandby.data.ClockFace.entries; it.copy(clockFace = all[(it.clockFace.ordinal + 1) % all.size]) }
        }) {
            Text("${s.clockFace.label} ›", color = C.Gray2, fontFamily = Inter, fontSize = 15.sp)
        }
        Item("Abstract style", divider = true, onClick = {
            prefs.update { val all = com.dockstandby.data.AbstractStyle.entries; it.copy(abstractStyle = all[(it.abstractStyle.ordinal + 1) % all.size]) }
        }) {
            Text("${s.abstractStyle.label} ›", color = C.Gray2, fontFamily = Inter, fontSize = 15.sp)
        }
    }
    Text(
        "Tip: double-tap the Clock page in StandBy to try the next face. Turn the Abstract page on or off under Pages.",
        Modifier.padding(horizontal = 6.dp), color = C.Gray, fontFamily = Inter, fontSize = 12.sp,
    )
}
