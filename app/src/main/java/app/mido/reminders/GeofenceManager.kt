package app.mido.reminders

import app.mido.pro.Entitlements
import android.Manifest
import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import app.mido.data.LocationTrigger
import app.mido.data.ReminderGateway
import app.mido.data.TaskEntity
import app.mido.data.parsedTrigger
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofencingRequest
import com.google.android.gms.location.LocationServices

/** Registers an arrive/leave geofence for each open task that has a trigger and a resolved place. */
class GeofenceManager(context: Context) : ReminderGateway {
    private val app = context.applicationContext
    private val client = LocationServices.getGeofencingClient(app)

    @SuppressLint("MissingPermission")
    override fun schedule(task: TaskEntity) {
        val trigger = task.parsedTrigger
        val lat = task.latitude
        val lng = task.longitude
        if (task.completed || trigger == null || lat == null || lng == null || !Entitlements.isPro(app)) {
            cancel(task.id)
            return
        }
        if (ContextCompat.checkSelfPermission(app, Manifest.permission.ACCESS_FINE_LOCATION) !=
            PackageManager.PERMISSION_GRANTED
        ) return

        // "When I leave" also listens for entering: leaving only counts if you were seen inside first.
        val transition = when (trigger) {
            LocationTrigger.ARRIVE -> Geofence.GEOFENCE_TRANSITION_ENTER
            LocationTrigger.LEAVE -> Geofence.GEOFENCE_TRANSITION_ENTER or Geofence.GEOFENCE_TRANSITION_EXIT
        }
        val fence = Geofence.Builder()
            .setRequestId(task.id.toString())
            .setCircularRegion(lat, lng, RADIUS_METERS)
            .setExpirationDuration(Geofence.NEVER_EXPIRE)
            .setTransitionTypes(transition)
            .build()
        // Arrive: don't fire just because you're already standing inside the fence. Leave: if you're inside when
        // it's set up, report that (quietly) so the later exit counts.
        val initial = if (trigger == LocationTrigger.LEAVE) GeofencingRequest.INITIAL_TRIGGER_ENTER else 0
        val request = GeofencingRequest.Builder().setInitialTrigger(initial).addGeofence(fence).build()
        try {
            client.addGeofences(request, pendingIntent())
        } catch (e: SecurityException) {
            // Permission revoked; will be retried next time the app opens.
        }
    }

    override fun cancel(taskId: Long) {
        client.removeGeofences(listOf(taskId.toString()))
    }

    private fun pendingIntent(): PendingIntent = PendingIntent.getBroadcast(
        app,
        0,
        Intent(app, GeofenceReceiver::class.java).setAction(GeofenceReceiver.ACTION),
        // Play services fills in the event extras, so this one has to be mutable.
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
    )

    companion object {
        const val RADIUS_METERS = 200f
    }
}
