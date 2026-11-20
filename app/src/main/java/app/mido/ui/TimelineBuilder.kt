package app.mido.ui

import app.mido.calendar.DriveLeg
import app.mido.data.CalendarEvent
import app.mido.data.HabitDay
import app.mido.data.TaskEntity
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** One row in the timeline. */
sealed interface TimelineEntry {
    val key: String

    data class Header(val label: String, val overdue: Boolean = false) : TimelineEntry {
        override val key: String get() = "h-$label"
    }

    data class Item(val task: TaskEntity, val overdue: Boolean = false) : TimelineEntry {
        override val key: String get() = "t-${task.id}"
    }

    /** A calendar event. [dayKey] keeps multi-day all-day events unique per day. */
    data class Event(val event: CalendarEvent, val dayKey: String) : TimelineEntry {
        override val key: String get() = "e-${event.key}-$dayKey"
    }

    /** "Leave by 4:25 PM" marker with the drive time. */
    data class Drive(val leg: DriveLeg) : TimelineEntry {
        override val key: String get() = "d-${leg.eventKey}"
    }

    /** A habit due on a day, with a checkbox for today. */
    data class Habit(val day: HabitDay) : TimelineEntry {
        override val key: String get() = "hb-${day.habit.id}-${day.date}"
    }

    /** The "you are here" marker inside today's section. */
    data class Now(val at: LocalDateTime) : TimelineEntry {
        override val key: String get() = "now"
    }
}

/** What the banner at the top talks about. */
data class NextUp(
    val title: String,
    val startMs: Long,
    val location: String?,
    val minutesAway: Long,
    val leave: DriveLeg? = null,
)

object TimelineBuilder {
    private val headerFmt = DateTimeFormatter.ofPattern("EEEE, MMM d")

    fun dueOf(task: TaskEntity, zone: ZoneId = ZoneId.systemDefault()): LocalDateTime? =
        task.dueAt?.let { LocalDateTime.ofInstant(Instant.ofEpochMilli(it), zone) }

    private fun completedDate(task: TaskEntity, zone: ZoneId): LocalDate? =
        task.completedAt?.let { LocalDateTime.ofInstant(Instant.ofEpochMilli(it), zone).toLocalDate() }

    private fun dateOf(ms: Long, zone: ZoneId): LocalDate = Instant.ofEpochMilli(ms).atZone(zone).toLocalDate()

    /** A row waiting to be placed in a day. group 0 = all-day (top), 1 = timed. */
    private class Row(val group: Int, val at: Long, val order: Int, val entry: TimelineEntry)

