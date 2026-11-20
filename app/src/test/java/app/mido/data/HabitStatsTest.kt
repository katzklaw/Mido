package app.mido.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate

class HabitStatsTest {
    private val today = LocalDate.of(2026, 11, 7) // Saturday
    private val sunday = DayOfWeek.SUNDAY
    private val daily = HabitEntity(id = 1, name = "Vitamins")
    private fun days(vararg back: Int) = back.map { today.minusDays(it.toLong()).toEpochDay() }.toSet()

    @Test fun streakCountsBackFromTodayOrYesterday() {
        val s = habitStats(daily, days(1, 2, 3), today, sunday, today.minusDays(10))
        assertEquals(3, s.currentStreak) // today not ticked yet doesn't break it
        val t = habitStats(daily, days(0, 1, 2, 3), today, sunday, today.minusDays(10))
        assertEquals(4, t.currentStreak)
    }

    @Test fun missedDayBreaksStreakAndBestIsKept() {
        // ticked 9,8,7,6,5 ago, missed 4,3, ticked 2,1
        val s = habitStats(daily, days(9, 8, 7, 6, 5, 2, 1), today, sunday, today.minusDays(9))
        assertEquals(2, s.currentStreak)
        assertEquals(5, s.bestStreak)
    }

    @Test fun rateOnlyCountsDaysSinceCreationAndSkipsUntickedToday() {
        val s = habitStats(daily, days(1, 2), today, sunday, today.minusDays(3))
        // created 3 days ago: days -3..-1 count (3), plus today excluded; ticked 2 of them
        assertEquals(2, s.done)
        assertEquals(3, s.possible)
        assertEquals(67, s.percent)
    }

    @Test fun newHabitHasNoPercentYet() {
        val s = habitStats(daily, emptySet(), today, sunday, today)
        assertNull(s.percent)
        assertEquals(0, s.currentStreak)
    }

    @Test fun onlyScheduledDaysCount() {
        // Only Mondays and Wednesdays; created long ago
        val mw = HabitEntity(id = 2, name = "Gym", daysMask = HabitSchedule.maskOf(listOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY)))
        val ticks = setOf(LocalDate.of(2026, 11, 4), LocalDate.of(2026, 11, 2), LocalDate.of(2026, 10, 28)).map { it.toEpochDay() }.toSet()
        val s = habitStats(mw, ticks, today, sunday, LocalDate.of(2026, 10, 26))
        // Mondays/Wednesdays Oct 26..Nov 7: Oct 26(M, missed), 28(W), Nov 2(M), 4(W)
        assertEquals(3, s.done)
        assertEquals(4, s.possible)
        assertEquals(3, s.currentStreak)
        assertEquals(3, s.bestStreak)
    }

    @Test fun weeklyHabitCountsWeeksThatHitTheTarget() {
        val w = HabitEntity(id = 3, name = "Walk", weeklyTarget = 2)
        // Weeks start Sunday. This week = Nov 1..7. Last week = Oct 25..31 (met), week before Oct 18..24 (missed)
        val ticks = setOf(LocalDate.of(2026, 10, 26), LocalDate.of(2026, 10, 28), LocalDate.of(2026, 10, 20)).map { it.toEpochDay() }.toSet()
        val s = habitStats(w, ticks, today, sunday, LocalDate.of(2026, 10, 18))
        assertEquals("weeks", s.unit)
        assertEquals(1, s.done)
        assertEquals(2, s.possible) // current week still open, not counted
        assertEquals(1, s.currentStreak)
        assertEquals(1, s.bestStreak)
    }

    @Test fun hittingTheWeeklyTargetThisWeekExtendsTheStreak() {
        val w = HabitEntity(id = 3, name = "Walk", weeklyTarget = 2)
        val ticks = setOf(LocalDate.of(2026, 10, 26), LocalDate.of(2026, 10, 28), LocalDate.of(2026, 11, 2), LocalDate.of(2026, 11, 3)).map { it.toEpochDay() }.toSet()
        val s = habitStats(w, ticks, today, sunday, LocalDate.of(2026, 10, 25))
        assertEquals(2, s.currentStreak)
    }
}
