package app.mido.calendar

/** Rules for the pinned "leave for X in N minutes" notification. */
object Countdown {
    /** Start showing it this long before you need to leave. */
    const val WINDOW_MS = 3 * 60 * 60_000L

    /** The leg to count down to: the soonest event that hasn't started, once its leave-by is close. */
    fun pick(legs: List<DriveLeg>, nowMs: Long): DriveLeg? =
        legs.filter { it.startMs > nowMs && it.leaveByMs - nowMs <= WINDOW_MS }.minByOrNull { it.startMs }

    /** Before leave-by, count down to it; after, count down to the event start. */
    fun target(leg: DriveLeg, nowMs: Long): Target =
        if (nowMs >= leg.leaveByMs) Target(leaveNow = true, whenMs = leg.startMs) else Target(leaveNow = false, whenMs = leg.leaveByMs)

    data class Target(val leaveNow: Boolean, val whenMs: Long)
}
