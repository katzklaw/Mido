package app.mido.ui

import app.mido.calendar.Countdown
import app.mido.calendar.DriveLeg
import app.mido.data.CalendarEvent
import app.mido.data.TaskEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId

class WidgetAndCountdownTest {
    private val zone = ZoneId.of("America/Chicago")
    private val now = LocalDateTime.of(2026, 10, 28, 12, 0)
    private fun ms(dt: LocalDateTime) = dt.atZone(zone).toInstant().toEpochMilli()
    private val nowMs = ms(now)

    private fun task(id: Long, due: LocalDateTime?) = TaskEntity(id = id, title = "t$id", dueAt = due?.let(::ms))

    // ---- widget range ----
    @Test fun widgetShowsTwoWeeksNotAnytimeNotLater() {
        val tasks = listOf(
            task(1, now.minusDays(1)), task(2, now.plusDays(10)), task(3, now.plusDays(14)),
            task(4, now.plusDays(20)), task(5, null),
        )
        val out = WidgetTimeline.build(tasks, emptyList(), emptyList(), now, zone)
        val ids = out.filterIsInstance<TimelineEntry.Item>().map { it.task.id }
        assertEquals(listOf(1L, 2L, 3L), ids)
        assertFalse(out.any { it is TimelineEntry.Header && it.label == "Anytime" })
    }

    @Test fun widgetKeepsEventsAndDrivesInRangeAndTheNowMarker() {
        val near = CalendarEvent(1, "near", ms(now.plusDays(5)), ms(now.plusDays(5).plusHours(1)), false)
        val far = CalendarEvent(2, "far", ms(now.plusDays(30)), ms(now.plusDays(30).plusHours(1)), false)
        val out = WidgetTimeline.build(emptyList(), listOf(near, far), emptyList(), now, zone)
        assertEquals(listOf(1L), out.filterIsInstance<TimelineEntry.Event>().map { it.event.id })
        assertTrue(out.any { it is TimelineEntry.Now })
    }

    // ---- next up wording ----
    @Test fun nextUpHeadlineForPlainTaskAndEmpty() {
        val empty = NextUpText.of(null, now, zone)
        assertEquals("Nothing else scheduled today", empty.headline)
        val n = NextUp("Dentist", ms(now.plusMinutes(20)), "Smile Dental", 20)
        val line = NextUpText.of(n, now, zone)
        assertEquals("“Dentist” starts in 20 mins", line.headline)
        assertEquals("📍 Smile Dental", line.sub)
    }

    @Test fun nextUpHeadlineCountsDownToLeaveTime() {
        val start = now.plusMinutes(90)
        val leg = DriveLeg("k", "Design Review", "Room 222", ms(start), ms(now.plusMinutes(39)), 46)
        val line = NextUpText.of(NextUp("Design Review", ms(start), "Room 222", 90, leg), now, zone)
        assertEquals("Leave for “Design Review” in 39 mins", line.headline)
        assertTrue(line.sub.startsWith("🚗 46 min drive · leave by 12:39 PM · starts 1:30 PM"))
    }

    @Test fun nextUpSaysLeaveNowOncePastLeaveBy() {
        val leg = DriveLeg("k", "Gym", "Gym", ms(now.plusMinutes(20)), ms(now.minusMinutes(1)), 15)
        val line = NextUpText.of(NextUp("Gym", ms(now.plusMinutes(20)), "Gym", 20, leg), now, zone)
        assertEquals("Leave now for “Gym”", line.headline)
    }

    // ---- countdown ----
    private fun leg(startOffsetMin: Long, leaveOffsetMin: Long) =
        DriveLeg("k$startOffsetMin", "e", "loc", nowMs + startOffsetMin * 60_000, nowMs + leaveOffsetMin * 60_000, 20)

    @Test fun countdownPicksSoonestEventOnceLeaveByIsWithinThreeHours() {
        val far = leg(600, 580)       // leave in 9h 40m: too early
        val soon = leg(120, 95)       // leave in 95 min
        val sooner = leg(60, 40)
        assertNull(Countdown.pick(listOf(far), nowMs))
        assertEquals(sooner, Countdown.pick(listOf(far, soon, sooner), nowMs))
    }

    @Test fun countdownIgnoresStartedEventsAndSwitchesTargetAtLeaveBy() {
        assertNull(Countdown.pick(listOf(leg(-5, -25)), nowMs))
        val l = leg(30, 10)
        assertEquals(Countdown.Target(false, l.leaveByMs), Countdown.target(l, nowMs))
        assertEquals(Countdown.Target(true, l.startMs), Countdown.target(l, l.leaveByMs + 1))
    }
}
