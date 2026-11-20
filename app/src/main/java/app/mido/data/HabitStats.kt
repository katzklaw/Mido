package app.mido.data

import java.time.DayOfWeek
import java.time.LocalDate

/** [unit] is "days" or "weeks". [done] of [possible] counts the recent window (30 days, or the last 4 weeks). */
data class HabitStats(
    val unit: String,
    val done: Int,
    val possible: Int,
    val currentStreak: Int,
    val bestStreak: Int,
) {
    val percent: Int? get() = if (possible == 0) null else (done * 100 + possible / 2) / possible
}

/** Completion rate and streaks. Pure logic. Today and the current week never count against you until they're over. */
object HabitStatsWindow {
    const val DAY_WINDOW = 30
    const val WEEK_WINDOW = 4
}

fun habitStats(h: HabitEntity, checks: Set<Long>, today: LocalDate, first: DayOfWeek, created: LocalDate): HabitStats {
    val earliestTick = checks.minOrNull()?.let { LocalDate.ofEpochDay(it) }
    val start = listOfNotNull(created, earliestTick).min().let { if (it.isAfter(today)) today else it }
    return if (HabitSchedule.isWeekly(h)) weeklyStats(h, checks, today, first, start) else dailyStats(h, checks, today, start)
}

private fun dailyStats(h: HabitEntity, checks: Set<Long>, today: LocalDate, start: LocalDate): HabitStats {
    val windowStart = maxOf(start, today.minusDays((HabitStatsWindow.DAY_WINDOW - 1).toLong()))
    var run = 0
    var best = 0
    var done = 0
    var possible = 0
    var d = start
    while (!d.isAfter(today)) {
        if (HabitSchedule.scheduledOn(h, d)) {
            val ticked = d.toEpochDay() in checks
            when {
                ticked -> {
                    run++
                    best = maxOf(best, run)
                    if (!d.isBefore(windowStart)) { done++; possible++ }
                }
                d == today -> {} // still time
                else -> {
                    run = 0
                    if (!d.isBefore(windowStart)) possible++
                }
            }
        }
        d = d.plusDays(1)
    }
    return HabitStats("days", done, possible, run, best)
}

private fun weeklyStats(h: HabitEntity, checks: Set<Long>, today: LocalDate, first: DayOfWeek, start: LocalDate): HabitStats {
    val thisWeek = HabitSchedule.weekDates(today, first).first()
    var weekStart = HabitSchedule.weekDates(start, first).first()
    val windowStart = thisWeek.minusWeeks((HabitStatsWindow.WEEK_WINDOW - 1).toLong())
    var run = 0
    var best = 0
    var done = 0
    var possible = 0
    while (!weekStart.isAfter(thisWeek)) {
        val count = (0..6).count { weekStart.plusDays(it.toLong()).toEpochDay() in checks }
        val met = count >= h.weeklyTarget
        when {
            met -> {
                run++
                best = maxOf(best, run)
                if (!weekStart.isBefore(windowStart)) { done++; possible++ }
            }
            weekStart == thisWeek -> {}
            else -> {
                run = 0
                if (!weekStart.isBefore(windowStart)) possible++
            }
        }
        weekStart = weekStart.plusWeeks(1)
    }
    return HabitStats("weeks", done, possible, run, best)
}
