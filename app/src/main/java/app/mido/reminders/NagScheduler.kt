package app.mido.reminders

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import app.mido.settings.AppSettings

/** Chains exact alarms that re-alert a reminder until it is completed or snoozed. */
object NagScheduler {
    const val ACTION_NAG = "app.mido.action.NAG"
    const val EXTRA_SENT = "nags_sent"

    /** Arms the next repeat after [sent] have gone out. No-op when the setting is off or the cap is hit. */
    fun scheduleNext(context: Context, taskId: Long, sent: Int) {
        val settings = AppSettings(context)
        if (!settings.persistentAlerts) return
        val at = NagPolicy.nextAt(sent, System.currentTimeMillis(), settings.nagIntervalMin) ?: return
        val app = context.applicationContext
        val alarms = app.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pi = pendingIntent(app, taskId, sent)
        val canExact = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarms.canScheduleExactAlarms()
        try {
            if (canExact) alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
            else alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
        } catch (e: SecurityException) {
            alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
        }
    }

    fun cancel(context: Context, taskId: Long) {
        val app = context.applicationContext
        (app.getSystemService(Context.ALARM_SERVICE) as AlarmManager).cancel(pendingIntent(app, taskId, 0))
    }

    private fun pendingIntent(app: Context, taskId: Long, sent: Int): PendingIntent {
        val intent = Intent(app, NagReceiver::class.java)
            .setAction(ACTION_NAG)
            .putExtra(ReminderReceiver.EXTRA_TASK_ID, taskId)
            .putExtra(EXTRA_SENT, sent)
        return PendingIntent.getBroadcast(
            app, taskId.toInt(), intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }
}
