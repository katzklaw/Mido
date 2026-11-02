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
}
