package app.mido.calendar

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import androidx.core.content.ContextCompat

/** Where you are now, from the phone's most recent location fix. */
class LastKnownOrigin(context: Context) : OriginProvider {
    private val app = context.applicationContext

    @SuppressLint("MissingPermission")
    override suspend fun current(): LatLng? {
        val ok = ContextCompat.checkSelfPermission(app, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(app, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (!ok) return null
        val lm = app.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val best = lm.getProviders(true)
            .mapNotNull { runCatching { lm.getLastKnownLocation(it) }.getOrNull() }
            .maxByOrNull { it.time } ?: return null
        return LatLng(best.latitude, best.longitude)
    }
}
