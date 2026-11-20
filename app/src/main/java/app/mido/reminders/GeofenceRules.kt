package app.mido.reminders

import app.mido.data.LocationTrigger
import java.time.Instant
import java.time.ZoneId

/** Decides what a geofence enter/exit means for a task. Pure, so it can be tested without Android. */
object GeofenceRules {
    enum class Action { IGNORE, MARK_INSIDE, ALERT }

    data class Decision(val action: Action, val placeEnteredAt: Long?)

    /**
     * [entered] is true for an ENTER transition and false for EXIT. A task with a due date stays quiet until that
     * day starts, so "buy pizza when I leave work" set for tomorrow doesn't go off today. "Leave" only fires after
     * you've been seen inside the place, so being somewhere else when the fence is set up doesn't count as leaving.
     */
    fun decide(
        trigger: LocationTrigger,
        entered: Boolean,
        dueAt: Long?,
        placeEnteredAt: Long?,
        nowMs: Long,
        zone: ZoneId = ZoneId.systemDefault(),
    ): Decision {
        if (dueAt != null) {
            val dayStart = Instant.ofEpochMilli(dueAt).atZone(zone).toLocalDate().atStartOfDay(zone).toInstant().toEpochMilli()
            if (nowMs < dayStart) return Decision(Action.IGNORE, placeEnteredAt)
        }
        return when (trigger) {
            LocationTrigger.ARRIVE ->
                if (entered) Decision(Action.ALERT, placeEnteredAt) else Decision(Action.IGNORE, placeEnteredAt)
            LocationTrigger.LEAVE -> when {
                entered -> Decision(Action.MARK_INSIDE, nowMs)
                placeEnteredAt != null -> Decision(Action.ALERT, null)
                else -> Decision(Action.IGNORE, null)
            }
        }
    }
}
