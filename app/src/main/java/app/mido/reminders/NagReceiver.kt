package app.mido.reminders

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import app.mido.data.AppDatabase
import app.mido.settings.AppSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** Fires a repeat alert for a reminder nobody has answered yet. */
class NagReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getLongExtra(ReminderReceiver.EXTRA_TASK_ID, -1L)
        if (id < 0) return
        val sent = intent.getIntExtra(NagScheduler.EXTRA_SENT, 0)
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val task = AppDatabase.get(context).taskDao().getById(id)
                val settings = AppSettings(context)
                if (task == null || task.completed || !settings.persistentAlerts) return@launch
                // Snoozed while this repeat was already on its way: stay quiet until the snooze ends.
                val snoozed = task.snoozedUntil
                if (snoozed != null && snoozed > System.currentTimeMillis()) return@launch
                // An "every time" place reminder that was checked off since it fired is answered.
                val doneAt = task.lastLocationDoneAt
                if (task.locationRepeat && doneAt != null && doneAt >= (task.lastLocationFiredAt ?: 0L)) {
                    Notifications.dismiss(context, id)
                    return@launch
                }
                val last = NagPolicy.isLast(sent, settings.nagIntervalMin)
                Notifications.showReminder(context, task, sticky = !last)
                NagScheduler.scheduleNext(context, id, sent + 1)
            } finally {
                pending.finish()
            }
        }
    }
}
