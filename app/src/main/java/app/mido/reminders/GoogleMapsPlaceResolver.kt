package app.mido.reminders

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Geocoder
import android.location.Location
import android.location.LocationManager
import androidx.core.content.ContextCompat
import app.mido.BuildConfig
import app.mido.data.PlaceResolver
import app.mido.data.ResolvedPlace
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Uses the Places API (New) text search when MAPS_API_KEY is set in local.properties,
 * biased toward wherever the phone last was. Falls back to Android's built-in Geocoder.
 */
class GoogleMapsPlaceResolver(context: Context) : PlaceResolver {
    private val app = context.applicationContext

    override suspend fun resolve(query: String): ResolvedPlace? = withContext(Dispatchers.IO) {
        val viaPlaces = if (BuildConfig.MAPS_API_KEY.isNotBlank()) {
            runCatching { searchPlaces(query, lastKnownLocation()) }.getOrNull()
        } else {
            null
        }
        viaPlaces ?: runCatching { geocode(query) }.getOrNull()
    }

    private fun searchPlaces(query: String, bias: Location?): ResolvedPlace? {
        val body = JSONObject().put("textQuery", query).put("maxResultCount", 1)
        if (bias != null) {
            body.put(
                "locationBias",
                JSONObject().put(
                    "circle",
                    JSONObject()
                        .put(
                            "center",
                            JSONObject().put("latitude", bias.latitude).put("longitude", bias.longitude),
                        )
                        .put("radius", 30_000.0),
                ),
            )
        }
        val conn = URL("https://places.googleapis.com/v1/places:searchText").openConnection() as HttpURLConnection
        try {
            conn.requestMethod = "POST"
            conn.connectTimeout = 10_000
            conn.readTimeout = 10_000
            conn.doOutput = true
            conn.setRequestProperty("Content-Type", "application/json")
            conn.setRequestProperty("X-Goog-Api-Key", BuildConfig.MAPS_API_KEY)
            conn.setRequestProperty("X-Goog-FieldMask", "places.displayName,places.formattedAddress,places.location")
            conn.outputStream.use { it.write(body.toString().toByteArray()) }
            if (conn.responseCode !in 200..299) return null
            val text = conn.inputStream.bufferedReader().use { it.readText() }
            val place = JSONObject(text).optJSONArray("places")?.optJSONObject(0) ?: return null
            val loc = place.optJSONObject("location") ?: return null
            return ResolvedPlace(
                name = place.optJSONObject("displayName")?.optString("text").orEmpty().ifBlank { query },
                address = place.optString("formattedAddress").ifBlank { null },
                latitude = loc.getDouble("latitude"),
                longitude = loc.getDouble("longitude"),
            )
        } finally {
            conn.disconnect()
        }
    }

    @Suppress("DEPRECATION")
    private fun geocode(query: String): ResolvedPlace? {
        if (!Geocoder.isPresent()) return null
        val hit = Geocoder(app).getFromLocationName(query, 1)?.firstOrNull() ?: return null
        return ResolvedPlace(query, hit.getAddressLine(0), hit.latitude, hit.longitude)
    }

    @SuppressLint("MissingPermission")
    private fun lastKnownLocation(): Location? {
        val granted = ContextCompat.checkSelfPermission(app, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(app, Manifest.permission.ACCESS_COARSE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED
        if (!granted) return null
        val lm = app.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        return lm.getProviders(true).mapNotNull { runCatching { lm.getLastKnownLocation(it) }.getOrNull() }
            .maxByOrNull { it.time }
    }
}
