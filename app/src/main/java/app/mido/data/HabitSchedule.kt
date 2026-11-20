package app.mido.data

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.TextStyle
import java.time.temporal.TemporalAdjusters
import java.util.Locale

/** A habit on a particular day, as shown in the timeline. */
data class HabitDay(
    val habit: HabitEntity,
    val date: LocalDate,
    val checked: Boolean,
    /** Only today can be ticked from the timeline. */
    val canCheck: Boolean,
    /** Extra text such as "2/3 this week". */
    val note: String? = null,
)

/** Which habits are due when, and how to describe them. Pure logic, no Android. */
object HabitSchedule {
    const val ALL_DAYS = 127

    fun bit(day: DayOfWeek): Int = 1 shl (day.value - 1)

    fun maskOf(days: Collection<DayOfWeek>): Int = days.fold(0) { m, d -> m or bit(d) }

    fun isWeekly(h: HabitEntity) = h.weeklyTarget > 0

    fun isDaily(h: HabitEntity) = !isWeekly(h) && (h.daysMask and ALL_DAYS) == ALL_DAYS

    /** Whether [date] is one of the habit's days. Weekly-target habits count every day as possible. */
    fun scheduledOn(h: HabitEntity, date: LocalDate): Boolean =
        isWeekly(h) || (h.daysMask and bit(date.dayOfWeek)) != 0

    fun weekDates(date: LocalDate, first: DayOfWeek): List<LocalDate> {
        val start = date.with(TemporalAdjusters.previousOrSame(first))
        return (0..6).map { start.plusDays(it.toLong()) }
    }

    fun doneThisWeek(checkedDays: Set<Long>, date: LocalDate, first: DayOfWeek): Int =
        weekDates(date, first).count { it.toEpochDay() in checkedDays }

    fun describe(h: HabitEntity, locale: Locale = Locale.getDefault()): String = when {
        isWeekly(h) -> "${h.weeklyTarget}× a week"
        isDaily(h) -> "Every day"
        else -> DayOfWeek.entries.filter { (h.daysMask and bit(it)) != 0 }
            .joinToString(", ") { it.getDisplayName(TextStyle.SHORT, locale) }
    }

    /**
     * What the timeline shows. Daily habits and "N times a week" habits show on today only (the latter until the
     * week's target is met, and still today once ticked). Habits tied to certain days show on each of those days.
     */
    fun timelineDays(
        habits: List<HabitEntity>,
        checks: Map<Long, Set<Long>>,
        today: LocalDate,
        first: DayOfWeek,
        daysAhead: Int = 13,
    ): List<HabitDay> {
        val out = mutableListOf<HabitDay>()
        for (h in habits) {
            val done = checks[h.id].orEmpty()
            val tickedToday = today.toEpochDay() in done
            when {
                isWeekly(h) -> {
                    val count = doneThisWeek(done, today, first)
                    if (count < h.weeklyTarget || tickedToday) {
                        out += HabitDay(h, today, tickedToday, true, "$count/${h.weeklyTarget} this week")
                    }
                }
                isDaily(h) -> out += HabitDay(h, today, tickedToday, true)
                else -> for (offset in 0..daysAhead) {
                    val date = today.plusDays(offset.toLong())
                    if (scheduledOn(h, date)) {
                        out += HabitDay(h, date, date.toEpochDay() in done, date == today)
                    }
                }
            }
        }
        return out
    }
}
