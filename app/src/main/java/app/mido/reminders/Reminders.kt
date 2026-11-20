package app.mido.reminders

import android.content.Context
import app.mido.data.ReminderGateway
import app.mido.data.TaskEntity

/** Time alarms and location geofences behind one gateway. */
object Reminders {
    fun gateway(context: Context): ReminderGateway {
        val alarms = AlarmReminderScheduler(context)
        val fences = GeofenceManager(context)
        return object : ReminderGateway {
            override fun schedule(task: TaskEntity) {
                alarms.schedule(task)
                fences.schedule(task)
                PinnedNotes.sync(context, task)
            }

            override fun cancel(taskId: Long) {
                alarms.cancel(taskId)
                fences.cancel(taskId)
                PinnedNotes.cancel(context, taskId)
            }
        }
    }

    fun repository(context: Context) = app.mido.data.TaskRepository(
        dao = app.mido.data.AppDatabase.get(context).taskDao(),
        reminders = gateway(context),
        places = GoogleMapsPlaceResolver(context),
        calendar = app.mido.calendar.AndroidCalendarSink(context, app.mido.settings.AppSettings(context)),
        onChanged = { app.mido.widget.WidgetUpdater.refresh(context) },
    )
}
