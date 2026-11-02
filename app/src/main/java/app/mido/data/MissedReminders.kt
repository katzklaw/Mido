package app.mido.data

import java.time.ZoneId

/** Finds reminders that should have fired recently but didn't, so the app can say so. */
object MissedReminders {
    const val WINDOW_MS = 8 * 60 * 60_000L
    const val GRACE_MS = 2 * 60_000L

    fun find(tasks: List<TaskEntity>, nowMs: Long, zone: ZoneId = ZoneId.systemDefault()): List<TaskEntity> =
        tasks.filter { t ->
            val at = ReminderTime.triggerAt(t, zone) ?: return@filter false
            val overdueEnough = at < nowMs - GRACE_MS
            val recentEnough = at > nowMs - WINDOW_MS
            val notified = t.notifiedAt?.let { it >= at - GRACE_MS } == true
            // A task created after its own reminder time (e.g. "yesterday 3pm") was never going to fire.
            val existedAtTheTime = t.createdAt <= at
            overdueEnough && recentEnough && !notified && existedAtTheTime
        }
}
