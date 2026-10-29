package com.dockstandby.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class StyleTheme { Colorful, Minimal }

enum class Palette { Sunset, Ocean, Aurora, Candy }

enum class ChargingAnimation(val label: String) { Liquid("Liquid"), Rings("Rings"), Off("Off") }

data class Settings(
    val theme: StyleTheme = StyleTheme.Colorful,
    val palette: Palette = Palette.Sunset,
    val nightMode: Boolean = true,
    val chargingAnimation: ChargingAnimation = ChargingAnimation.Liquid,
    val autoLaunch: Boolean = false,
    /** Minutes without interaction before dimming to the glance clock; 0 = never. */
    val dimAfterMin: Int = 10,
    /** Enabled pages in display order (across both themes). */
    val pages: List<PageId> = PageId.entries,
    /** Pick the page that fits the time of day and your calendar. */
    val smartSwitch: Boolean = true,
    /** One widget per slot in [widgetSlots]. */
    val widgets: List<WidgetType> = defaultWidgets,
    val countdownLabel: String = "",
    /** Countdown target as LocalDate.toEpochDay(), or null when not set. */
    val countdownDay: Long? = null,
    /** Show message text in banners even while the phone is locked (off = app name only). */
    val showContentLocked: Boolean = false,
    /** Packages whose notifications never show as banners. */
    val mutedApps: Set<String> = emptySet(),
    /** Home Assistant base URL (e.g. http://homeassistant.local:8123) and long-lived token. */
    val haUrl: String = "",
    val haToken: String = "",
    /** Entity ids shown on the Home page, in order. */
    val haEntities: List<String> = emptyList(),
    /** End of StandBy's own timer (epoch ms), or null when none is running. */
    val timerEndAt: Long? = null,
    /** Seconds each photo stays up in the slideshow. */
    val slideSeconds: Int = 12,
    /** Show "On this day" photos, then favourites, before the rest. */
    val memoriesFirst: Boolean = true,
    /** Tint the Colorful clock with colours from the current photo instead of the palette. */
    val matchPhotoColors: Boolean = false,
    /** Light haptic tap and soft chime when the phone is docked (auto-open on). */
    val dockHaptic: Boolean = true,
    val dockChime: Boolean = true,
    /** First-run walkthrough finished or skipped. */
    val onboarded: Boolean = false,
    val clockFace: ClockFace = ClockFace.Theme,
    val abstractStyle: AbstractStyle = AbstractStyle.Blobs,
    val checklist: List<ChecklistItem> = defaultChecklist,
)

data class ChecklistItem(val text: String, val done: Boolean)

private val defaultChecklist = listOf(
    ChecklistItem("Milk", false),
    ChecklistItem("Eggs", false),
    ChecklistItem("Bread", false),
    ChecklistItem("Coffee beans", true),
)

/** App settings backed by SharedPreferences, exposed as a process-wide StateFlow. */
class Prefs private constructor(context: Context) {
    private val sp: SharedPreferences =
        context.applicationContext.getSharedPreferences("standby", Context.MODE_PRIVATE)

    private val _settings = MutableStateFlow(read())
    val settings: StateFlow<Settings> = _settings.asStateFlow()

