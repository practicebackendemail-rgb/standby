# StandBy — Privacy Policy (draft)

_Last updated: 29 October 2026. Draft for review before publishing; replace the contact line._

StandBy turns your phone into a clock and information display while it charges. It has no
account, no analytics, no ads and no server of its own. Everything it reads stays on your phone,
except the two network requests described below.

## What StandBy accesses, and why

| Permission | Used for | Leaves the phone? |
|---|---|---|
| Calendar (read) | Showing today's events and "next up". | No |
| Approximate location | Getting local weather. | Rounded coordinates (3 decimals, ~100 m) are sent to Open-Meteo to fetch the forecast. |
| Photos (read) | The photo slideshow and "On this day" memories. | No |
| Physical activity | Today's step count from the phone's step sensor. | No |
| Notification access | What's playing (to show and control music), message and call banners, and Clock-app timers / alarms. | No |
| Display over other apps | Opening StandBy automatically when charging starts. | No |
| Post notifications | The silent "StandBy is ready" notification Android requires for the background service. | No |
| Internet | Weather (Open-Meteo) and, if you set it up, your own Home Assistant server. | See below |

Notification content is held in memory only to draw a banner for a few seconds; it is never
stored. While the phone is locked, banners show only the app name unless you turn on "Show message
text when locked".

## Network requests

- **Weather:** `api.open-meteo.com` receives approximate coordinates. See Open-Meteo's own privacy
  policy. No identifier is sent.
- **Home Assistant (optional):** if you enter a Home Assistant URL and token, StandBy talks only to
  that server. The token is stored in StandBy's private app storage on this phone.

## Data stored on the phone

Settings, your checklist, countdown, widget choices, and the Home Assistant URL / token are kept in
StandBy's private app storage. Uninstalling StandBy deletes them.

## Contact

Questions: [your contact email here]
