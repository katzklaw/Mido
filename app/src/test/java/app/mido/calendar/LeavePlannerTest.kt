package app.mido.calendar

import app.mido.data.CalendarEvent
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.LocalDate

class LeavePlannerTest {
    private val now = 1_000_000_000_000L
    private val min = 60_000L

    private fun ev(id: Long, startOffsetMin: Long, loc: String?, allDay: Boolean = false) =
        CalendarEvent(id, "e$id", now + startOffsetMin * min, now + (startOffsetMin + 30) * min, allDay, loc)

    private class FakeTravel(val nowSecs: Int, val laterSecs: Int = nowSecs) : TravelTimeProvider {
        val calls = mutableListOf<Long?>()
        override suspend fun driveSeconds(origin: LatLng, destination: Destination, departAtMs: Long?): Int? {
            calls += departAtMs
            return if (departAtMs == null) nowSecs else laterSecs
        }
    }

    private val origin = OriginProvider { LatLng(1.0, 2.0) }

    @Test fun leaveByIsStartMinusDriveMinusBuffer() {
        assertEquals(1000L - 600_000L - 300_000L, LeaveMath.leaveBy(1000L, 600, 5))
    }

    @Test fun nearEventUsesSingleCurrentTrafficCall() = runBlocking {
        val travel = FakeTravel(25 * 60)
        val legs = LeavePlanner(travel, origin).plan(listOf(ev(1, 40, "Gym")), now)
        assertEquals(1, legs.size)
        assertEquals(25, legs[0].driveMinutes)
        assertEquals(now + 40 * min - 25 * min - 5 * min, legs[0].leaveByMs)
        assertEquals(listOf<Long?>(null), travel.calls)
    }

    @Test fun farEventIsRefinedWithPredictedDeparture() = runBlocking {
        val travel = FakeTravel(nowSecs = 20 * 60, laterSecs = 40 * 60) // rush hour later
        val legs = LeavePlanner(travel, origin).plan(listOf(ev(1, 240, "Office")), now)
        assertEquals(40, legs[0].driveMinutes)
        assertEquals(now + 240 * min - 40 * min - 5 * min, legs[0].leaveByMs)
        assertEquals(2, travel.calls.size)
        assertEquals(now + 240 * min - 20 * min - 5 * min, travel.calls[1])
    }

    @Test fun skipsAllDayPastVirtualAndLocationless() = runBlocking {
        val events = listOf(
            ev(1, 60, null), ev(2, 60, "https://meet.google.com/abc"), ev(3, 60, "Home", allDay = true),
            ev(4, -10, "Past place"), ev(5, 60, "  "),
        )
        assertTrue(LeavePlanner(FakeTravel(600), origin).plan(events, now).isEmpty())
    }

    @Test fun noOriginMeansNoLegs() = runBlocking {
        assertTrue(LeavePlanner(FakeTravel(600), { null }).plan(listOf(ev(1, 60, "Gym")), now).isEmpty())
    }

    @Test fun capsNumberOfEvents() = runBlocking {
        val events = (1..10L).map { ev(it, it * 30, "Place $it") }
        assertEquals(4, LeavePlanner(FakeTravel(600), origin).plan(events, now).size)
    }

    @Test fun parsesRoutesDurations() {
        assertEquals(1500, RoutesParsing.parseDurationSeconds("1500s"))
        assertEquals(90, RoutesParsing.parseDurationSeconds("90.7s"))
        assertNull(RoutesParsing.parseDurationSeconds(null))
        assertNull(RoutesParsing.parseDurationSeconds("abc"))
    }

    @Test fun virtualLocationsAreNotPlaces() {
        assertFalse(LeaveMath.isPhysicalPlace("Zoom.us/j/123"))
        assertTrue(LeaveMath.isPhysicalPlace("Room 222"))
    }

    @Test fun allDayEventsAreReanchoredToLocalMidnight() {
        val zone = ZoneId.of("America/Chicago")
        val utcMidnight = LocalDate.of(2026, 10, 28).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        val e = CalendarEvent.normalize(1, "x", utcMidnight, utcMidnight + 86_400_000, true, null, null, zone)
        assertEquals(LocalDate.of(2026, 10, 28), e.startDate(zone))
        assertEquals(LocalDate.of(2026, 10, 28), e.endDate(zone))
    }
}
