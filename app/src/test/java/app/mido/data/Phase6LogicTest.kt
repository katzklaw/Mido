package app.mido.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

class Phase6LogicTest {
    private val zone = ZoneId.of("America/Chicago")
    private fun ms(dt: LocalDateTime) = dt.atZone(zone).toInstant().toEpochMilli()
    private val oct27 = LocalDate.of(2026, 10, 27)

    // ---- calendar view ----
    @Test fun repeatingTaskIsProjectedAcrossTheMonth() {
        val daily = TaskEntity(id = 1, title = "x", dueAt = ms(oct27.atTime(9, 0)), repeatRule = RepeatRule(RepeatRule.Freq.DAILY).encode())
        val dates = Occurrences.dates(daily, oct27, oct27.plusDays(4), zone)
        assertEquals(5, dates.size)
        assertEquals(oct27.plusDays(4), dates.last())
    }

    @Test fun weeklyOnDaysProjectsOnlyThoseDays() {
        val rule = RepeatRule(RepeatRule.Freq.WEEKLY, 1, setOf(DayOfWeek.TUESDAY, DayOfWeek.THURSDAY))
        val t = TaskEntity(id = 1, title = "x", dueAt = ms(oct27.atTime(19, 0)), repeatRule = rule.encode()) // Oct 27 2026 is a Tuesday
        val dates = Occurrences.dates(t, oct27, oct27.plusDays(7), zone)
        assertEquals(listOf(oct27, oct27.plusDays(2), oct27.plusDays(7)), dates)
    }

    @Test fun completedAndUndatedTasksAreNotProjected() {
        val done = TaskEntity(id = 1, title = "x", dueAt = ms(oct27.atTime(9, 0)), completed = true, repeatRule = RepeatRule(RepeatRule.Freq.DAILY).encode())
        assertEquals(listOf(oct27), Occurrences.dates(done, oct27, oct27.plusDays(5), zone))
        assertTrue(Occurrences.dates(TaskEntity(id = 2, title = "y"), oct27, oct27.plusDays(5), zone).isEmpty())
    }

    @Test fun dayAgendaSortsAllDayFirstThenByTime() {
        val late = TaskEntity(id = 1, title = "late", dueAt = ms(oct27.atTime(18, 0)))
        val early = TaskEntity(id = 2, title = "early", dueAt = ms(oct27.atTime(8, 30)))
        val allDayTask = TaskEntity(id = 3, title = "ad", dueAt = ms(oct27.atStartOfDay()), allDay = true)
        val ev = CalendarEvent(9, "meeting", ms(oct27.atTime(12, 0)), ms(oct27.atTime(13, 0)), false)
        val other = TaskEntity(id = 4, title = "other day", dueAt = ms(oct27.plusDays(1).atTime(8, 0)))
        val items = DayAgenda.forDay(oct27, listOf(late, early, allDayTask, other), listOf(ev), zone)
        val labels = items.map { when (it) { is AgendaItem.Task -> it.task.title; is AgendaItem.Event -> it.event.title } }
        assertEquals(listOf("ad", "early", "meeting", "late"), labels)
    }

    @Test fun projectedRepeatIsFlagged() {
        val t = TaskEntity(id = 1, title = "x", dueAt = ms(oct27.atTime(9, 0)), repeatRule = RepeatRule(RepeatRule.Freq.DAILY).encode())
        val later = DayAgenda.forDay(oct27.plusDays(2), listOf(t), emptyList(), zone).single() as AgendaItem.Task
        assertTrue(later.projected)
        val first = DayAgenda.forDay(oct27, listOf(t), emptyList(), zone).single() as AgendaItem.Task
        assertEquals(false, first.projected)
    }

    @Test fun countsCoverMultiDayEvents() {
        val trip = CalendarEvent(5, "trip", ms(oct27.atStartOfDay()), ms(oct27.plusDays(3).atStartOfDay()), true)
        val c = DayAgenda.counts(oct27, oct27.plusDays(10), emptyList(), listOf(trip), zone)
        assertEquals(DayCounts(0, 1), c[oct27.plusDays(2)])
        assertNull(c[oct27.plusDays(3)])
    }
}
