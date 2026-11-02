package app.mido.calendar

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import app.mido.reminders.Notifications
import app.mido.ui.Fmt
import app.mido.widget.WidgetUpdater

/**
 * Sets alarms for each drive leg, replacing the ones from last time:
 * a "time to leave" alert at leave-by, and a wake-up three hours before it to pin the countdown.
 */
class LeaveAlarms(context: Context) {
    private val app = context.applicationContext
    private val alarms = app.getSystemService(Context.ALARM_SERVICE) as AlarmManager
    private val prefs = app.getSharedPreferences("leave_alarms", Context.MODE_PRIVATE)

    fun sync(legs: List<DriveLeg>) {
        LegCache(app).save(legs) // the home screen widget reads these
        val now = System.currentTimeMillis()
        val wanted = legs.filter { it.leaveByMs > now }
        val keep = wanted.map { it.eventKey }.toSet()

        // Drop alarms for events that vanished, moved, or whose leg is gone.
        prefs.getStringSet(KEY, emptySet()).orEmpty().filter { it !in keep }.forEach {
            alarms.cancel(pending(KIND_ALERT, it, null))
            alarms.cancel(pending(KIND_COUNTDOWN, it, null))
        }

        for (leg in wanted) {
            arm(leg.leaveByMs, pending(KIND_ALERT, leg.eventKey, leg))
            val countdownAt = leg.leaveByMs - Countdown.WINDOW_MS
            if (countdownAt > now) arm(countdownAt, pending(KIND_COUNTDOWN, leg.eventKey, leg))
        }
        prefs.edit().putStringSet(KEY, keep).apply()

        LeaveCountdown.sync(app, legs)
        WidgetUpdater.refresh(app)
    }

    private fun arm(atMs: Long, pi: PendingIntent) {
        try {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarms.canScheduleExactAlarms()) {
                alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMs, pi)
            } else {
                alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMs, pi)
            }
        } catch (e: SecurityException) {
            alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMs, pi)
        }
    }

    private fun pending(kind: Int, eventKey: String, leg: DriveLeg?): PendingIntent {
        val intent = Intent(app, LeaveByReceiver::class.java)
            .setAction(if (kind == KIND_ALERT) LeaveByReceiver.ACTION else LeaveByReceiver.ACTION_COUNTDOWN)
            .putExtra(LeaveByReceiver.EXTRA_KEY, eventKey)
        if (leg != null) LegIntent.put(intent, leg)
        return PendingIntent.getBroadcast(
            app, eventKey.hashCode() * 31 + kind, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private companion object {
        const val KEY = "scheduled"
        const val KIND_ALERT = 1
        const val KIND_COUNTDOWN = 2
    }
}

class LeaveByReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val leg = LegIntent.read(intent) ?: return
        when (intent.action) {
            ACTION -> {
                val text = "${leg.driveMinutes} min drive · ${leg.title} at ${Fmt.time(leg.startMs)}"
                val channel = if (app.mido.settings.AppSettings(context).loudLeaveAlert) Notifications.CHANNEL_LEAVE else Notifications.CHANNEL_REMINDERS
                Notifications.showSimple(context, 1_000_000 + (leg.eventKey.hashCode() and 0xFFFFF), "Time to leave", text, channel)
                // Flip the pinned countdown to "Leave now".
                LeaveCountdown.start(context, leg)
            }
            // Started by an exact alarm, which is allowed to launch the countdown service from the background.
            ACTION_COUNTDOWN -> LeaveCountdown.start(context, leg)
        }
    }

    companion object {
        const val ACTION = "app.mido.action.LEAVE_BY"
        const val ACTION_COUNTDOWN = "app.mido.action.LEAVE_COUNTDOWN"
        const val EXTRA_KEY = "event_key"
    }
}
