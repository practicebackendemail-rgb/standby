# StandBy roadmap

Fifteen improvements grouped into five phases. Each phase ships on its own and leaves the app
working; later phases build on earlier ones (e.g. smart switching needs the page registry from
phase 2, notifications need the dimming/wake logic from phase 1).

Sizes: **S** ≈ half a day, **M** ≈ 1–2 days, **L** ≈ 3+ days.

---

## Phase 1 — Safe to leave on all night

The app is on screen for hours, so this comes first.

### 2. Always-on dimming · M
- New `DisplayController` owned by `StandByActivity` / `StandByDreamService`. It sets
  `window.attributes.screenBrightness` (activity) or `screenBright`/brightness (dream).
- Brightness follows the ambient light sensor through a smoothed curve (reuse the sensor already in
  `rememberIsDark`), clamped to a user min/max.
- After **N minutes without interaction or motion** (setting, default 10), fade to a "glance" level
  (~5%). Night mode keeps its own very low red level.
- Setting: *Dim when idle* (on/off, delay).
- **Done when:** in a dark room the screen sits at minimum, in daylight it is readable, and it dims
  after the idle delay without ever turning fully off.

### 3. Burn-in protection · S
- `DesignCanvas` gets a pixel-shift offset: every 60 s move content by up to ±4 dp along a slow
  Lissajous path, so nothing stays on the same pixels for long. Static high-contrast parts (clock
  digits, page dots) also get a very slow opacity breathe in night mode.
- Page dots fade out after 5 s of no swiping.
- **Done when:** a 1-hour screen recording shows every element drifting; nothing is pixel-static.

### 4. Wake on approach · M
- Proximity sensor (`TYPE_PROXIMITY`) and accelerometer tap/pick-up detection
  (`TYPE_SIGNIFICANT_MOTION` where available, else a threshold on linear acceleration).
- Any of these restores full brightness from the idle level (item 2) and resets the idle timer.
- Sensors are registered only while StandBy is visible, at the lowest useful rate.
- **Done when:** waving a hand over the top of the phone or tapping the desk brightens it within
  ~300 ms.

### 15a. Battery and performance pass · S
- Pause weather polling, step counting, the slideshow and media callbacks for pages that are not
  visible (the pager currently keeps neighbours composed).
- Turn off infinite animations when the screen is dimmed to glance level.
- **Done when:** an overnight session on the charger shows no wakelocks other than the screen's own
  in `dumpsys batterystats`.

---

## Phase 2 — It knows what you need

### 5. Choose and reorder pages · M
- Replace the hard-coded `colorfulPages` / `minimalPages` lists with a page registry:
  `PageSpec(id, title, themes, defaultOrder)`.
- `Prefs` stores the enabled page ids in order. Setup gets a *Pages* section: drag handles to
  reorder, a switch per page, a live thumbnail.
- **Done when:** hiding Widgets and moving Photos first is reflected immediately in StandBy and
  survives a restart.

### 1. Smart page switching · M
- A small rules engine evaluated every minute and on events (power, alarm, media, calendar):
  - 06:00–10:00 → Weather + Calendar (Day in Minimal)
  - 10 min before a calendar event → the page showing that event, with the event highlighted
  - Music starts → Now Playing (exists today)
  - Evening (after 19:00) → Photos
  - Dark room → Night (exists today)
- Only pages the user enabled (item 5) are candidates. A manual swipe pauses automation for
  10 minutes.
- Setting: *Smart switching* (on/off) with a short explanation of the rules.
- **Done when:** with demo time set to 07:00 / 13:50 (event at 14:00) / 20:00, StandBy lands on the
  expected page without any input.

### 6. Widget picker · M
- Widget stack gets four slots; each slot picks from: Steps ring, Battery, Next alarm, Next event,
  World clock, Countdown (to a date the user enters), Checklist (exists), Quote of the day (bundled
  offline list).
- Long-press a widget in StandBy or use setup to change it.
- **Done when:** every widget type renders in each slot size and the choice persists.

---

## Phase 3 — Useful at a glance

### 9. Live notifications · M
- Extend `MediaListenerService` (already a `NotificationListenerService`) to stream posted
  notifications into a `NotificationRepo`.
- A quiet banner slides in at the top of any page for messages and calls: app icon, sender, first
  line; auto-hides after 8 s. Calls get a larger card with the caller name.
- Privacy: when the phone is locked, show only app name + "New message" unless the user enables
  *Show content on StandBy*. Respect Do Not Disturb and per-app mute in setup.
- **Done when:** a test message shows a banner, is hidden while locked with privacy on, and never
  appears during DND.

### 10. Timers and alarms · M
- Android has no public API to read another app's timers, so: (a) parse the Clock app's ongoing
  timer/stopwatch notification via the listener above, and (b) add StandBy's own timer, started by
  tapping the clock.
