package app.mido.calendar

import app.mido.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.time.Instant

/** Live driving time via the Google Routes API (needs MAPS_API_KEY with Routes API enabled). */
class RoutesTravelTime : TravelTimeProvider {
    private data class CacheKey(val lat: Double, val lng: Double, val dest: String, val bucket: Long)

    private val cache = HashMap<CacheKey, Int>()

    override suspend fun driveSeconds(origin: LatLng, destination: Destination, departAtMs: Long?): Int? {
        if (BuildConfig.MAPS_API_KEY.isBlank()) return null
        // Reuse answers for ~5 minutes so reopening the app doesn't burn API calls.
        val bucket = (departAtMs ?: System.currentTimeMillis()) / (5 * 60_000L)
        val key = CacheKey(Math.round(origin.latitude * 1000) / 1000.0, Math.round(origin.longitude * 1000) / 1000.0, destination.address, bucket)
        cache[key]?.let { return it }
        val secs = withContext(Dispatchers.IO) { runCatching { request(origin, destination, departAtMs) }.getOrNull() }
        if (secs != null) cache[key] = secs
        return secs
    }

    private fun request(origin: LatLng, destination: Destination, departAtMs: Long?): Int? {
        val body = JSONObject()
            .put(
                "origin",
                JSONObject().put(
                    "location",
                    JSONObject().put("latLng", JSONObject().put("latitude", origin.latitude).put("longitude", origin.longitude)),
                ),
            )
            .put("destination", JSONObject().put("address", destination.address))
            .put("travelMode", "DRIVE")
            .put("routingPreference", "TRAFFIC_AWARE")
        // The API only accepts a departure time in the future.
        if (departAtMs != null && departAtMs > System.currentTimeMillis() + 60_000L) {
            body.put("departureTime", Instant.ofEpochMilli(departAtMs).toString())
        }
        val conn = URL("https://routes.googleapis.com/directions/v2:computeRoutes").openConnection() as HttpURLConnection
        try {
            conn.requestMethod = "POST"
            conn.connectTimeout = 10_000
            conn.readTimeout = 10_000
            conn.doOutput = true
            conn.setRequestProperty("Content-Type", "application/json")
            conn.setRequestProperty("X-Goog-Api-Key", BuildConfig.MAPS_API_KEY)
            conn.setRequestProperty("X-Goog-FieldMask", "routes.duration")
            conn.outputStream.use { it.write(body.toString().toByteArray()) }
            if (conn.responseCode !in 200..299) return null
            val text = conn.inputStream.bufferedReader().use { it.readText() }
            val route = JSONObject(text).optJSONArray("routes")?.optJSONObject(0) ?: return null
            return RoutesParsing.parseDurationSeconds(route.optString("duration"))
        } finally {
            conn.disconnect()
        }
    }
}
