package app.mido.data

import java.time.LocalDate
import java.time.ZoneId

object Occurrences {
    private const val MAX_PROJECTIONS = 500

    /** Days within [from]..[to] on which [task] falls: its due date, plus projected repeats if it repeats. */
    fun dates(task: TaskEntity, from: LocalDate, to: LocalDate, zone: ZoneId): List<LocalDate> {
        val due = task.dueAt ?: return emptyList()
        var current = java.time.LocalDateTime.ofInstant(java.time.Instant.ofEpochMilli(due), zone)
        val out = mutableListOf<LocalDate>()
        val base = current.toLocalDate()
        if (!base.isBefore(from) && !base.isAfter(to)) out += base

        val rule = task.parsedRepeat
        if (rule != null && !task.completed) {
            var n = 0
            while (n++ < MAX_PROJECTIONS) {
                current = rule.nextAfter(current)
                val d = current.toLocalDate()
                if (d.isAfter(to)) break
                if (!d.isBefore(from)) out += d
            }
        }
        return out
    }
}

object DayAgenda {
    private fun minutesOfDay(ms: Long, zone: ZoneId): Int =
        java.time.Instant.ofEpochMilli(ms).atZone(zone).let { it.hour * 60 + it.minute }

    /** Everything on [date]: all-day items first, then timed items in order. */
    fun forDay(date: LocalDate, tasks: List<TaskEntity>, events: List<CalendarEvent>, zone: ZoneId): List<AgendaItem> {
        class Row(val group: Int, val minutes: Int, val order: Int, val item: AgendaItem)
        val rows = mutableListOf<Row>()

        for (t in tasks) {
            if (Occurrences.dates(t, date, date, zone).isEmpty()) continue
            val base = TimelineHelpers.dateOf(t.dueAt!!, zone)
            rows += Row(if (t.allDay) 0 else 1, minutesOfDay(t.dueAt, zone), 1, AgendaItem.Task(t, projected = base != date))
        }
        for (e in events) {
            if (date.isBefore(e.startDate(zone)) || date.isAfter(e.endDate(zone))) continue
            val startsToday = e.startDate(zone) == date
            rows += Row(
                if (e.allDay || !startsToday) 0 else 1,
                if (startsToday) minutesOfDay(e.startMs, zone) else 0,
                0,
                AgendaItem.Event(e),
            )
        }
        return rows.sortedWith(compareBy({ it.group }, { it.minutes }, { it.order })).map { it.item }
    }

    /** How many tasks and events fall on each day in [from]..[to], for the dots in the month grid. */
    fun counts(
        from: LocalDate, to: LocalDate,
        tasks: List<TaskEntity>, events: List<CalendarEvent>, zone: ZoneId,
    ): Map<LocalDate, DayCounts> {
        val taskCount = HashMap<LocalDate, Int>()
        val eventCount = HashMap<LocalDate, Int>()
        for (t in tasks) Occurrences.dates(t, from, to, zone).forEach { taskCount.merge(it, 1, Int::plus) }
        for (e in events) {
            var d = maxOf(e.startDate(zone), from)
            val last = minOf(e.endDate(zone), to)
            while (!d.isAfter(last)) {
                eventCount.merge(d, 1, Int::plus)
                d = d.plusDays(1)
            }
        }
        return (taskCount.keys + eventCount.keys).associateWith { DayCounts(taskCount[it] ?: 0, eventCount[it] ?: 0) }
    }
}

internal object TimelineHelpers {
    fun dateOf(ms: Long, zone: ZoneId): LocalDate = java.time.Instant.ofEpochMilli(ms).atZone(zone).toLocalDate()
}
