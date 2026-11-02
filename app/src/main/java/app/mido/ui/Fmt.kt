package app.mido.ui

import app.mido.data.CalendarEvent
import app.mido.data.LocationTrigger
import java.time.Instant
import java.time.ZoneId
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

object Fmt {
    private val timeFmt = DateTimeFormatter.ofPattern("h:mm a")
    private val dayFmt = DateTimeFormatter.ofPattern("EEE")
    private val fullFmt = DateTimeFormatter.ofPattern("EEE, MMM d")

    fun time(dt: LocalDateTime): String = dt.format(timeFmt)

    fun time(ms: Long, zone: ZoneId = ZoneId.systemDefault()): String =
        time(Instant.ofEpochMilli(ms).atZone(zone).toLocalDateTime())

    /** "10:00 AM – 11:00 AM", or "All day". */
    fun eventTime(e: CalendarEvent): String =
        if (e.allDay) "All day" else "${time(e.startMs)} – ${time(e.endMs)}"

    fun dueLabel(due: LocalDateTime, allDay: Boolean, today: LocalDate): String {
        val d = due.toLocalDate()
        val dayPart = when {
            d == today -> "Today"
            d == today.plusDays(1) -> "Tomorrow"
            d.isAfter(today) && d.isBefore(today.plusDays(7)) -> d.format(dayFmt)
            else -> d.format(fullFmt)
        }
        return if (allDay) dayPart else "$dayPart · ${time(due)}"
    }

    fun placeLabel(name: String, trigger: LocationTrigger?, everyTime: Boolean = false): String = when (trigger) {
        LocationTrigger.ARRIVE -> "$name · on ${if (everyTime) "every " else ""}arrival"
        LocationTrigger.LEAVE -> "$name · ${if (everyTime) "every time you leave" else "on leaving"}"
        null -> name
    }

    fun minutesLabel(minutes: Long): String = when {
        minutes < 60 -> "$minutes ${if (minutes == 1L) "min" else "mins"}"
        minutes % 60 == 0L -> "${minutes / 60} hr"
        else -> "${minutes / 60} hr ${minutes % 60} min"
    }
}
