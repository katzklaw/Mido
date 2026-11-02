package app.mido.reminders

import android.content.Context
import app.mido.data.TaskEntity
import app.mido.settings.AppSettings

/** Shows a reminder and, when persistent alerts are on, starts the repeat alerts. */
object ReminderAlert {
    fun fire(context: Context, task: TaskEntity, text: String? = null) {
        val persistent = AppSettings(context).persistentAlerts
        Notifications.showReminder(context, task, text, sticky = persistent)
        if (persistent) NagScheduler.scheduleNext(context, task.id, 0)
    }
}
