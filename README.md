# Mido

*my attempt at recreating the long defunct Midu todo app*

## Features:
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
- Reminder suggestions: "Missed call from 111-222-3333, call them back?" and "Text from Sam: turn this into a task?" 
- Completion chime: a short happy ding when you check a task off (in the app or the widget); silent on silent/vibrate; can be turned off in Settings
- Widget: shows the next two weeks, a next-up line instead of a title ("Leave for X in 39 mins"), and 6 colour themes with adjustable background opacity (Settings)
- Pinned countdown: while a drive is coming up, a "Leave for X" notification with a live countdown stays in your shade 
- Next Up banner shows the next task or event, and the leave-by time when there is one
- A simple habit tracker with widget.

<img width="345" height="759" alt="Screenshot_272" src="https://github.com/user-attachments/assets/3f5a9213-dcf6-4671-9770-b1c4c897109f" /> <img width="344" height="756" alt="Screenshot_269" src="https://github.com/user-attachments/assets/350f6788-848d-4698-a102-f937a3fb07d9" /> <img width="342" height="763" alt="Screenshot_271" src="https://github.com/user-attachments/assets/c7efc8dd-bf4b-4b73-8259-6408b5502b93" />

<img width="339" height="755" alt="Screenshot_268" src="https://github.com/user-attachments/assets/669a63f8-b150-41a9-85db-96b8b262b2a9" />
Create reminders with natural language. Or jot something down fast on the go, and go back and edit the time and place details later. 



<img width="341" height="757" alt="Screenshot_277" src="https://github.com/user-attachments/assets/0da4227f-1684-4f04-a306-4dc323ee72fb" />
Make simple checklists. Pin a reminder or list directly to your notifications so you won't forget it!



Make reminders that repeat. Make reminders that trigger when you arrive or leave to a place. Get a notification when it's time to leave based on current traffic conditions!

