package app.mido.calendar

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** The last drive legs we worked out, so the widget can show them without hitting the network. */
class LegCache(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("leg_cache", Context.MODE_PRIVATE)

    fun save(legs: List<DriveLeg>) {
        val arr = JSONArray()
        legs.forEach {
            arr.put(
                JSONObject()
                    .put("k", it.eventKey).put("t", it.title).put("l", it.location)
                    .put("s", it.startMs).put("b", it.leaveByMs).put("m", it.driveMinutes),
            )
        }
        prefs.edit().putString("legs", arr.toString()).apply()
    }

    fun load(): List<DriveLeg> = runCatching {
        val arr = JSONArray(prefs.getString("legs", "[]"))
        (0 until arr.length()).map {
            val o = arr.getJSONObject(it)
            DriveLeg(o.getString("k"), o.getString("t"), o.getString("l"), o.getLong("s"), o.getLong("b"), o.getInt("m"))
        }
    }.getOrDefault(emptyList())
}
