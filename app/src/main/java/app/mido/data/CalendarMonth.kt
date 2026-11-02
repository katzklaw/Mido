package app.mido.data

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth

data class DayCounts(val tasks: Int = 0, val events: Int = 0)

sealed interface AgendaItem {
    /** [projected] is true for a future repeat of a repeating task (not its stored due date). */
    data class Task(val task: TaskEntity, val projected: Boolean = false) : AgendaItem
    data class Event(val event: CalendarEvent) : AgendaItem
}

/** Pure helpers behind the month calendar screen. */
object CalendarMonth {
    /** Six full weeks covering [month], starting on [weekStart]. */
    fun gridDates(month: YearMonth, weekStart: DayOfWeek = DayOfWeek.SUNDAY): List<LocalDate> {
        val first = month.atDay(1)
        val back = ((first.dayOfWeek.value - weekStart.value) + 7) % 7
        val start = first.minusDays(back.toLong())
        return (0 until 42).map { start.plusDays(it.toLong()) }
    }
}
