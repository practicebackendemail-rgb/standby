package com.dockstandby.data

/** Every StandBy page, and which themes it exists in. Order here is the default order. */
enum class PageId(val title: String, val themes: Set<StyleTheme>) {
    Clock("Clock", setOf(StyleTheme.Colorful, StyleTheme.Minimal)),
    Charging("Charging", setOf(StyleTheme.Colorful, StyleTheme.Minimal)),
    WeatherCalendar("Weather + Calendar", setOf(StyleTheme.Colorful)),
    Day("Day", setOf(StyleTheme.Minimal)),
    NowPlaying("Now Playing", setOf(StyleTheme.Colorful)),
    Widgets("Widgets", setOf(StyleTheme.Colorful)),
    Analog("Analog", setOf(StyleTheme.Minimal)),
    Photos("Photos", setOf(StyleTheme.Colorful, StyleTheme.Minimal)),
    Home("Home", setOf(StyleTheme.Colorful, StyleTheme.Minimal)),
    Abstract("Abstract", setOf(StyleTheme.Colorful, StyleTheme.Minimal)),
}

/** Widgets that can go in any of the four widget-stack slots. */
enum class WidgetType(val label: String) {
    Rings("Activity rings"),
    Battery("Battery"),
    Alarm("Next alarm"),
    NextEvent("Next up"),
    WorldClock("World clock"),
    Countdown("Countdown"),
    Checklist("Checklist"),
    Quote("Quote of the day"),
}

/** Slot shapes in the widget stack: one large, one wide, two small. */
enum class WidgetSlot { Large, Wide, Small }

val widgetSlots = listOf(WidgetSlot.Large, WidgetSlot.Wide, WidgetSlot.Small, WidgetSlot.Small)
val defaultWidgets = listOf(WidgetType.Rings, WidgetType.Checklist, WidgetType.Alarm, WidgetType.NextEvent)

/** The user's page order for [theme]: enabled pages that exist in that theme, never empty. */
fun Settings.pagesFor(theme: StyleTheme): List<PageId> =
    pages.filter { theme in it.themes }.ifEmpty { listOf(PageId.Clock) }

/** What the Clock page shows. [Theme] is the Colorful / Minimal clock from the mockups. */
enum class ClockFace(val label: String) {
    Theme("Theme clock"),
    Flip("Flip"),
    Word("Word"),
    Neon("Neon"),
    Orbit("Solar orbit"),
    DotMatrix("Dot matrix"),
    Stacked("Stacked"),
}

/** Styles for the Abstract page. */
enum class AbstractStyle(val label: String) {
    Blobs("Gradient blobs"),
    Generative("Generative"),
    Particles("Particles"),
    Waves("Light waves"),
}
