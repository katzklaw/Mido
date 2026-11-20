package app.mido.reminders

import app.mido.data.LocationTrigger
import app.mido.reminders.GeofenceRules.Action
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId

class GeofenceRulesTest {
    private val zone = ZoneId.of("America/Chicago")
    private fun ms(dt: LocalDateTime) = dt.atZone(zone).toInstant().toEpochMilli()
    private val today = LocalDateTime.of(2026, 11, 6, 17, 0)
    private val tomorrow8 = LocalDateTime.of(2026, 11, 7, 8, 0)

    private fun decide(t: LocationTrigger, entered: Boolean, due: LocalDateTime?, enteredAt: Long?, now: LocalDateTime) =
        GeofenceRules.decide(t, entered, due?.let(::ms), enteredAt, ms(now), zone)

    @Test fun leavingWhenYouWereNeverInsideDoesNothing() {
        // Created at home, fence set up at work: the phone says "outside" -> must not alert.
        val d = decide(LocationTrigger.LEAVE, entered = false, due = null, enteredAt = null, now = today)
        assertEquals(Action.IGNORE, d.action)
    }

    @Test fun enteringThenLeavingAlertsOnce() {
        val enter = decide(LocationTrigger.LEAVE, true, null, null, today)
        assertEquals(Action.MARK_INSIDE, enter.action)
        val leave = decide(LocationTrigger.LEAVE, false, null, enter.placeEnteredAt, today.plusHours(8))
        assertEquals(Action.ALERT, leave.action)
        assertNull(leave.placeEnteredAt)
        // A second exit without re-entering is ignored.
        assertEquals(Action.IGNORE, decide(LocationTrigger.LEAVE, false, null, leave.placeEnteredAt, today.plusHours(9)).action)
    }

    @Test fun tasksDueTomorrowStayQuietToday() {
        val passByWork = decide(LocationTrigger.LEAVE, true, tomorrow8, null, today)
        assertEquals(Action.IGNORE, passByWork.action)
        assertNull(passByWork.placeEnteredAt) // today's visit doesn't count
        val arriveTomorrow = decide(LocationTrigger.LEAVE, true, tomorrow8, null, tomorrow8.plusHours(1))
        assertEquals(Action.MARK_INSIDE, arriveTomorrow.action)
        val leaveTomorrow = decide(LocationTrigger.LEAVE, false, tomorrow8, arriveTomorrow.placeEnteredAt, tomorrow8.plusHours(9))
        assertEquals(Action.ALERT, leaveTomorrow.action)
    }

    @Test fun arrivalAlertsOnlyOnEnterAndOnlyFromTheDueDay() {
        assertEquals(Action.IGNORE, decide(LocationTrigger.ARRIVE, true, tomorrow8, null, today).action)
        assertEquals(Action.ALERT, decide(LocationTrigger.ARRIVE, true, tomorrow8, null, tomorrow8).action)
        assertEquals(Action.ALERT, decide(LocationTrigger.ARRIVE, true, null, null, today).action)
        assertEquals(Action.IGNORE, decide(LocationTrigger.ARRIVE, false, null, null, today).action)
    }

    @Test fun overdueTasksStillWork() {
        val yesterday = today.minusDays(1)
        assertEquals(Action.ALERT, decide(LocationTrigger.ARRIVE, true, yesterday, null, today).action)
    }
}
