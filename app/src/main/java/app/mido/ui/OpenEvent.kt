package app.mido.ui

import android.content.ActivityNotFoundException
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.provider.CalendarContract
import android.widget.Toast
import app.mido.data.CalendarEvent

/** Opens [event] in the phone's calendar app (Google Calendar), on the right occurrence. */
fun openInCalendar(context: Context, event: CalendarEvent) {
    val uri = ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, event.id)
    val intent = Intent(Intent.ACTION_VIEW, uri)
        .putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, event.startMs)
        .putExtra(CalendarContract.EXTRA_EVENT_END_TIME, event.endMs)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    try {
        context.startActivity(intent)
    } catch (e: ActivityNotFoundException) {
        Toast.makeText(context, "No calendar app found to open this event", Toast.LENGTH_SHORT).show()
    } catch (e: SecurityException) {
        Toast.makeText(context, "Couldn't open this event", Toast.LENGTH_SHORT).show()
    }
}
