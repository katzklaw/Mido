package app.mido.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId

class ReminderTimeTest {
    private val zone = ZoneId.of("America/Chicago")
    private fun ms(dt: LocalDateTime) = dt.atZone(zone).toInstant().toEpochMilli()
    private val due = LocalDateTime.of(2026, 10, 28, 15, 30)

    @Test fun timedTaskFiresAtDueTime() {
        assertEquals(ms(due), ReminderTime.triggerAt(TaskEntity(title = "x", dueAt = ms(due)), zone))
    }

    @Test fun allDayTaskFiresAtNineAm() {
        val t = TaskEntity(title = "x", dueAt = ms(due.toLocalDate().atStartOfDay()), allDay = true)
        assertEquals(ms(LocalDateTime.of(2026, 10, 28, 9, 0)), ReminderTime.triggerAt(t, zone))
    }

    @Test fun noDueOrCompletedMeansNoReminder() {
        assertNull(ReminderTime.triggerAt(TaskEntity(title = "x"), zone))
        assertNull(ReminderTime.triggerAt(TaskEntity(title = "x", dueAt = ms(due), completed = true), zone))
    }

    @Test fun snoozedReminderRingsAtTheSnoozeTime() {
        val due = LocalDateTime.of(2026, 10, 28, 8, 0)
        val snooze = ms(due.plusMinutes(10))
        val t = TaskEntity(title = "x", dueAt = ms(due), snoozedUntil = snooze)
        assertEquals(snooze, ReminderTime.triggerAt(t, zone))
        assertNull(ReminderTime.triggerAt(t.copy(completed = true), zone))
    }

    @Test fun snoozeThatNeverFiredIsReportedMissed() {
        val due = LocalDateTime.of(2026, 10, 28, 8, 0)
        val t = TaskEntity(
            title = "x", dueAt = ms(due), createdAt = 1L,
            notifiedAt = ms(due), snoozedUntil = ms(due.plusMinutes(10)),
        )
        // Original fired at 8:00, snooze due 8:10 but the alarm was lost; at 8:30 it should show up as missed.
        assertEquals(listOf(t), MissedReminders.find(listOf(t), ms(due.plusMinutes(30)), zone))
        // Right after the snooze time it's still within grace.
        assertEquals(emptyList<TaskEntity>(), MissedReminders.find(listOf(t), ms(due.plusMinutes(11)), zone))
    }
}
