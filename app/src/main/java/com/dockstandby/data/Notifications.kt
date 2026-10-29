package com.dockstandby.data

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.graphics.Bitmap
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** A notification worth showing as a StandBy banner. */
data class Banner(
    val key: String,
    val pkg: String,
    val appName: String,
    val icon: Bitmap?,
    val title: String,
    val text: String,
    val isCall: Boolean,
    val postedAt: Long,
    val contentIntent: PendingIntent?,
)

/** A running (or paused) timer / stopwatch read from the Clock app's ongoing notification. */
data class ClockTimer(
    val key: String,
    val pkg: String,
    val label: String,
    /** Wall-clock ms the chronometer counts toward (count-down) or from (stopwatch); null if paused. */
    val base: Long?,
    val countDown: Boolean,
    /** What the notification itself says, used when paused. */
    val text: String,
)

/** An alarm or finished timer that is ringing right now, with the notification's own buttons. */
data class Ringing(
    val key: String,
    val title: String,
    val text: String,
    val snooze: PendingIntent?,
    val stop: PendingIntent?,
)

/** Process-wide notification state, fed by [MediaListenerService]. */
object NotificationRepo {
    private val _banners = MutableStateFlow<List<Banner>>(emptyList())
    val banners: StateFlow<List<Banner>> = _banners.asStateFlow()

    private val _timers = MutableStateFlow<List<ClockTimer>>(emptyList())
    val timers: StateFlow<List<ClockTimer>> = _timers.asStateFlow()

    private val _ringing = MutableStateFlow<Ringing?>(null)
    val ringing: StateFlow<Ringing?> = _ringing.asStateFlow()

    /** Apps seen recently, for the mute list in setup (package → label). */
    private val _seenApps = MutableStateFlow<Map<String, String>>(emptyMap())
    val seenApps: StateFlow<Map<String, String>> = _seenApps.asStateFlow()

    internal fun posted(b: Banner) {
        _banners.update { (listOf(b) + it.filter { o -> o.key != b.key }).take(10) }
        _seenApps.update { (it + (b.pkg to b.appName)).entries.toList().takeLast(20).associate { e -> e.key to e.value } }
    }
    internal fun removed(key: String) {
        _banners.update { it.filter { b -> b.key != key } }
        _timers.update { it.filter { t -> t.key != key } }
        if (_ringing.value?.key == key) _ringing.value = null
    }
    internal fun timer(t: ClockTimer) = _timers.update { listOf(t) + it.filter { o -> o.key != t.key } }
    internal fun clearTimers(pkg: String) = _timers.update { it.filter { t -> t.pkg != pkg } }
    internal fun ringing(r: Ringing) { _ringing.value = r }

    fun dismissBanner(key: String) = _banners.update { it.filter { b -> b.key != key } }
}

private val clockPackages = setOf(
    "com.google.android.deskclock",
    "com.sec.android.app.clockpackage",
    "com.android.deskclock",
)

/**
 * Notification access gives us: the active media session (Now Playing), message/call banners,
 * the Clock app's timers, and ringing alarms.
 */
class MediaListenerService : NotificationListenerService() {

    override fun onNotificationPosted(sbn: StatusBarNotification, rankingMap: RankingMap) {
        if (sbn.packageName == packageName) return
        val n = sbn.notification
        val extras = n.extras
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
        val text = (extras.getCharSequence(Notification.EXTRA_BIG_TEXT) ?: extras.getCharSequence(Notification.EXTRA_TEXT))?.toString().orEmpty()

        // Ringing alarm / finished timer. Google Clock uses a full-screen alarm notification with
        // Snooze / Dismiss actions. Samsung Clock posts a category=alarm notification with no
        // actions and shows its own popup; with nothing for us to press, we leave that to Samsung.
        if (n.category == Notification.CATEGORY_ALARM && (n.fullScreenIntent != null || sbn.packageName in clockPackages)) {
            fun action(vararg words: String) = n.actions?.firstOrNull { a ->
                words.any { w -> a.title?.toString()?.contains(w, ignoreCase = true) == true }
            }?.actionIntent
            val stop = action("dismiss", "stop", "off", "cancel") ?: n.actions?.lastOrNull()?.actionIntent
            if (stop != null) {
                NotificationRepo.ringing(Ringing(sbn.key, title.ifBlank { "Alarm" }, text, snooze = action("snooze"), stop = stop))
            }
            // The timer that just fired is no longer counting down.
            NotificationRepo.clearTimers(sbn.packageName)
            return
        }

        // Clock app timers and stopwatches (ongoing). Skip group summaries, which duplicate the
        // real timer notification on Samsung.
        if (sbn.packageName in clockPackages && sbn.isOngoing) {
            if (n.flags and Notification.FLAG_GROUP_SUMMARY != 0) return
            NotificationRepo.timer(parseClockTimer(sbn, title, text))
            return
        }

        val isCall = n.category == Notification.CATEGORY_CALL
        // Skip what wouldn't interrupt the user anyway: ongoing (except calls), summaries,
        // media players, silent notifications, and anything Do Not Disturb would block.
        if (sbn.isOngoing && !isCall) return
        if (n.flags and Notification.FLAG_GROUP_SUMMARY != 0) return
        if (extras.getString(Notification.EXTRA_TEMPLATE)?.contains("MediaStyle") == true) return
        val ranking = Ranking()
        if (rankingMap.getRanking(sbn.key, ranking)) {
            if (!ranking.matchesInterruptionFilter()) return
            if (ranking.importance < NotificationManager.IMPORTANCE_DEFAULT) return
        }
        if (title.isBlank() && text.isBlank()) return

        val pm = packageManager
        val appName = runCatching { pm.getApplicationLabel(pm.getApplicationInfo(sbn.packageName, 0)).toString() }.getOrDefault(sbn.packageName)
        val icon = runCatching { pm.getApplicationIcon(sbn.packageName).toBitmap(96, 96) }.getOrNull()
        NotificationRepo.posted(
            Banner(sbn.key, sbn.packageName, appName, icon, title, text, isCall, System.currentTimeMillis(), n.contentIntent),
        )
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) = NotificationRepo.removed(sbn.key)

