package app.mido.data

import kotlinx.coroutines.flow.Flow
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

class TaskRepository(
    private val dao: TaskDao,
    private val reminders: ReminderGateway = ReminderGateway.None,
    private val places: PlaceResolver = PlaceResolver.None,
    private val calendar: CalendarSink = CalendarSink.None,
    /** Called after any change to the task list (used to refresh the home screen widget). */
    private val onChanged: () -> Unit = {},
    private val zone: ZoneId = ZoneId.systemDefault(),
) {
    fun observeAll(): Flow<List<TaskEntity>> = dao.observeAll()

    suspend fun add(task: TaskEntity): Long {
        val id = dao.insert(task)
        val placed = withResolvedPlace(task.copy(id = id))
        reminders.schedule(placed)
        syncCalendar(placed)
        onChanged()
        return id
    }

    /** Saves an edited task and re-arms everything that depends on it (alarm, geofence, calendar). */
    suspend fun update(edited: TaskEntity, previous: TaskEntity) {
        var task = edited
        if (task.locationName != previous.locationName) {
            task = task.copy(latitude = null, longitude = null, locationAddress = null)
        }
        dao.update(task)
        task = withResolvedPlace(task)
        reminders.schedule(task)
        syncCalendar(task)
        onChanged()
    }

    /** Mirrors the task into the calendar and remembers the event id. */
    private suspend fun syncCalendar(task: TaskEntity): TaskEntity {
        val id = runCatching { calendar.sync(task) }.getOrDefault(task.calendarEventId)
        if (id == task.calendarEventId) return task
        val updated = task.copy(calendarEventId = id)
        dao.update(updated)
        return updated
    }

    /** Looks up coordinates for a task's place name (once) so a geofence can be armed. */
    private suspend fun withResolvedPlace(task: TaskEntity): TaskEntity {
        val name = task.locationName
        if (name == null || task.latitude != null) return task
        val found = runCatching { places.resolve(name) }.getOrNull() ?: return task
        val updated = task.copy(
            latitude = found.latitude,
            longitude = found.longitude,
            locationAddress = found.address,
        )
        dao.update(updated)
        return updated
    }

    suspend fun delete(task: TaskEntity) {
        dao.delete(task)
        reminders.cancel(task.id)
        task.calendarEventId?.let { runCatching { calendar.remove(it) } }
        onChanged()
    }

    /** Re-arm every open task's reminder (app start, reboot, time-zone change). */
    suspend fun rescheduleAll() {
        dao.getOpen().forEach {
            val placed = withResolvedPlace(it)
            reminders.schedule(placed)
            // Catch up tasks that never made it into the calendar (e.g. sync was just switched on).
            if (placed.calendarEventId == null && placed.dueAt != null) syncCalendar(placed)
        }
    }

    /**
     * Checking off a repeating task rolls it forward to its next occurrence instead of
     * completing it. Non-repeating tasks are simply toggled.
     */
    suspend fun toggleComplete(task: TaskEntity, now: LocalDateTime = LocalDateTime.now()) {
        try {
            toggleInner(task, now)
        } finally {
            onChanged()
        }
    }

    private suspend fun toggleInner(task: TaskEntity, now: LocalDateTime) {
        val rule = task.parsedRepeat
        val due = task.dueAt
        if (!task.completed && task.locationRepeat && task.parsedTrigger != null) {
            // "Every time" place reminder: checking it off clears this occurrence; the geofence stays armed.
            dao.update(task.copy(lastLocationDoneAt = System.currentTimeMillis()))
            return
        }
        if (!task.completed && rule != null && due != null) {
            var next = LocalDateTime.ofInstant(Instant.ofEpochMilli(due), zone)
            do {
                next = rule.nextAfter(next)
            } while (!next.isAfter(now) && !(task.allDay && next.toLocalDate() == now.toLocalDate()))
            val rolled = task.copy(dueAt = next.atZone(zone).toInstant().toEpochMilli())
            dao.update(rolled)
            reminders.schedule(rolled)
            syncCalendar(rolled)
        } else if (task.completed) {
            val reopened = task.copy(completed = false, completedAt = null)
            dao.update(reopened)
            reminders.schedule(reopened)
            syncCalendar(reopened)
        } else {
            val done = task.copy(
                completed = true,
                completedAt = now.atZone(zone).toInstant().toEpochMilli(),
            )
            dao.update(done)
            reminders.cancel(task.id)
            syncCalendar(done)
        }
    }
}
