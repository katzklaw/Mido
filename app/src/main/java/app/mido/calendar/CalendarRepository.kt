package app.mido.calendar

import android.Manifest
import android.content.Context
import android.content.ContentUris
import android.content.pm.PackageManager
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.CalendarContract
import androidx.core.content.ContextCompat
import app.mido.data.CalendarEvent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.ZoneId

data class CalendarInfo(val id: Long, val name: String, val account: String)

/**
 * Reads events from the phone's calendar provider. Google Calendar syncs into it, so this
 * needs no Google sign-in, just the READ_CALENDAR permission.
 */
class CalendarRepository(context: Context) {
    private val app = context.applicationContext

    fun hasPermission(): Boolean =
        ContextCompat.checkSelfPermission(app, Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED

    /** Events from the start of today through [days] days ahead. */
    suspend fun upcoming(days: Long = 8, zone: ZoneId = ZoneId.systemDefault()): List<CalendarEvent> {
        val from = LocalDate.now(zone).atStartOfDay(zone).toInstant().toEpochMilli()
        val to = LocalDate.now(zone).plusDays(days).atStartOfDay(zone).toInstant().toEpochMilli()
        return query(from, to, zone)
    }

    suspend fun query(fromMs: Long, toMs: Long, zone: ZoneId = ZoneId.systemDefault()): List<CalendarEvent> =
        withContext(Dispatchers.IO) {
            if (!hasPermission()) return@withContext emptyList()
            val uri = CalendarContract.Instances.CONTENT_URI.buildUpon()
                .also { ContentUris.appendId(it, fromMs); ContentUris.appendId(it, toMs) }
                .build()
            val projection = arrayOf(
                CalendarContract.Instances.EVENT_ID,
                CalendarContract.Instances.TITLE,
                CalendarContract.Instances.BEGIN,
                CalendarContract.Instances.END,
                CalendarContract.Instances.ALL_DAY,
                CalendarContract.Instances.EVENT_LOCATION,
                CalendarContract.Instances.CALENDAR_DISPLAY_NAME,
                CalendarContract.Instances.STATUS,
                CalendarContract.Instances.SELF_ATTENDEE_STATUS,
                CalendarContract.Instances.VISIBLE,
            )
            val out = mutableListOf<CalendarEvent>()
            try {
                app.contentResolver.query(uri, projection, null, null, "${CalendarContract.Instances.BEGIN} ASC")?.use { c ->
                    while (c.moveToNext()) {
                        val status = c.getInt(7)
                        val self = c.getInt(8)
                        val visible = c.getInt(9)
                        if (status == CalendarContract.Events.STATUS_CANCELED) continue
                        if (self == CalendarContract.Attendees.ATTENDEE_STATUS_DECLINED) continue
                        if (visible == 0) continue
                        out += CalendarEvent.normalize(
                            id = c.getLong(0),
                            title = c.getString(1)?.takeIf { it.isNotBlank() } ?: "(No title)",
                            begin = c.getLong(2),
                            end = c.getLong(3),
                            allDay = c.getInt(4) == 1,
                            location = c.getString(5)?.trim()?.takeIf { it.isNotEmpty() },
                            calendarName = c.getString(6),
                            zone = zone,
                        )
                    }
                }
            } catch (e: SecurityException) {
                // Permission revoked mid-query.
            }
            out
        }

    /** Calendars Mido is allowed to add events to (Google accounts show up here once synced). */
    suspend fun writableCalendars(): List<CalendarInfo> = withContext(Dispatchers.IO) {
        if (!hasPermission()) return@withContext emptyList()
        val out = mutableListOf<CalendarInfo>()
        try {
            app.contentResolver.query(
                CalendarContract.Calendars.CONTENT_URI,
                arrayOf(
                    CalendarContract.Calendars._ID,
                    CalendarContract.Calendars.CALENDAR_DISPLAY_NAME,
                    CalendarContract.Calendars.ACCOUNT_NAME,
                    CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL,
                ),
                null, null, "${CalendarContract.Calendars.ACCOUNT_NAME} ASC",
            )?.use { c ->
                while (c.moveToNext()) {
                    if (c.getInt(3) < CalendarContract.Calendars.CAL_ACCESS_CONTRIBUTOR) continue
                    out += CalendarInfo(c.getLong(0), c.getString(1).orEmpty(), c.getString(2).orEmpty())
                }
            }
        } catch (e: SecurityException) {
        }
        out
    }

    /** Emits the upcoming events whenever [trigger] fires and whenever the calendar changes. */
    fun observe(trigger: Flow<Unit>): Flow<List<CalendarEvent>> = callbackFlow {
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                // Re-query off the main thread.
                this@callbackFlow.launch(Dispatchers.IO) { trySend(upcoming()) }
            }
        }
        if (hasPermission()) app.contentResolver.registerContentObserver(CalendarContract.CONTENT_URI, true, observer)
        try {
            trigger.collect { trySend(upcoming()) }
            awaitClose()
        } finally {
            app.contentResolver.unregisterContentObserver(observer)
        }
    }.flowOn(Dispatchers.IO)
}
