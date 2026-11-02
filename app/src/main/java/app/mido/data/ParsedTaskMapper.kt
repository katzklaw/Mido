package app.mido.data

import app.mido.parser.ParsedTask
import java.time.ZoneId

/** Turns what the quick-add parser understood into a task ready to save. */
fun ParsedTask.toEntity(notes: String = "", zone: ZoneId = ZoneId.systemDefault()): TaskEntity = TaskEntity(
    title = title,
    notes = notes,
    dueAt = due?.atZone(zone)?.toInstant()?.toEpochMilli(),
    allDay = allDay,
    repeatRule = repeat?.encode(),
    locationName = locationName,
    locationTrigger = locationTrigger?.name,
    locationRepeat = locationRepeat && locationTrigger != null,
)
