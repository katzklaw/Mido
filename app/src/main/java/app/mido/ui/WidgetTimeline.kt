package app.mido.ui

import app.mido.calendar.DriveLeg
import app.mido.data.CalendarEvent
import app.mido.data.TaskEntity
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

/** The timeline the home screen widget shows: overdue, today, and the next [days] days. */
object WidgetTimeline {
    const val DEFAULT_DAYS = 14

    fun build(
        tasks: List<TaskEntity>,
        events: List<CalendarEvent>,
        drives: List<DriveLeg>,
        now: LocalDateTime,
        zone: ZoneId = ZoneId.systemDefault(),
        days: Int = DEFAULT_DAYS,
    ): List<TimelineEntry> {
        val last = now.toLocalDate().plusDays(days.toLong())
        fun dateOf(ms: Long) = Instant.ofEpochMilli(ms).atZone(zone).toLocalDate()

        // Undated ("Anytime") tasks have no place on a calendar-style overview, so they stay in the app.
        val inRange = tasks.filter { t -> t.dueAt?.let { !dateOf(it).isAfter(last) } == true }
        val eventsInRange = events.filter { !it.startDate(zone).isAfter(last) }
        val drivesInRange = drives.filter { !dateOf(it.leaveByMs).isAfter(last) }
        return TimelineBuilder.build(inRange, now, zone, eventsInRange, drivesInRange)
    }
}
