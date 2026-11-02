package app.mido.data

import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset

/** Everything needed to write a task into the calendar as an event. Pure, so it can be tested. */
data class CalendarEventSpec(
    val title: String,
    val description: String?,
    val location: String?,
    val startMs: Long,
    /** Null for repeating events, which use [duration] instead. */
    val endMs: Long?,
    val duration: String?,
    val rrule: String?,
    val allDay: Boolean,
    val timeZoneId: String,
) {
    companion object {
        const val DEFAULT_MINUTES = 30L

        /** Null when the task has no date, so there is nothing to put on a calendar. */
        fun from(task: TaskEntity, zone: ZoneId = ZoneId.systemDefault()): CalendarEventSpec? {
            val due = task.dueAt ?: return null
            val rrule = task.parsedRepeat?.toRrule()
            val location = task.locationAddress ?: task.locationName
            val description = task.notes.trim().ifEmpty { null }

            return if (task.allDay) {
                // All-day events live on UTC midnights in the calendar provider.
                val day = Instant.ofEpochMilli(due).atZone(zone).toLocalDate()
                val start = day.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
                CalendarEventSpec(
                    task.title, description, location, start,
                    endMs = if (rrule == null) start + 86_400_000L else null,
                    duration = if (rrule != null) "P1D" else null,
                    rrule = rrule, allDay = true, timeZoneId = "UTC",
                )
            } else {
                CalendarEventSpec(
                    task.title, description, location, due,
                    endMs = if (rrule == null) due + DEFAULT_MINUTES * 60_000L else null,
                    duration = if (rrule != null) "PT${DEFAULT_MINUTES}M" else null,
                    rrule = rrule, allDay = false, timeZoneId = zone.id,
                )
            }
        }
    }
}

/** iCalendar RRULE, e.g. "FREQ=WEEKLY;INTERVAL=2;BYDAY=TU,TH". */
fun RepeatRule.toRrule(): String {
    val parts = mutableListOf("FREQ=${freq.name}")
    if (interval > 1) parts += "INTERVAL=$interval"
    if (freq == RepeatRule.Freq.WEEKLY && days.isNotEmpty()) {
        parts += "BYDAY=" + days.sortedBy { it.value }.joinToString(",") { it.name.take(2) }
    }
    return parts.joinToString(";")
}