- When an alarm rings (Clock app's alarm notification / full-screen intent detected), show a
  full-screen Snooze / Stop panel that fires the notification's own actions.
- **Done when:** a running Clock-app timer appears large on StandBy, and a ringing alarm can be
  snoozed from StandBy. *Limit:* works with Google Clock and Samsung Clock; other clock apps may
  need extra parsing.

### 11. Smart home controls · L
- Android does not let third-party apps use other apps' device controls, so pick a backend:
  - **Home Assistant** (recommended): user enters URL + long-lived token; StandBy shows chosen
    entities (lights, scenes, switches) as tiles and toggles them over the REST API.
  - **Google Home APIs** for Android as a later option (requires Google developer registration).
- New *Home* page and an optional widget slot.
- **Done when:** a light in Home Assistant toggles from StandBy and its state updates within 2 s.

---

## Phase 4 — Your photos, beautifully

### 7. Google Photos albums + memories · L
- Add the **Google Photos Picker API** (Google Sign-In + a Google Cloud project with the Photos
  Picker API enabled). Since March 2025 apps can only see items the user picks, so the flow is:
  "Choose photos" opens the picker, picked items are downloaded and cached on device (picker URLs
  expire), and the cache feeds the existing slideshow.
- **Memories** from local photos: "On this day" (same day in previous years, via
  `MediaStore.Images.Media.DATE_TAKEN`) and "Recent favourites" (`IS_FAVORITE` on Android 11+),
  shown as a small caption on the slideshow.
- Setting: slideshow source (Phone / Google Photos / Both), interval.
- **Done when:** photos picked in Google Photos play in the slideshow offline, and an "On this day"
  caption appears when such photos exist.

### 8. Clock colours from the photo · S
- Use `androidx.palette` to extract vibrant/muted colours from the current slide; tint the Photos
  clock and, optionally (setting *Match palette to photo*), the Colorful clock gradient.
- **Done when:** the clock colour changes with each slide and always meets 4.5:1 contrast against
  the scrim.

---

## Phase 5 — Polish and ship

### 12. Smooth transitions · M
- Rolling digits: each digit animates with `AnimatedContent` (slide up + fade) when it changes.
- Pager: depth/parallax page transform; glows and backgrounds move slower than content.
- Night mode and theme changes crossfade (night already does).
- **Done when:** the minute change and page swipes run at 60 fps with no dropped frames in a
  profiling run.

### 13. Haptics and sound · S
- On dock (power connected while StandBy opens): a short `VibrationEffect` click pattern and an
  optional soft chime via `SoundPool`.
- Silent in DND and at night; settings for each.
- **Done when:** docking gives one subtle tap + chime by day and nothing at night.

### 14. App icon and first-run walkthrough · M
- Adaptive launcher icon (foreground bolt + gradient background, monochrome layer for themed
  icons) replacing the placeholder drawable.
- First launch: 4–5 swipeable cards, each explaining one feature and asking for its permission at
  that moment (calendar, location, notifications, photos, display over apps, screen saver), with
  "Skip".
- **Done when:** a fresh install walks through setup in under a minute and lands on a working
  StandBy.

### 15b. Release readiness · M
- Respect 12/24-hour consistently: Minimal Day and Analog currently always use 24-hour times.
- Remove the debug-only demo intent from release builds (keep it in `debug`), and make
  `StandByActivity` non-exported again in release.
- R8 rules, signed release build, versioning.
- Play Store: justification for the `specialUse` foreground service, privacy policy covering
  location, calendar, photos and notifications, screenshots from demo mode.
- **Done when:** `./gradlew bundleRelease` produces a signed AAB that installs and passes a manual
  run-through of every page.

---

## Status
- **Phase 1** — done (29 Oct 2026). Verified on device in both the app and the screen saver.
- **Phase 2** — done (29 Oct 2026). Page reorder uses ▲/▼ buttons rather than drag handles, and
  setup has no live page thumbnails yet.
- **Phase 3** — built (29 Oct 2026). Verified on device: banners (content hidden when locked),
  StandBy's own quick timer, and Samsung Clock timer chips (label + exact countdown, cleared when
  it rings). Samsung's ringing notification has no actions, so Samsung's own popup handles
  Dismiss; StandBy's Snooze/Stop panel only appears for clocks that expose actions (e.g. Google
  Clock) — untested. Home Assistant untested (needs a server URL + token). No HA widget slot yet.
- **Phase 4** — local part done (29 Oct 2026). Photos-page clock takes its colours from each
  photo, adjusted to ≥ 4.5:1 contrast against the area behind the digits (verified on device).
  "On this day" / favourites ordering and captions are built but unverified: no photo on the test
  phone has a matching date or is favourited. "Match clock colours to photo" on the Colorful
  page is unverified. Google Photos Picker remains future scope.
- **Phase 5** — built (29 Oct 2026). Verified on device: walkthrough, rolling-digit clocks render
  at mockup spacing, debug + release (R8, 1.9 MB unsigned APK / AAB) build. Not yet verified: the
  roll animation at a minute change, the swipe depth effect, and the release build running (needs
  signing; see RELEASE.md). Dock tap + chime + auto-open verified on a real replug (all within
  300 ms of plugging in).

## Suggested order

| Phase | Items | Rough effort |
|---|---|---|
| 1 | 2, 3, 4, 15a | ~4 days |
| 2 | 5, 1, 6 | ~5 days |
| 3 | 9, 10, 11 | ~7 days |
| 4 | 7, 8 | ~4 days |
| 5 | 12, 13, 14, 15b | ~6 days |

## Decisions (28 Oct 2026)
- **Smart home backend (11):** Home Assistant.
- **Google Photos (7):** future scope — no Google Cloud project for now. Phase 4 ships the local
  "On this day" / favourites memories and palette matching (8); the Picker API part waits.
- **Notification privacy default (9):** hide message content while the phone is locked.
