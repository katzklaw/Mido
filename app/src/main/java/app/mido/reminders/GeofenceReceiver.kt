package app.mido.reminders

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import app.mido.data.AppDatabase
import app.mido.data.parsedTrigger
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofencingEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** DING: you arrived at / left the place a task is tied to. */
class GeofenceReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val event = GeofencingEvent.fromIntent(intent) ?: return
        if (event.hasError()) return
        val ids = event.triggeringGeofences.orEmpty().mapNotNull { it.requestId.toLongOrNull() }
        if (ids.isEmpty()) return
        val arrived = event.geofenceTransition == Geofence.GEOFENCE_TRANSITION_ENTER
        val left = event.geofenceTransition == Geofence.GEOFENCE_TRANSITION_EXIT
        if (!arrived && !left) return

        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val dao = AppDatabase.get(context).taskDao()
                for (id in ids) {
                    val stored = dao.getById(id) ?: continue
                    if (stored.completed) continue
                    var task = stored
                    val now = System.currentTimeMillis()
                    val trigger = task.parsedTrigger ?: continue
                    val decision = GeofenceRules.decide(trigger, arrived, task.dueAt, task.placeEnteredAt, now)
                    if (decision.placeEnteredAt != task.placeEnteredAt) {
                        task = task.copy(placeEnteredAt = decision.placeEnteredAt)
                        dao.update(task)
                    }
                    if (decision.action != GeofenceRules.Action.ALERT) continue
                    if (task.locationRepeat) {
                        // Ignore GPS wobble: a place reminder can't fire again within 30 minutes.
                        val last = task.lastLocationFiredAt
                        if (last != null && now - last < REPEAT_COOLDOWN_MS) continue
                        task = task.copy(lastLocationFiredAt = now)
                        dao.update(task)
                    }
                    val place = task.locationName ?: "your location"
                    val text = if (arrived) "You arrived at $place" else "You left $place"
                    ReminderAlert.fire(context, task, text)
                }
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val ACTION = "app.mido.action.GEOFENCE"
        const val REPEAT_COOLDOWN_MS = 30 * 60_000L
    }
}
