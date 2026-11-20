package app.mido.reminders

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import app.mido.MainActivity
import app.mido.data.Checklist
import app.mido.data.TaskEntity

/**
 * Tasks and checklists kept in the notification shade. The notification is "ongoing" so it resists swiping, and if
 * Android lets someone swipe it away anyway, the dismissal puts it straight back (until the task is done or unpinned).
 */
object PinnedNotes {
    private const val ID_BASE = 2_000_000

    private fun notificationId(taskId: Long) = ID_BASE + taskId.toInt()

    /** Shows, refreshes or removes the pinned notification so it matches the task. */
    fun sync(context: Context, task: TaskEntity) {
        if (!task.shadePinned || task.completed) {
            cancel(context, task.id)
            return
        }
        if (!Notifications.canPost(context)) return
        Notifications.ensureChannels(context)

        val open = PendingIntent.getActivity(
            context, 0, Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE,
        )
        val progress = Checklist.progress(task.notes)
        val summary = when {
            progress != null -> "${progress.first} of ${progress.second} done"
            task.notes.isNotBlank() -> task.notes.lineSequence().first()
            else -> null
        }
        val builder = NotificationCompat.Builder(context, Notifications.CHANNEL_PINNED)
            .setSmallIcon(android.R.drawable.ic_menu_agenda)
            .setContentTitle(task.title)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(open)
            .setDeleteIntent(broadcast(context, task.id, NotificationActionReceiver.ACTION_REPOST))
        if (summary != null) builder.setContentText(summary)
        if (task.notes.isNotBlank()) {
            builder.setStyle(NotificationCompat.BigTextStyle().bigText(Checklist.forDisplay(task.notes)))
        }
        if (Checklist.firstOpenIndex(task.notes) != null) {
            builder.addAction(0, "✓ Next item", broadcast(context, task.id, NotificationActionReceiver.ACTION_NEXT_ITEM))
        }
        builder.addAction(0, "Done", broadcast(context, task.id, NotificationActionReceiver.ACTION_DONE))

        try {
            NotificationManagerCompat.from(context).notify(notificationId(task.id), builder.build())
        } catch (e: SecurityException) {
            // Notification permission was revoked; nothing to do.
        }
    }

    fun cancel(context: Context, taskId: Long) {
        NotificationManagerCompat.from(context).cancel(notificationId(taskId))
    }

    private fun broadcast(context: Context, taskId: Long, action: String): PendingIntent {
        val intent = Intent(context, NotificationActionReceiver::class.java)
            .setAction(action)
            .putExtra(ReminderReceiver.EXTRA_TASK_ID, taskId)
        // Own request-code range so these never collide with a reminder's Done/Snooze buttons.
        val request = 7_000_000 + (taskId.toInt() * 31) + action.hashCode()
        return PendingIntent.getBroadcast(context, request, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }
}
