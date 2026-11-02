package app.mido.calendar

import app.mido.data.CalendarEvent

data class LatLng(val latitude: Double, val longitude: Double)

/** Where a drive ends: a free-text address/place name (as typed in the event). */
data class Destination(val address: String)

/** Driving time with traffic, in seconds. [departAtMs] null means "leaving right now". */
interface TravelTimeProvider {
    suspend fun driveSeconds(origin: LatLng, destination: Destination, departAtMs: Long?): Int?
}

fun interface OriginProvider {
    suspend fun current(): LatLng?
}

/** "Leave by 4:25 PM to reach X at 4:55 PM". */
data class DriveLeg(
    val eventKey: String,
    val title: String,
    val location: String,
    val startMs: Long,
    val leaveByMs: Long,
    val driveMinutes: Int,
)

object LeaveMath {
    const val DEFAULT_BUFFER_MIN = 5

    fun leaveBy(startMs: Long, driveSeconds: Int, bufferMin: Int = DEFAULT_BUFFER_MIN): Long =
        startMs - driveSeconds * 1000L - bufferMin * 60_000L

    fun minutes(driveSeconds: Int): Int = ((driveSeconds + 59) / 60).coerceAtLeast(1)

    private val virtualMarkers = listOf("http://", "https://", "zoom.us", "meet.google", "teams.microsoft", "webex", "hangouts")

    /** Video-call links and the like aren't places you can drive to. */
    fun isPhysicalPlace(location: String?): Boolean {
        val l = location?.trim()?.lowercase() ?: return false
        if (l.isEmpty()) return false
        return virtualMarkers.none { l.contains(it) }
    }
}

object RoutesParsing {
    /** Routes API durations look like "1500s" or "1500.5s". */
    fun parseDurationSeconds(raw: String?): Int? {
        val t = raw?.trim()?.removeSuffix("s") ?: return null
        return t.toDoubleOrNull()?.toInt()
    }
}

class LeavePlanner(
    private val travel: TravelTimeProvider,
    private val origin: OriginProvider,
    private val bufferMin: Int = LeaveMath.DEFAULT_BUFFER_MIN,
    private val horizonMs: Long = 12 * 60 * 60_000L,
    private val maxEvents: Int = 4,
) {
    /**
     * For the next few timed events with a physical location, works out when to leave.
     * Uses current traffic, then (if the event is a while away) re-asks with the predicted
     * departure time so rush-hour changes are accounted for.
     */
    suspend fun plan(events: List<CalendarEvent>, nowMs: Long): List<DriveLeg> {
        val upcoming = events
            .filter { !it.allDay && it.startMs > nowMs && it.startMs <= nowMs + horizonMs && LeaveMath.isPhysicalPlace(it.location) }
            .sortedBy { it.startMs }
            .take(maxEvents)
        if (upcoming.isEmpty()) return emptyList()
        val from = origin.current() ?: return emptyList()

        return upcoming.mapNotNull { e ->
            val dest = Destination(e.location!!.trim())
            var secs = travel.driveSeconds(from, dest, null) ?: return@mapNotNull null
            var leaveBy = LeaveMath.leaveBy(e.startMs, secs, bufferMin)
            if (leaveBy - nowMs > 20 * 60_000L) {
                travel.driveSeconds(from, dest, leaveBy)?.let {
                    secs = it
                    leaveBy = LeaveMath.leaveBy(e.startMs, secs, bufferMin)
                }
            }
            DriveLeg(e.key, e.title, dest.address, e.startMs, leaveBy, LeaveMath.minutes(secs))
        }
    }
}
