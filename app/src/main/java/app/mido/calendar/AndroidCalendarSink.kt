package app.mido.calendar

import app.mido.pro.Entitlements
import android.Manifest
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.provider.CalendarContract
import androidx.core.content.ContextCompat
import app.mido.data.CalendarEventSpec
import app.mido.data.CalendarSink
import app.mido.data.TaskEntity
import app.mido.settings.AppSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Writes Mido tasks that have a date into the chosen calendar; Google Calendar syncs them up. */
class AndroidCalendarSink(context: Context, private val settings: AppSettings) : CalendarSink {
    private val app = context.applicationContext
    private val resolver get() = app.contentResolver

    private fun canWrite() =
        ContextCompat.checkSelfPermission(app, Manifest.permission.WRITE_CALENDAR) == PackageManager.PERMISSION_GRANTED

    override suspend fun sync(task: TaskEntity): Long? = withContext(Dispatchers.IO) {
        if (!settings.syncEnabled || !canWrite() || !Entitlements.isPro(app)) return@withContext task.calendarEventId

        val spec = if (task.completed) null else CalendarEventSpec.from(task)
        val existing = task.calendarEventId
        if (spec == null) {
            existing?.let { delete(it) }
            return@withContext null
        }

        val values = ContentValues().apply {
            put(CalendarContract.Events.CALENDAR_ID, settings.syncCalendarId)
            put(CalendarContract.Events.TITLE, spec.title)
            put(CalendarContract.Events.DESCRIPTION, spec.description)
            put(CalendarContract.Events.EVENT_LOCATION, spec.location)
            put(CalendarContract.Events.DTSTART, spec.startMs)
            put(CalendarContract.Events.EVENT_TIMEZONE, spec.timeZoneId)
            put(CalendarContract.Events.ALL_DAY, if (spec.allDay) 1 else 0)
            put(CalendarContract.Events.RRULE, spec.rrule)
            // Repeating events must use DURATION; one-offs use DTEND. Null the other so edits don't leave stale values.
            put(CalendarContract.Events.DTEND, spec.endMs)
            put(CalendarContract.Events.DURATION, spec.duration)
        }

        try {
            if (existing != null) {
                val uri = ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, existing)
                if (resolver.update(uri, values, null, null) > 0) return@withContext existing
                // The event was deleted in the calendar app; fall through and recreate it.
            }
            resolver.insert(CalendarContract.Events.CONTENT_URI, values)?.let { ContentUris.parseId(it) }
        } catch (e: Exception) {
            existing
        }
    }

    override suspend fun remove(eventId: Long) {
        withContext(Dispatchers.IO) { if (canWrite()) delete(eventId) }
    }

    private fun delete(eventId: Long) {
        runCatching {
            resolver.delete(ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, eventId), null, null)
        }
    }
}
