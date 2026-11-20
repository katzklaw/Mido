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
        if (action == ACTION_REPOST || action == ACTION_NEXT_ITEM) {
            val pending = goAsync()
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val dao = app.mido.data.AppDatabase.get(context).taskDao()
                    var task = dao.getById(id)
                    if (task != null && action == ACTION_NEXT_ITEM) {
                        val i = app.mido.data.Checklist.firstOpenIndex(task.notes)
                        if (i != null) {
                            task = task.copy(notes = app.mido.data.Checklist.toggle(task.notes, i))
                            dao.update(task)
                            app.mido.widget.WidgetUpdater.refresh(context)
                        }
                    }
                    // Swiped away or edited: put it back (or remove it, if it's no longer pinned or is finished).
                    if (task != null) PinnedNotes.sync(context, task) else PinnedNotes.cancel(context, id)
                } finally {
                    pending.finish()
                }
            }
            return
        }
        Notifications.dismiss(context, id)
        NagScheduler.cancel(context, id)

        if (action == ACTION_SNOOZE || action == ACTION_SNOOZE_HOUR) {
            val minutes = if (action == ACTION_SNOOZE_HOUR) 60L else Notifications.SNOOZE_MINUTES
            val until = System.currentTimeMillis() + minutes * 60_000
            // Arm the alarm first, then remember the snooze in the database so re-arming (every 15 minutes,
            // on app open, after a reboot) keeps it instead of cancelling it.
            AlarmReminderScheduler(context).scheduleAt(id, until)
            val pending = goAsync()
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val dao = app.mido.data.AppDatabase.get(context).taskDao()
                    dao.getById(id)?.let { dao.update(it.copy(snoozedUntil = until)) }
                } finally {
                    pending.finish()
                }
            }
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
        const val ACTION_SNOOZE_HOUR = "app.mido.action.SNOOZE_HOUR"
        const val ACTION_REPOST = "app.mido.action.REPOST_PINNED"
        const val ACTION_NEXT_ITEM = "app.mido.action.NEXT_ITEM"
    }
}
