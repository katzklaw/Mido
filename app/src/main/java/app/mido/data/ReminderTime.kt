package app.mido.data

import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId

/** Works out when (if ever) a task should ping you by time. Pure logic, no Android. */
object ReminderTime {
    /** All-day tasks ping at this hour on their day. */
    val ALL_DAY_TIME: LocalTime = LocalTime.of(9, 0)

    fun triggerAt(task: TaskEntity, zone: ZoneId = ZoneId.systemDefault()): Long? {
        if (task.completed) return null
        // A snoozed reminder rings at the snooze time, not at the original time.
        task.snoozedUntil?.let { return it }
        val due = task.dueAt ?: return null
        if (!task.allDay) return due
        return Instant.ofEpochMilli(due).atZone(zone).toLocalDate()
            .atTime(ALL_DAY_TIME).atZone(zone).toInstant().toEpochMilli()
    }
}
