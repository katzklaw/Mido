package app.mido.reminders

import android.content.Context
import app.mido.data.AppDatabase
import app.mido.data.MissedReminders
import app.mido.data.ReminderTime
import app.mido.ui.Fmt
import java.time.Instant
import java.time.ZoneId

/**
 * Safety net: if the system swallowed a reminder (battery saver, reboot, phone off), say so
 * as soon as we get a chance instead of staying silent.
 */
object MissedSweep {
    suspend fun run(context: Context) {
        val dao = AppDatabase.get(context).taskDao()
        val now = System.currentTimeMillis()
        for (task in MissedReminders.find(dao.getOpen(), now)) {
            val at = ReminderTime.triggerAt(task) ?: continue
            val whenText = Fmt.time(Instant.ofEpochMilli(at).atZone(ZoneId.systemDefault()).toLocalDateTime())
            Notifications.showReminder(context, task, "Missed reminder, it was due $whenText")
            dao.update(task.copy(notifiedAt = now))
        }
    }
}
