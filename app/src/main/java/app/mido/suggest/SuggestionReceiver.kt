package app.mido.suggest

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.widget.Toast
import app.mido.data.toEntity
import app.mido.parser.ParsedTask
import app.mido.parser.QuickAddParser
import app.mido.reminders.Reminders
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.LocalDateTime

/** The "Add task" / "Remind me in 1h" buttons on suggestion notifications. */
class SuggestionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val title = intent.getStringExtra(EXTRA_TITLE)?.takeIf { it.isNotBlank() } ?: return
        val notes = intent.getStringExtra(EXTRA_NOTES).orEmpty()
        val now = LocalDateTime.now()

        val parsed: ParsedTask = when (intent.action) {
            // The task text may contain a date ("call the vet tomorrow"), so run it through the quick-add parser.
            ACTION_ADD_TASK -> QuickAddParser.parse(title, now)
            ACTION_CALLBACK_TASK -> ParsedTask(
                title = title, due = now.plusHours(1).withSecond(0).withNano(0),
                allDay = false, repeat = null, locationName = null, locationTrigger = null,
            )
            else -> return
        }

        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                Reminders.repository(context).add(parsed.toEntity(notes))
            } finally {
                pending.finish()
            }
        }
        Toast.makeText(context, "Added: ${parsed.title}", Toast.LENGTH_SHORT).show()
    }

    companion object {
        const val ACTION_ADD_TASK = "app.mido.action.SUGGEST_ADD_TASK"
        const val ACTION_CALLBACK_TASK = "app.mido.action.SUGGEST_CALLBACK_TASK"
        const val EXTRA_TITLE = "title"
        const val EXTRA_NOTES = "notes"

        fun pending(context: Context, action: String, requestCode: Int, vararg extras: Pair<String, String>): PendingIntent {
            val intent = Intent(context, SuggestionReceiver::class.java).setAction(action)
            extras.forEach { (k, v) -> intent.putExtra(k, v) }
            return PendingIntent.getBroadcast(context, requestCode, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        }
    }
}
