# Mido

Open this folder in Android Studio (Ladybug or newer, JDK 17) and let Gradle sync. Run on a device or emulator (Android 8.0+).

## Setup for place lookup
Add your Google Maps key to `local.properties` (Android Studio creates this file; it is not committed):

    MAPS_API_KEY=your_key_here

The key needs the **Places API (New)** and the **Routes API** enabled (Routes powers the live drive times). Without a key, Mido falls back to Android's built-in geocoder, which is less accurate for business names.

## What works now
- Timeline home screen: overdue, Today (with a "now" marker), future days, then Anytime
- "Next up" banner at the top; List view as the secondary tab
- Quick add with natural language ("take out trash every tue at 7pm", "pick up gym bag at Planet Fitness when I leave")
- Repeating tasks roll forward when checked off; location is stored as info or an arrive/leave trigger
- Reminders: a notification fires at the due time (all-day tasks at 9:00) with Done and Snooze 10m buttons; alarms survive reboots and app updates
- Location triggers: a place in a task is looked up automatically and a 200 m geofence is armed for "on arrival" / "when I leave"; the notification says "You arrived at X" / "You left X"
- Google Calendar: events from your phone's calendar (Google Calendar syncs into it) show on the timeline; declined and cancelled events are hidden; all-day and multi-day events sit at the top of their day
- Leave-by: for your next few events with a physical address, Mido asks Google for live-traffic drive time from where you are, adds a "🚗 Leave by 4:25 PM" marker to the timeline, and sets a "time to leave" notification. It re-checks traffic about every 15 minutes in the background. Video-call links are ignored.
- Task editing: tap a task, then Edit, to change title, notes, date/time, repeat, and place or trigger
- Calendar write-back (Settings, gear icon): timed and all-day tasks can be added to a Google Calendar of your choice as events (repeats become repeating events; done or deleted tasks remove the event). One-way: edits made in the calendar app don't come back. Events Mido created aren't shown twice on the timeline.
- Settings: leave-by buffer (0 to 30 min) and the calendar to write to
- Month calendar: a third view next to Timeline and List. Month grid with dots, tap a day for its agenda; repeating tasks appear on their future dates; + adds to the selected day
- Home screen widget: the same timeline as the app (line, nodes, now marker) in a smaller font, scrollable, check tasks off right in it; tapping the list opens the app, only the + opens the small floating add box (long-press the home screen, Widgets, Mido)
- Reliable reminders: exact alarms that fire with the app closed, re-armed on boot, update and every ~15 min; a reliability check in Settings (battery, exact alarms, Samsung steps) with a test-reminder button; a warning strip while something could delay reminders; and a "Missed reminder" notification if one was swallowed
- Suggestions (Settings): "Missed call from 111-222-3333, call them back?" and "Text from Sam: turn this into a task?" using notification access; processed on the phone only
- Completion chime: a short happy ding when you check a task off (in the app or the widget); silent on silent/vibrate; can be turned off in Settings
- Widget: shows the next two weeks, a next-up line instead of a title ("Leave for X in 39 mins"), and 6 colour themes with adjustable background opacity (Settings)
- Pinned countdown: while a drive is coming up, a "Leave for X" notification with a live countdown stays in your shade (foreground service); toggle in Settings
- Next Up banner shows the next task or event, and the leave-by time when there is one
- Local Room database; no account needed

## Not built yet
Two-way calendar sync, subtasks/lists, search, per-contact rules for text suggestions.

## Tests
Parser, timeline, leave-by planner, calendar-event and edit unit tests live in app/src/test (`./gradlew test`).

## Where things are
- Settings (gear icon): leave-by buffer, calendar write-back, reliability check, suggestions.
- If the build complains about a missing Gradle wrapper, let Android Studio regenerate it.

## Persistent alerts (phase 8)
Settings → "Keep alerting until I respond" (on by default). A reminder rings on an alarm-tone channel and re-alerts every
1/2/5/10 minutes until you tap Done or Snooze (or complete/delete the task elsewhere). It gives up after about an hour.
Logic: `NagPolicy` (pure), `NagScheduler` (chained exact alarms), `NagReceiver`, `ReminderAlert`.
Do Not Disturb can still silence it unless you allow alarms/Mido through.

## Mido Pro (phase 9b)
Free: quick add, repeats, timeline/list/calendar, reminders, persistent alerts, read-only calendar, 14-day widget in Black or Light,
missed-call suggestions, 5 text-to-task suggestions. Pro: live-traffic leave-by + pinned countdown, arrive/leave place reminders,
add-to-Google-Calendar, widget colours and opacity, unlimited text-to-task.

Everything asks `Entitlements.isPro(context)` (app/.../pro). It is true when `PRO_UNLOCKED=true` is in local.properties
(your own build) or a valid licence key was entered in Settings → Mido Pro. Play Billing can later become a second source there.

Making keys (needs only a JDK):
1. `java tools/KeyTool.java init` (one time). Keep `mido-private.key` secret and backed up. Add the printed public key to `local.properties` as `PRO_PUBLIC_KEY=...` (one line; it survives unzipping new versions), then rebuild.
2. `java tools/KeyTool.java make "Customer Name"` prints a key to send them. `check <key>` verifies one.
