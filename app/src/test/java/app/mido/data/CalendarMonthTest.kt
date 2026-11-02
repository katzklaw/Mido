package app.mido.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.ZoneId

class CalendarMonthTest {
    private val zone = ZoneId.of("America/Chicago")
    private fun ms(dt: LocalDateTime) = dt.atZone(zone).toInstant().toEpochMilli()

    @Test fun gridCoversSixWeeksAndWholeMonth() {
        val grid = CalendarMonth.gridDates(YearMonth.of(2026, 10))
        assertEquals(42, grid.size)
        assertEquals(LocalDate.of(2026, 9, 27), grid.first()) // Oct 1 2026 is a Thursday; weeks start Sunday
        assertEquals(DayOfWeek.SUNDAY, grid.first().dayOfWeek)
        assertTrue(grid.containsAll((1..31).map { LocalDate.of(2026, 10, it) }))
        assertEquals(LocalDate.of(2026, 9, 28), CalendarMonth.gridDates(YearMonth.of(2026, 10), DayOfWeek.MONDAY).first())
    }

    @Test fun summaryCountsTasksAndSpansMultiDayEvents() {
        val d = LocalDate.of(2026, 10, 28)
        val tasks = listOf(
            TaskEntity(id = 1, title = "a", dueAt = ms(d.atTime(9, 0))),
            TaskEntity(id = 2, title = "b", dueAt = ms(d.atTime(17, 0))),
            TaskEntity(id = 3, title = "undated"),
        )
        val trip = CalendarEvent(9, "trip", ms(d.atStartOfDay()), ms(d.plusDays(2).atStartOfDay()), true)
        val sum = DayAgenda.counts(d.minusDays(1), d.plusDays(5), tasks, listOf(trip), zone)
        assertEquals(DayCounts(tasks = 2, events = 1), sum[d])
        assertEquals(DayCounts(tasks = 0, events = 1), sum[d.plusDays(1)])
        assertEquals(null, sum[d.plusDays(2)])
    }

    @Test fun agendaOrdersAllDayThenTimeEventsBeforeTasks() {
        val d = LocalDate.of(2026, 10, 28)
        val t = TaskEntity(id = 1, title = "task", dueAt = ms(d.atTime(10, 0)))
        val e = CalendarEvent(2, "event", ms(d.atTime(10, 0)), ms(d.atTime(11, 0)), false)
        val allDay = CalendarEvent(3, "bday", ms(d.atStartOfDay()), ms(d.plusDays(1).atStartOfDay()), true)
        val early = TaskEntity(id = 4, title = "early", dueAt = ms(d.atTime(8, 0)))
        val names = DayAgenda.forDay(d, listOf(t, early), listOf(e, allDay), zone).map {
            when (it) { is AgendaItem.Task -> it.task.title; is AgendaItem.Event -> it.event.title }
        }
        assertEquals(listOf("bday", "early", "event", "task"), names)
    }

    @Test fun missedRemindersOnlyWhenOverdueUnnotifiedAndRecent() {
        val now = 1_000_000_000_000L
        val h = 3_600_000L
        fun t(id: Long, due: Long, notified: Long? = null, created: Long = 0L) =
            TaskEntity(id = id, title = "t$id", dueAt = due, notifiedAt = notified, createdAt = created)
        val tasks = listOf(
            t(1, now - h),                         // missed
            t(2, now - h, notified = now - h),      // already shown
            t(3, now - 10 * h),                     // too old
            t(4, now + h),                          // future
            t(5, now - h, created = now - h / 2),   // created after its own reminder time
            t(6, now - h, notified = now - 3 * h),  // notified for an earlier occurrence
            TaskEntity(id = 7, title = "done", dueAt = now - h, completed = true),
        )
        assertEquals(listOf(1L, 6L), MissedReminders.find(tasks, now, zone = zone).map { it.id })
    }
}
