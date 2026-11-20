package app.mido.ui

import app.mido.calendar.DriveLeg
import app.mido.data.CalendarEvent
import app.mido.data.TaskEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId

class TimelineBuilderTest {
    private val zone = ZoneId.of("America/Chicago")
    private val now = LocalDateTime.of(2026, 10, 27, 12, 0)

    private fun ms(dt: LocalDateTime) = dt.atZone(zone).toInstant().toEpochMilli()

    private fun task(id: Long, due: LocalDateTime?, allDay: Boolean = false, completed: Boolean = false, completedAt: LocalDateTime? = null) =
        TaskEntity(
            id = id, title = "t$id", dueAt = due?.let(::ms), allDay = allDay,
            completed = completed, completedAt = completedAt?.let(::ms), createdAt = id,
        )

    private fun shape(entries: List<TimelineEntry>) = entries.map {
        when (it) {
            is TimelineEntry.Header -> "H:${it.label}"
            is TimelineEntry.Item -> "T:${it.task.id}${if (it.overdue) "!" else ""}"
            is TimelineEntry.Event -> "E:${it.event.id}"
            is TimelineEntry.Drive -> "D:${it.leg.eventKey.substringBefore('@')}"
            is TimelineEntry.Habit -> "B:${it.day.habit.name}"
            is TimelineEntry.Now -> "NOW"
        }
    }

    @Test fun nowMarkerSitsBetweenPastAndFutureTasks() {
        val out = TimelineBuilder.build(
            listOf(task(1, now.minusHours(2)), task(2, now.plusHours(3))), now, zone,
        )
        assertEquals(listOf("H:Today", "T:1", "NOW", "T:2"), shape(out))
    }

    @Test fun nowMarkerAtEndWhenEverythingIsPast() {
        val out = TimelineBuilder.build(listOf(task(1, now.minusHours(2))), now, zone)
        assertEquals(listOf("H:Today", "T:1", "NOW"), shape(out))
    }

    @Test fun emptyListStillShowsToday() {
        assertEquals(listOf("H:Today", "NOW"), shape(TimelineBuilder.build(emptyList(), now, zone)))
    }

    @Test fun overdueGoesFirstAndFutureDaysGetHeaders() {
        val out = TimelineBuilder.build(
            listOf(task(1, now.minusDays(2)), task(2, now.plusDays(1)), task(3, now.plusDays(3))), now, zone,
        )
        assertEquals(
            listOf("H:Overdue", "T:1!", "H:Today", "NOW", "H:Tomorrow", "T:2", "H:Friday, Oct 30", "T:3"),
            shape(out),
        )
    }

    @Test fun completedFromYesterdayIsHidden() {
        val out = TimelineBuilder.build(
            listOf(task(1, now.minusDays(1), completed = true, completedAt = now.minusDays(1))), now, zone,
        )
        assertEquals(listOf("H:Today", "NOW"), shape(out))
    }

    @Test fun undatedTasksGoUnderAnytime() {
        val out = TimelineBuilder.build(listOf(task(1, null)), now, zone)
        assertEquals(listOf("H:Today", "NOW", "H:Anytime", "T:1"), shape(out))
    }

    @Test fun nextUpIsNearestTimedTaskLaterToday() {
        val next = TimelineBuilder.nextUp(
            listOf(task(1, now.plusHours(3)), task(2, now.plusMinutes(20)), task(3, now.plusDays(1))), now, zone,
        )
        assertEquals("t2", next!!.title)
        assertEquals(20L, next.minutesAway)
        assertTrue(TimelineBuilder.nextUp(emptyList(), now, zone) == null)
    }

    private fun event(id: Long, start: LocalDateTime, mins: Long = 60, loc: String? = null) =
        CalendarEvent(id, "ev$id", ms(start), ms(start.plusMinutes(mins)), false, loc)

    @Test fun eventsAndTasksInterleaveByTimeWithDriveBeforeEvent() {
        val e = event(7, now.plusHours(4), loc = "Room 222")
        val leg = DriveLeg(e.key, e.title, "Room 222", e.startMs, ms(now.plusHours(3).plusMinutes(30)), 25)
        val out = TimelineBuilder.build(
            listOf(task(1, now.plusHours(2)), task(2, now.plusHours(5))), now, zone, listOf(e), listOf(leg),
        )
        assertEquals(listOf("H:Today", "NOW", "T:1", "D:7", "E:7", "T:2"), shape(out))
    }

    @Test fun allDayEventSitsAtTopOfItsDay() {
        val day = now.toLocalDate()
        val allDay = CalendarEvent(9, "bday", ms(day.atStartOfDay()), ms(day.plusDays(1).atStartOfDay()), true)
        val out = TimelineBuilder.build(listOf(task(1, now.minusHours(1))), now, zone, listOf(allDay))
        assertEquals(listOf("H:Today", "E:9", "T:1", "NOW"), shape(out))
    }

    @Test fun multiDayAllDayEventShowsOnEachDay() {
        val d = now.toLocalDate()
        val trip = CalendarEvent(5, "trip", ms(d.atStartOfDay()), ms(d.plusDays(2).atStartOfDay()), true)
        val out = TimelineBuilder.build(emptyList(), now, zone, listOf(trip))
        assertEquals(listOf("H:Today", "E:5", "NOW", "H:Tomorrow", "E:5"), shape(out))
    }

    @Test fun yesterdaysEventsAreDropped() {
        val out = TimelineBuilder.build(emptyList(), now, zone, listOf(event(3, now.minusDays(1))))
        assertEquals(listOf("H:Today", "NOW"), shape(out))
    }

    @Test fun nextUpPicksEventAndAttachesLeaveBy() {
        val e = event(7, now.plusMinutes(90), loc = "HQ")
        val leg = DriveLeg(e.key, e.title, "HQ", e.startMs, ms(now.plusMinutes(60)), 25)
        val next = TimelineBuilder.nextUp(listOf(task(1, now.plusHours(3))), now, zone, listOf(e), listOf(leg))!!
        assertEquals("ev7", next.title)
        assertEquals(leg, next.leave)
        assertEquals(90L, next.minutesAway)
    }

    @Test fun pinnedUndatedTasksSitAtTopOfToday() {
        val pinned = task(5, null).copy(pinned = true)
        val out = TimelineBuilder.build(
            listOf(task(1, now.minusHours(2)), task(2, now.plusHours(3)), pinned, task(6, null)), now, zone,
        )
        assertEquals(listOf("H:Today", "T:5", "T:1", "NOW", "T:2", "H:Anytime", "T:6"), shape(out))
    }

    @Test fun pinnedTaskAppearsOnTodayEvenWithNothingElse() {
        val out = TimelineBuilder.build(listOf(task(5, null).copy(pinned = true)), now, zone)
        assertEquals(listOf("H:Today", "T:5", "NOW"), shape(out))
    }

    @Test fun completedPinnedTaskStopsBeingPinned() {
        val done = task(5, null, completed = true, completedAt = now.minusHours(1)).copy(pinned = true)
        val out = TimelineBuilder.build(listOf(done), now, zone)
        assertEquals(listOf("H:Today", "NOW", "H:Anytime", "T:5"), shape(out))
    }

    @Test fun widgetTimelineIncludesPinnedTasks() {
        val out = WidgetTimeline.build(listOf(task(5, null).copy(pinned = true), task(6, null)), emptyList(), emptyList(), now, zone)
        assertEquals(listOf("H:Today", "T:5", "NOW"), shape(out))
    }
}
