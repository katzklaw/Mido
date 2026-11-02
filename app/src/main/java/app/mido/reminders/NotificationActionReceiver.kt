package app.mido.reminders

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** Handles the Done and Snooze buttons on a reminder notification. */
class NotificationActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getLongExtra(ReminderReceiver.EXTRA_TASK_ID, -1L)
        if (id < 0) return
        val action = intent.action
        Notifications.dismiss(context, id)
        NagScheduler.cancel(context, id)

        if (action == ACTION_SNOOZE) {
            AlarmReminderScheduler(context).scheduleAt(id, System.currentTimeMillis() + Notifications.SNOOZE_MINUTES * 60_000)
            return
        }
        if (action == ACTION_DONE) {
            val pending = goAsync()
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val dao = app.mido.data.AppDatabase.get(context).taskDao()
                    val task = dao.getById(id)
                    if (task != null && !task.completed) {
                        Reminders.repository(context).toggleComplete(task)
                    }
                } finally {
                    pending.finish()
                }
            }
        }
    }

    companion object {
        const val ACTION_DONE = "app.mido.action.DONE"
        const val ACTION_SNOOZE = "app.mido.action.SNOOZE"
    }
}
