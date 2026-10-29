package com.dockstandby.ui.standby

import com.dockstandby.data.CalEvent
import com.dockstandby.data.PageId
import java.time.Duration
import java.time.LocalDateTime

/** How long a manual swipe keeps smart switching from moving the page. */
const val SMART_PAUSE_MS = 10 * 60_000L

/** An event starting within the next 10 minutes, if any. */
fun CalEvent.startsSoon(now: LocalDateTime): Boolean {
    if (allDay) return false
    val mins = Duration.between(now, start).toMinutes()
    return mins in 0..10
}

/**
 * The page that best fits the moment, from the user's enabled [pages]:
 *  - an event starts within 10 minutes → the agenda page
 *  - 06:00–10:00 → the agenda page (weather + today's events)
 *  - from 19:00 → Photos
 *  - otherwise → Clock
 * Charging / music / night are handled elsewhere. Returns null if no candidate is enabled.
 */
fun smartPage(now: LocalDateTime, events: List<CalEvent>, pages: List<PageId>): PageId? {
    val agenda = listOf(PageId.WeatherCalendar, PageId.Day)
    val candidates = when {
        events.any { it.startsSoon(now) } -> agenda
        now.hour in 6 until 10 -> agenda
        now.hour >= 19 -> listOf(PageId.Photos, PageId.Clock)
        else -> listOf(PageId.Clock)
    }
    return candidates.firstOrNull { it in pages }
}