    fun build(
        tasks: List<TaskEntity>,
        now: LocalDateTime,
        zone: ZoneId = ZoneId.systemDefault(),
        events: List<CalendarEvent> = emptyList(),
        drives: List<DriveLeg> = emptyList(),
        habitDays: List<HabitDay> = emptyList(),
    ): List<TimelineEntry> {
        val today = now.toLocalDate()
        val nowMs = now.atZone(zone).toInstant().toEpochMilli()

        // Completed tasks only stay visible for the day they were completed.
        val visible = tasks.filter { !it.completed || completedDate(it, zone) == today }

        val overdue = visible
            .filter { !it.completed && dueOf(it, zone)?.toLocalDate()?.isBefore(today) == true }
            .sortedBy { it.dueAt }
        val overdueIds = overdue.map { it.id }.toSet()

        val dated = visible.filter { it.id !in overdueIds && dueOf(it, zone) != null }
        // Pinned undated tasks live at the top of Today, every day, until they're done.
        val pinned = visible.filter { it.pinned && !it.completed && dueOf(it, zone) == null }.sortedBy { it.createdAt }
        val pinnedIds = pinned.map { it.id }.toSet()
        val anytime = visible.filter { dueOf(it, zone) == null && it.id !in pinnedIds }

        val rowsByDate = HashMap<LocalDate, MutableList<Row>>()
        fun add(date: LocalDate, row: Row) { rowsByDate.getOrPut(date) { mutableListOf() }.add(row) }

        for (t in dated) {
            val date = dueOf(t, zone)!!.toLocalDate()
            add(date, Row(if (t.allDay) 0 else 1, t.dueAt ?: 0L, 1, TimelineEntry.Item(t)))
        }

        for (e in events) {
            val first = maxOf(e.startDate(zone), today)
            val last = e.endDate(zone)
            if (last.isBefore(today)) continue
            var d = first
            while (!d.isAfter(last)) {
                // A multi-day all-day event is listed once per day; timed events on their start day.
                add(d, Row(if (e.allDay) 0 else 1, e.startMs, 0, TimelineEntry.Event(e, d.toString())))
                d = d.plusDays(1)
            }
        }

        for (leg in drives) {
            if (leg.leaveByMs + 60 * 60_000L < nowMs) continue // long gone
            add(dateOf(leg.leaveByMs, zone), Row(1, leg.leaveByMs, 2, TimelineEntry.Drive(leg)))
        }

        val habitsByDate = habitDays.groupBy { it.date }
        val dates = (rowsByDate.keys + habitsByDate.keys + today).sortedBy { it.toEpochDay() }
        val out = mutableListOf<TimelineEntry>()

        if (overdue.isNotEmpty()) {
            out += TimelineEntry.Header("Overdue", overdue = true)
            overdue.forEach { out += TimelineEntry.Item(it, overdue = true) }
        }

        for (date in dates) {
            out += TimelineEntry.Header(dateLabel(date, today))
            if (date == today) pinned.forEach { out += TimelineEntry.Item(it) }
            habitsByDate[date]?.forEach { out += TimelineEntry.Habit(it) }
            val rows = (rowsByDate[date] ?: mutableListOf())
                .sortedWith(compareBy<Row>({ it.group }, { it.at }, { it.order }))

            if (date == today) {
                var nowInserted = false
                for (row in rows) {
                    if (!nowInserted && row.group == 1 && row.at > nowMs) {
                        out += TimelineEntry.Now(now)
                        nowInserted = true
                    }
                    out += row.entry
                }
                if (!nowInserted) out += TimelineEntry.Now(now)
            } else {
                rows.forEach { out += it.entry }
            }
        }

        if (anytime.isNotEmpty()) {
            out += TimelineEntry.Header("Anytime")
            anytime
                .sortedWith(compareBy<TaskEntity>({ it.completed }, { it.createdAt }))
                .forEach { out += TimelineEntry.Item(it) }
        }

        return out
    }

    /** The next timed thing later today (task or calendar event), plus its leave-by if known. */
    fun nextUp(
        tasks: List<TaskEntity>,
        now: LocalDateTime,
        zone: ZoneId = ZoneId.systemDefault(),
        events: List<CalendarEvent> = emptyList(),
        drives: List<DriveLeg> = emptyList(),
    ): NextUp? {
        val nowMs = now.atZone(zone).toInstant().toEpochMilli()
        val today = now.toLocalDate()
        val candidates = mutableListOf<NextUp>()

        for (t in tasks) {
            if (t.completed || t.allDay) continue
            val due = dueOf(t, zone) ?: continue
            if (due.toLocalDate() != today || due.isBefore(now)) continue
            // Tasks mirrored into the calendar get drive legs via their event; match on title + time.
            val leg = drives.firstOrNull { it.startMs == t.dueAt && it.title == t.title }
            candidates += NextUp(
                t.title, t.dueAt!!, t.locationName,
                Duration.between(now, due).toMinutes(), leg,
            )
        }
        for (e in events) {
            if (e.allDay || e.startMs < nowMs || e.startDate(zone) != today) continue
            val leg = drives.firstOrNull { it.eventKey == e.key }
            candidates += NextUp(e.title, e.startMs, e.location, (e.startMs - nowMs) / 60_000L, leg)
        }
        return candidates.minByOrNull { it.startMs }
    }

    private fun dateLabel(date: LocalDate, today: LocalDate): String = when (date) {
        today -> "Today"
        today.plusDays(1) -> "Tomorrow"
        else -> date.format(headerFmt)
    }
}
