package app.mido.data

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset

/**
 * A calendar entry read from the phone's calendar (which Google Calendar syncs into).
 * For all-day events [startMs]/[endMs] are local midnights, so they read like any other day.
 */
data class CalendarEvent(
    val id: Long,
    val title: String,
    val startMs: Long,
    val endMs: Long,
    val allDay: Boolean,
    val location: String? = null,
    val calendarName: String? = null,
) {
    /** Unique per occurrence, so repeating events don't collide. */
    val key: String get() = "$id@$startMs"

    fun startDate(zone: ZoneId): LocalDate = Instant.ofEpochMilli(startMs).atZone(zone).toLocalDate()

    /** Last day the event touches (all-day ends are exclusive midnights, so step back 1 ms). */
    fun endDate(zone: ZoneId): LocalDate =
        if (allDay) Instant.ofEpochMilli(maxOf(startMs, endMs - 1)).atZone(zone).toLocalDate() else startDate(zone)

    companion object {
        /**
         * Android stores all-day events as UTC midnights. Re-anchor them to local midnights so
         * "Oct 28 all day" shows on Oct 28 whatever the time zone.
         */
        fun normalize(
            id: Long, title: String, begin: Long, end: Long, allDay: Boolean,
            location: String?, calendarName: String?, zone: ZoneId,
        ): CalendarEvent {
            if (!allDay) return CalendarEvent(id, title, begin, end, false, location, calendarName)
            fun local(utcMs: Long) =
                Instant.ofEpochMilli(utcMs).atZone(ZoneOffset.UTC).toLocalDate().atStartOfDay(zone).toInstant().toEpochMilli()
            return CalendarEvent(id, title, local(begin), local(end), true, location, calendarName)
        }
    }
}
