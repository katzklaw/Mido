package app.mido.data

import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/** Turns the edit form's fields back into a task. */
object TaskEdit {
    fun apply(
        task: TaskEntity,
        title: String,
        notes: String,
        date: LocalDate?,
        time: LocalTime?,
        repeat: RepeatRule?,
        place: String,
        trigger: LocationTrigger?,
        zone: ZoneId = ZoneId.systemDefault(),
        today: LocalDate = LocalDate.now(zone),
        everyTime: Boolean = false,
    ): TaskEntity {
        // A repeating task needs a date to repeat from.
        val day = date ?: if (repeat != null) today else null
        val dueAt = day?.let { d ->
            (time?.let { d.atTime(it) } ?: d.atStartOfDay()).atZone(zone).toInstant().toEpochMilli()
        }
        val placeName = place.trim().ifEmpty { null }
        return task.copy(
            title = title.trim(),
            notes = notes.trim(),
            dueAt = dueAt,
            allDay = day != null && time == null,
            repeatRule = if (dueAt != null) repeat?.encode() else null,
            locationName = placeName,
            locationTrigger = if (placeName != null) trigger?.name else null,
            locationRepeat = placeName != null && trigger != null && everyTime,
        )
    }
}
