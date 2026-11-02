package app.mido.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset

class CalendarSyncTest {
    private val zone = ZoneId.of("America/Chicago")
    private fun ms(dt: LocalDateTime) = dt.atZone(zone).toInstant().toEpochMilli()
    private val due = LocalDateTime.of(2026, 10, 28, 15, 30)

    @Test fun timedTaskBecomesThirtyMinuteEvent() {
        val spec = CalendarEventSpec.from(TaskEntity(title = "Dentist", dueAt = ms(due), notes = " bring card ", locationName = "Smile Dental"), zone)!!
        assertEquals(ms(due), spec.startMs)
        assertEquals(ms(due) + 30 * 60_000L, spec.endMs)
        assertNull(spec.duration)
        assertNull(spec.rrule)
        assertFalse(spec.allDay)
        assertEquals("America/Chicago", spec.timeZoneId)
        assertEquals("bring card", spec.description)
        assertEquals("Smile Dental", spec.location)
    }

    @Test fun resolvedAddressWinsOverTypedName() {
        val spec = CalendarEventSpec.from(
            TaskEntity(title = "x", dueAt = ms(due), locationName = "Gym", locationAddress = "1 Main St"), zone,
        )!!
        assertEquals("1 Main St", spec.location)
    }

    @Test fun allDayTaskUsesUtcMidnightsAndOneDayEnd() {
        val day = due.toLocalDate()
        val spec = CalendarEventSpec.from(TaskEntity(title = "x", dueAt = ms(day.atStartOfDay()), allDay = true), zone)!!
        val utcMidnight = day.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        assertEquals(utcMidnight, spec.startMs)
        assertEquals(utcMidnight + 86_400_000L, spec.endMs)
        assertEquals("UTC", spec.timeZoneId)
        assertTrue(spec.allDay)
    }

    @Test fun repeatingTaskUsesRruleAndDurationNotEnd() {
        val rule = RepeatRule(RepeatRule.Freq.WEEKLY, 2, setOf(DayOfWeek.THURSDAY, DayOfWeek.TUESDAY))
        val spec = CalendarEventSpec.from(TaskEntity(title = "x", dueAt = ms(due), repeatRule = rule.encode()), zone)!!
        assertEquals("FREQ=WEEKLY;INTERVAL=2;BYDAY=TU,TH", spec.rrule)
        assertEquals("PT30M", spec.duration)
        assertNull(spec.endMs)
    }

    @Test fun rruleForSimpleRules() {
        assertEquals("FREQ=DAILY", RepeatRule(RepeatRule.Freq.DAILY).toRrule())
        assertEquals("FREQ=MONTHLY;INTERVAL=3", RepeatRule(RepeatRule.Freq.MONTHLY, 3).toRrule())
        assertEquals("FREQ=WEEKLY;BYDAY=MO,TU,WE,TH,FR", RepeatRule(RepeatRule.Freq.WEEKLY, 1, RepeatRule.WEEKDAYS).toRrule())
    }

    @Test fun undatedTaskHasNoEvent() {
        assertNull(CalendarEventSpec.from(TaskEntity(title = "x"), zone))
    }

    @Test fun presetsKeepCustomRuleAsExtraOption() {
        val custom = RepeatRule(RepeatRule.Freq.WEEKLY, 1, setOf(DayOfWeek.TUESDAY, DayOfWeek.THURSDAY))
        val opts = RepeatPresets.optionsFor(custom)
        assertEquals(RepeatPresets.all.size + 1, opts.size)
        assertEquals(opts.size - 1, RepeatPresets.indexOf(opts, custom))
        assertEquals(0, RepeatPresets.indexOf(RepeatPresets.optionsFor(null), null))
        val weekly = RepeatRule(RepeatRule.Freq.WEEKLY)
        assertEquals(RepeatPresets.all.size, RepeatPresets.optionsFor(weekly).size)
    }

    @Test fun editWithDateAndTimeIsTimed() {
        val e = TaskEdit.apply(TaskEntity(title = "old"), " New ", "", due.toLocalDate(), LocalTime.of(15, 30), null, "", null, zone)
        assertEquals("New", e.title)
        assertEquals(ms(due), e.dueAt)
        assertFalse(e.allDay)
    }

    @Test fun editWithDateOnlyIsAllDayAndClearingDateClearsRepeat() {
        val allDay = TaskEdit.apply(TaskEntity(title = "t"), "t", "", due.toLocalDate(), null, null, "", null, zone)
        assertTrue(allDay.allDay)
        val rule = RepeatRule(RepeatRule.Freq.DAILY)
        val cleared = TaskEdit.apply(TaskEntity(title = "t", repeatRule = rule.encode()), "t", "", null, null, null, "", null, zone)
        assertNull(cleared.dueAt)
        assertNull(cleared.repeatRule)
    }

    @Test fun repeatWithoutDateStartsToday() {
        val today = LocalDate.of(2026, 10, 27)
        val e = TaskEdit.apply(TaskEntity(title = "t"), "t", "", null, null, RepeatRule(RepeatRule.Freq.DAILY), "", null, zone, today)
        assertEquals(ms(today.atStartOfDay()), e.dueAt)
        assertTrue(e.allDay)
        assertEquals("DAILY|1|", e.repeatRule)
    }

    @Test fun blankPlaceDropsTrigger() {
        val e = TaskEdit.apply(TaskEntity(title = "t"), "t", "", null, null, null, "  ", LocationTrigger.ARRIVE, zone)
        assertNull(e.locationName)
        assertNull(e.locationTrigger)
        val every = TaskEdit.apply(TaskEntity(title = "t"), "t", "", null, null, null, "Gym", LocationTrigger.ARRIVE, zone, everyTime = true)
        assertTrue(every.locationRepeat)
        val infoOnly = TaskEdit.apply(TaskEntity(title = "t"), "t", "", null, null, null, "Gym", null, zone, everyTime = true)
        assertFalse(infoOnly.locationRepeat)
        val e2 = TaskEdit.apply(TaskEntity(title = "t"), "t", "", null, null, null, "Gym", LocationTrigger.LEAVE, zone)
        assertEquals("LEAVE", e2.locationTrigger)
    }
}