    override fun onListenerConnected() {
        // Pick up timers / a ringing alarm already showing; don't replay old messages as banners.
        runCatching {
            activeNotifications?.forEach { sbn ->
                val n = sbn.notification
                val isAlarm = n.category == Notification.CATEGORY_ALARM && n.fullScreenIntent != null
                if (isAlarm || (sbn.packageName in clockPackages && sbn.isOngoing)) onNotificationPosted(sbn, currentRanking)
            }
        }
    }
}

/** Whether banner text may be shown now: unlocked, or the user opted in to content while locked. */
fun Context.mayShowNotificationContent(showContentLocked: Boolean): Boolean {
    val km = getSystemService(android.app.KeyguardManager::class.java)
    return showContentLocked || km?.isKeyguardLocked != true
}

/**
 * Reads a Clock-app timer notification.
 *  - Google Clock: the standard chronometer extras (count-down toward `when`).
 *  - Samsung Clock: no chronometer extras. It puts "Label / 6:53 pm" (label and end time, to the
 *    minute) in a Samsung-only extra, and `when` is the moment the timer started. Most timers are
 *    whole minutes, so the exact end is start + N minutes landing inside that end minute; if none
 *    fits, the middle of the minute is used. Anything without an end time is shown as paused.
 */
internal fun parseClockTimer(sbn: StatusBarNotification, title: String, text: String): ClockTimer {
    val n = sbn.notification
    val e = n.extras
    if (e.getBoolean(Notification.EXTRA_SHOW_CHRONOMETER)) {
        return ClockTimer(sbn.key, sbn.packageName, title.ifBlank { "Timer" }, n.`when`, e.getBoolean(Notification.EXTRA_CHRONOMETER_COUNT_DOWN), text)
    }
    val info = e.getCharSequence("android.ongoingActivityNoti.secondaryInfo")?.toString()
    val parts = info?.split(" / ")
    val label = parts?.firstOrNull()?.takeIf { it.isNotBlank() } ?: title.ifBlank { "Timer" }
    val endMinute = parts?.getOrNull(1)?.let(::nextOccurrence)
    val base = endMinute?.let { end ->
        val k = Math.round((end + 30_000 - n.`when`) / 60_000.0)
        val exact = n.`when` + k * 60_000
        if (exact in end until end + 60_000) exact else end + 30_000
    }
    return ClockTimer(sbn.key, sbn.packageName, label, base, countDown = true, text = parts?.getOrNull(1) ?: text.ifBlank { "Paused" })
}

private val clockTimeFormats = listOf("h:mm a", "H:mm").map {
    java.time.format.DateTimeFormatterBuilder().parseCaseInsensitive().appendPattern(it).toFormatter(java.util.Locale.ENGLISH)
}

/** "6:53 pm" / "18:53" → epoch ms of its next occurrence (today, or tomorrow if already past). */
private fun nextOccurrence(s: String): Long? {
    val t = clockTimeFormats.firstNotNullOfOrNull { f -> runCatching { java.time.LocalTime.parse(s.trim(), f) }.getOrNull() } ?: return null
    val now = java.time.LocalDateTime.now()
    var at = now.toLocalDate().atTime(t)
    if (at.isBefore(now.minusMinutes(1))) at = at.plusDays(1)
    return at.atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
}
