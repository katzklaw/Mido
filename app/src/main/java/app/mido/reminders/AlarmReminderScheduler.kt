package app.mido.reminders

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import app.mido.data.ReminderGateway
import app.mido.data.ReminderTime
import app.mido.data.TaskEntity

class AlarmReminderScheduler(context: Context) : ReminderGateway {
    private val app = context.applicationContext
    private val alarms = app.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    override fun schedule(task: TaskEntity) {
        val at = ReminderTime.triggerAt(task)
        if (at == null || at <= System.currentTimeMillis()) {
            alarms.cancel(pendingIntent(task.id))
        } else {
            scheduleAt(task.id, at)
        }
    }

    /** Also used for snoozing: replaces any alarm already set for this task. */
    fun scheduleAt(taskId: Long, atMillis: Long) {
        NagScheduler.cancel(app, taskId) // a new time replaces any repeat alerts for the old one
        val pi = pendingIntent(taskId)
        val canExact = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarms.canScheduleExactAlarms()
        try {
            if (canExact) {
                alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMillis, pi)
            } else {
                alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMillis, pi)
            }
        } catch (e: SecurityException) {
            alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMillis, pi)
        }
    }

    override fun cancel(taskId: Long) {
        alarms.cancel(pendingIntent(taskId))
        NagScheduler.cancel(app, taskId)
        Notifications.dismiss(app, taskId)
    }

    private fun pendingIntent(taskId: Long): PendingIntent {
        val intent = Intent(app, ReminderReceiver::class.java)
            .setAction(ReminderReceiver.ACTION_FIRE)
            .putExtra(ReminderReceiver.EXTRA_TASK_ID, taskId)
        return PendingIntent.getBroadcast(
            app,
            taskId.toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }
}
