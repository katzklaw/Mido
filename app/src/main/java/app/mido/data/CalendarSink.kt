package app.mido.data

/** Where tasks get written into Google Calendar (via the phone's calendar provider). */
interface CalendarSink {
    /**
     * Creates, updates or removes the calendar event for [task] as needed and returns the
     * event id to remember (null when the task has no event).
     */
    suspend fun sync(task: TaskEntity): Long?

    suspend fun remove(eventId: Long)

    object None : CalendarSink {
        override suspend fun sync(task: TaskEntity): Long? = task.calendarEventId
        override suspend fun remove(eventId: Long) = Unit
    }
}