    private fun read() = Settings(
        theme = enumOr(sp.getString("theme", null), StyleTheme.Colorful),
        palette = enumOr(sp.getString("palette", null), Palette.Sunset),
        nightMode = sp.getBoolean("night", true),
        chargingAnimation = enumOr(sp.getString("chargeAnim", null), ChargingAnimation.Liquid),
        autoLaunch = sp.getBoolean("autoLaunch", false),
        dimAfterMin = sp.getInt("dimAfter", 10),
        pages = sp.getString("pages", null)?.let { decodeEnums<PageId>(it) }?.let { saved ->
            // Pages added in an app update start switched on. Installs from before this list was
            // saved knew every page except Abstract.
            val known = sp.getString("knownPages", null)?.let { decodeEnums<PageId>(it) } ?: (PageId.entries - PageId.Abstract)
            saved + PageId.entries.filter { it !in known && it !in saved }
        } ?: PageId.entries,
        smartSwitch = sp.getBoolean("smartSwitch", true),
        widgets = sp.getString("widgets", null)?.let { decodeEnums<WidgetType>(it) }?.takeIf { it.size == widgetSlots.size } ?: defaultWidgets,
        countdownLabel = sp.getString("countdownLabel", "") ?: "",
        countdownDay = sp.getLong("countdownDay", Long.MIN_VALUE).takeIf { it != Long.MIN_VALUE },
        showContentLocked = sp.getBoolean("showContentLocked", false),
        mutedApps = sp.getStringSet("mutedApps", null)?.toSet() ?: emptySet(),
        haUrl = sp.getString("haUrl", "") ?: "",
        haToken = sp.getString("haToken", "") ?: "",
        haEntities = sp.getString("haEntities", null)?.split(",")?.filter { it.isNotBlank() } ?: emptyList(),
        timerEndAt = sp.getLong("timerEndAt", 0L).takeIf { it > 0L },
        slideSeconds = sp.getInt("slideSeconds", 12),
        memoriesFirst = sp.getBoolean("memoriesFirst", true),
        matchPhotoColors = sp.getBoolean("matchPhotoColors", false),
        dockHaptic = sp.getBoolean("dockHaptic", true),
        dockChime = sp.getBoolean("dockChime", true),
        onboarded = sp.getBoolean("onboarded", false),
        clockFace = enumOr(sp.getString("clockFace", null), ClockFace.Theme),
        abstractStyle = enumOr(sp.getString("abstractStyle", null), AbstractStyle.Blobs),
        checklist = sp.getString("checklist", null)?.let(::decodeChecklist) ?: defaultChecklist,
    )

    fun update(block: (Settings) -> Settings) {
        val s = block(_settings.value)
        sp.edit()
            .putString("theme", s.theme.name)
            .putString("palette", s.palette.name)
            .putBoolean("night", s.nightMode)
            .putString("chargeAnim", s.chargingAnimation.name)
            .putBoolean("autoLaunch", s.autoLaunch)
            .putInt("dimAfter", s.dimAfterMin)
            .putString("pages", s.pages.joinToString(",") { it.name })
            .putString("knownPages", PageId.entries.joinToString(",") { it.name })
            .putBoolean("smartSwitch", s.smartSwitch)
            .putString("widgets", s.widgets.joinToString(",") { it.name })
            .putString("countdownLabel", s.countdownLabel)
            .putLong("countdownDay", s.countdownDay ?: Long.MIN_VALUE)
            .putBoolean("showContentLocked", s.showContentLocked)
            .putStringSet("mutedApps", s.mutedApps)
            .putString("haUrl", s.haUrl)
            .putString("haToken", s.haToken)
            .putString("haEntities", s.haEntities.joinToString(","))
            .putLong("timerEndAt", s.timerEndAt ?: 0L)
            .putInt("slideSeconds", s.slideSeconds)
            .putBoolean("memoriesFirst", s.memoriesFirst)
            .putBoolean("matchPhotoColors", s.matchPhotoColors)
            .putBoolean("dockHaptic", s.dockHaptic)
            .putBoolean("dockChime", s.dockChime)
            .putBoolean("onboarded", s.onboarded)
            .putString("clockFace", s.clockFace.name)
            .putString("abstractStyle", s.abstractStyle.name)
            .putString("checklist", encodeChecklist(s.checklist))
            .apply()
        _settings.value = s
    }

    /** Raw access for small counters (e.g. the step baseline). */
    val raw: SharedPreferences get() = sp

    companion object {
        @Volatile private var instance: Prefs? = null
        fun get(context: Context): Prefs =
            instance ?: synchronized(this) { instance ?: Prefs(context).also { instance = it } }
    }
}

private inline fun <reified E : Enum<E>> enumOr(name: String?, default: E): E =
    enumValues<E>().firstOrNull { it.name == name } ?: default

private fun encodeChecklist(items: List<ChecklistItem>) =
    items.joinToString("\n") { (if (it.done) "1" else "0") + it.text }

private fun decodeChecklist(s: String) =
    s.lines().filter { it.length > 1 }.map { ChecklistItem(it.drop(1), it[0] == '1') }

private inline fun <reified E : Enum<E>> decodeEnums(s: String): List<E> =
    s.split(',').mapNotNull { name -> enumValues<E>().firstOrNull { it.name == name } }
