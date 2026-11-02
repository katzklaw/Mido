package app.mido.reminders

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import app.mido.MainActivity
import app.mido.data.TaskEntity
import app.mido.data.parsedTrigger
import app.mido.ui.Fmt

object Notifications {
    const val CHANNEL_REMINDERS = "reminders"
    const val CHANNEL_COUNTDOWN = "countdown"
    const val CHANNEL_SUGGESTIONS = "suggestions"
    /** Alarm-style channel used while "keep alerting until I respond" is on. */
    const val CHANNEL_ALERTS = "reminders_persistent"
    /** Alarm-style channel for the "time to leave" alert. */
    const val CHANNEL_LEAVE = "time_to_leave"
    const val SNOOZE_MINUTES = 10L

    fun ensureChannels(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_SUGGESTIONS, "Suggestions", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "Offers to call back missed calls or turn texts into tasks"
            },
        )
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_REMINDERS, "Reminders", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Task and location reminders"
            },
        )
        nm.createNotificationChannel(
            alarmChannel(CHANNEL_ALERTS, "Persistent reminders", "Reminders that keep alerting, with an alarm sound, until you mark them done or snooze them"),
        )
        nm.createNotificationChannel(
            alarmChannel(CHANNEL_LEAVE, "Time to leave", "A loud alarm-tone alert when it's time to leave for an event"),
        )
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_COUNTDOWN, "Leave-by countdown", NotificationManager.IMPORTANCE_LOW).apply {
                description = "A pinned countdown to when you need to leave"
                setShowBadge(false)
            },
        )
    }

    private fun alarmChannel(id: String, name: String, desc: String) =
        NotificationChannel(id, name, NotificationManager.IMPORTANCE_HIGH).apply {
            description = desc
            setSound(
                android.media.RingtoneManager.getDefaultUri(android.media.RingtoneManager.TYPE_ALARM)
                    ?: android.media.RingtoneManager.getDefaultUri(android.media.RingtoneManager.TYPE_NOTIFICATION),
                android.media.AudioAttributes.Builder()
                    .setUsage(android.media.AudioAttributes.USAGE_ALARM)
                    .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build(),
            )
            enableVibration(true)
            vibrationPattern = longArrayOf(0, 400, 200, 400, 200, 400)
        }

    fun canPost(context: Context): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) return false
        return NotificationManagerCompat.from(context).areNotificationsEnabled()
    }

    fun showReminder(context: Context, task: TaskEntity, text: String? = null, sticky: Boolean = false) {
        if (!canPost(context)) return
        ensureChannels(context)

        val open = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE,
        )

        val body = text ?: task.locationName?.let { "📍 ${Fmt.placeLabel(it, task.parsedTrigger)}" }

        val notification = NotificationCompat.Builder(context, if (sticky) CHANNEL_ALERTS else CHANNEL_REMINDERS)
            .setSmallIcon(android.R.drawable.ic_popup_reminder)
            .setContentTitle(task.title)
            .apply { if (body != null) setContentText(body) }
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(if (sticky) NotificationCompat.CATEGORY_ALARM else NotificationCompat.CATEGORY_REMINDER)
            .setAutoCancel(!sticky)
            .setOngoing(sticky)
            .setOnlyAlertOnce(false)
            .setContentIntent(open)
            .addAction(0, "Done", actionIntent(context, task.id, NotificationActionReceiver.ACTION_DONE))
            .addAction(
                0,
                "Snooze ${SNOOZE_MINUTES}m",
                actionIntent(context, task.id, NotificationActionReceiver.ACTION_SNOOZE),
            )
            .build()

        try {
            NotificationManagerCompat.from(context).notify(task.id.toInt(), notification)
        } catch (e: SecurityException) {
            // Permission was revoked between the check and the post; nothing to do.
        }
    }

    /** A plain notification with no buttons (used for "time to leave"). */
    fun showSimple(context: Context, id: Int, title: String, text: String, channel: String = CHANNEL_REMINDERS) {
        if (!canPost(context)) return
        ensureChannels(context)
        val open = PendingIntent.getActivity(
            context, 0, Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE,
        )
        val n = NotificationCompat.Builder(context, channel)
            .setSmallIcon(android.R.drawable.ic_menu_directions)
            .setContentTitle(title)
            .setContentText(text)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .setContentIntent(open)
            .build()
        try {
            NotificationManagerCompat.from(context).notify(id, n)
        } catch (e: SecurityException) {
        }
    }

    /** A suggestion with up to a few buttons ("Call back", "Add task"...). */
    fun showSuggestion(
        context: Context,
        id: Int,
        title: String,
        text: String,
        icon: Int,
        actions: List<Pair<String, PendingIntent>>,
        contentIntent: PendingIntent? = null,
    ) {
        if (!canPost(context)) return
        ensureChannels(context)
        val b = NotificationCompat.Builder(context, CHANNEL_SUGGESTIONS)
            .setSmallIcon(icon)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
        contentIntent?.let { b.setContentIntent(it) }
        actions.forEach { (label, pi) -> b.addAction(0, label, pi) }
        try {
            NotificationManagerCompat.from(context).notify(id, b.build())
        } catch (e: SecurityException) {
        }
    }

    /** The pinned "Leave for X" countdown. Counts down to leave-by, then to the event start. */
    fun buildCountdown(context: Context, leg: app.mido.calendar.DriveLeg, nowMs: Long): android.app.Notification {
        val target = app.mido.calendar.Countdown.target(leg, nowMs)
        val open = PendingIntent.getActivity(
            context, 0, Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(context, CHANNEL_COUNTDOWN)
            .setSmallIcon(android.R.drawable.ic_menu_directions)
            .setContentTitle(if (target.leaveNow) "Leave now for “${leg.title}”" else "Leave for “${leg.title}”")
            .setContentText("🚗 ${leg.driveMinutes} min drive · starts ${Fmt.time(leg.startMs)}")
            .setWhen(target.whenMs)
            .setShowWhen(true)
            .setUsesChronometer(true)
            .setChronometerCountDown(true)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_EVENT)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .setContentIntent(open)
            .build()
    }

    /** Fallback when a foreground service can't be started: an ordinary ongoing notification. */
    fun notifyCountdown(context: Context, leg: app.mido.calendar.DriveLeg) {
        if (!canPost(context)) return
        ensureChannels(context)
        try {
            NotificationManagerCompat.from(context)
                .notify(app.mido.calendar.LeaveCountdown.NOTIFICATION_ID, buildCountdown(context, leg, System.currentTimeMillis()))
        } catch (e: SecurityException) {
        }
    }

    fun dismiss(context: Context, taskId: Long) {
        NotificationManagerCompat.from(context).cancel(taskId.toInt())
    }

    private fun actionIntent(context: Context, taskId: Long, action: String): PendingIntent {
        val intent = Intent(context, NotificationActionReceiver::class.java)
            .setAction(action)
            .putExtra(ReminderReceiver.EXTRA_TASK_ID, taskId)
        // Distinct request code per task+action so the two buttons never collide.
        val request = (taskId.toInt() * 31) + action.hashCode()
        return PendingIntent.getBroadcast(context, request, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }
}
