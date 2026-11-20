package app.mido.data

import app.mido.ui.TimelineBuilder
import app.mido.ui.TimelineEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.Locale

class HabitScheduleTest {
    private val sunday = DayOfWeek.SUNDAY
    private val monday = DayOfWeek.MONDAY
    private val wed = LocalDate.of(2026, 11, 4) // a Wednesday
    private val daily = HabitEntity(id = 1, name = "Vitamins")
    private val mwf = HabitEntity(id = 2, name = "Gym", daysMask = HabitSchedule.maskOf(listOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY)))
    private val threeAWeek = HabitEntity(id = 3, name = "Walk", weeklyTarget = 3)

    @Test fun maskAndDescribe() {
        assertEquals(127, HabitSchedule.maskOf(DayOfWeek.entries))
        assertTrue(HabitSchedule.isDaily(daily))
        assertFalse(HabitSchedule.isDaily(mwf))
        assertEquals("Every day", HabitSchedule.describe(daily, Locale.US))
        assertEquals("Mon, Wed, Fri", HabitSchedule.describe(mwf, Locale.US))
        assertEquals("3× a week", HabitSchedule.describe(threeAWeek, Locale.US))
    }

    @Test fun weekDatesStartOnTheFirstDay() {
        val sun = HabitSchedule.weekDates(wed, sunday)
        assertEquals(LocalDate.of(2026, 11, 1), sun.first())
        assertEquals(LocalDate.of(2026, 11, 7), sun.last())
        val mon = HabitSchedule.weekDates(wed, monday)
        assertEquals(LocalDate.of(2026, 11, 2), mon.first())
        assertEquals(LocalDate.of(2026, 11, 8), mon.last())
        // A Sunday belongs to the week that started the previous Monday.
        assertEquals(LocalDate.of(2026, 11, 2), HabitSchedule.weekDates(LocalDate.of(2026, 11, 8), monday).first())
    }

    @Test fun dailyShowsTodayOnlyAndReflectsTick() {
        val days = HabitSchedule.timelineDays(listOf(daily), mapOf(1L to setOf(wed.toEpochDay())), wed, sunday)
        assertEquals(1, days.size)
        assertTrue(days[0].checked)
        assertTrue(days[0].canCheck)
    }

    @Test fun specificDaysShowOnEachMatchingDay() {
        val days = HabitSchedule.timelineDays(listOf(mwf), emptyMap(), wed, sunday, daysAhead = 13)
        // Wed Nov 4 through Tue Nov 17: Wed, Fri, Mon, Wed, Fri, Mon -> Nov 4, 6, 9, 11, 13, 16
        assertEquals(listOf(4, 6, 9, 11, 13, 16), days.map { it.date.dayOfMonth })
        assertTrue(days[0].canCheck)
        assertFalse(days[1].canCheck)
    }

    @Test fun weeklyTargetShowsUntilMetThenOnlyIfTickedToday() {
        val twoDone = setOf(LocalDate.of(2026, 11, 2).toEpochDay(), LocalDate.of(2026, 11, 3).toEpochDay())
        val open = HabitSchedule.timelineDays(listOf(threeAWeek), mapOf(3L to twoDone), wed, sunday)
        assertEquals("2/3 this week", open.single().note)
        assertFalse(open.single().checked)

        val metEarlier = twoDone + LocalDate.of(2026, 11, 1).toEpochDay()
        assertTrue(HabitSchedule.timelineDays(listOf(threeAWeek), mapOf(3L to metEarlier), wed, sunday).isEmpty())

        val metToday = twoDone + wed.toEpochDay()
        val shown = HabitSchedule.timelineDays(listOf(threeAWeek), mapOf(3L to metToday), wed, sunday).single()
        assertTrue(shown.checked)
        assertEquals("3/3 this week", shown.note)
    }

    @Test fun lastWeeksTicksDontCountThisWeek() {
        val lastWeek = setOf(LocalDate.of(2026, 10, 27).toEpochDay(), LocalDate.of(2026, 10, 28).toEpochDay(), LocalDate.of(2026, 10, 29).toEpochDay())
        assertEquals(0, HabitSchedule.doneThisWeek(lastWeek, wed, sunday))
    }

    @Test fun timelinePutsHabitsAtTheTopOfTheirDay() {
        val now = LocalDateTime.of(2026, 11, 4, 10, 0)
        val zone = ZoneId.of("America/Chicago")
        val habitDays = HabitSchedule.timelineDays(listOf(daily, mwf), emptyMap(), now.toLocalDate(), sunday)
        val out = TimelineBuilder.build(emptyList(), now, zone, habitDays = habitDays)
        val shape = out.map {
            when (it) {
                is TimelineEntry.Header -> "H:${it.label}"
                is TimelineEntry.Habit -> "B:${it.day.habit.name}"
                is TimelineEntry.Now -> "NOW"
                else -> "?"
            }
        }
        assertEquals(listOf("H:Today", "B:Vitamins", "B:Gym", "NOW"), shape.take(4))
        // Friday's Gym gets its own day header.
        assertTrue("H:Friday, Nov 6" in shape)
        val fri = shape.indexOf("H:Friday, Nov 6")
        assertEquals("B:Gym", shape[fri + 1])
    }
}
