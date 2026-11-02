package app.mido.data

import java.time.DayOfWeek
import java.time.LocalDateTime

/**
 * A simple repeat rule. Stored in the database as a short string (see [encode]).
 *
 * - DAILY: every [interval] days
 * - WEEKLY: every [interval] weeks, optionally restricted to [days] of the week
 * - MONTHLY: every [interval] months (same day of month)
 * - YEARLY: every [interval] years
 */
data class RepeatRule(
    val freq: Freq,
    val interval: Int = 1,
    val days: Set<DayOfWeek> = emptySet(),
) {
    enum class Freq { DAILY, WEEKLY, MONTHLY, YEARLY }

    fun encode(): String =
        "${freq.name}|$interval|${days.sortedBy { it.value }.joinToString(",") { it.name }}"

    /** The first occurrence strictly after [from] (time of day is kept). */
    fun nextAfter(from: LocalDateTime): LocalDateTime = when (freq) {
        Freq.DAILY -> from.plusDays(interval.toLong())
        Freq.MONTHLY -> from.plusMonths(interval.toLong())
        Freq.YEARLY -> from.plusYears(interval.toLong())
        Freq.WEEKLY -> if (days.isEmpty()) {
            from.plusWeeks(interval.toLong())
        } else {
            val sorted = days.sortedBy { it.value }
            val laterThisWeek = sorted.firstOrNull { it.value > from.dayOfWeek.value }
            if (laterThisWeek != null) {
                from.plusDays((laterThisWeek.value - from.dayOfWeek.value).toLong())
            } else {
                // Jump to the first selected day of the week `interval` weeks ahead (weeks start on Monday).
                val mondayOfThisWeek = from.minusDays((from.dayOfWeek.value - 1).toLong())
                mondayOfThisWeek
                    .plusWeeks(interval.toLong())
                    .plusDays((sorted.first().value - 1).toLong())
            }
        }
    }

    /** Human readable, e.g. "Every day", "Every 2 weeks", "Every Tue, Thu". */
    fun describe(): String {
        val dayNames = days.sortedBy { it.value }
            .joinToString(", ") { it.name.take(3).lowercase().replaceFirstChar { c -> c.uppercase() } }
        return when (freq) {
            Freq.DAILY -> if (interval == 1) "Every day" else "Every $interval days"
            Freq.WEEKLY -> when {
                days == WEEKDAYS -> "Every weekday"
                days.isNotEmpty() && interval == 1 -> "Every $dayNames"
                days.isNotEmpty() -> "Every $interval weeks on $dayNames"
                interval == 1 -> "Every week"
                else -> "Every $interval weeks"
            }
            Freq.MONTHLY -> if (interval == 1) "Every month" else "Every $interval months"
            Freq.YEARLY -> if (interval == 1) "Every year" else "Every $interval years"
        }
    }

    companion object {
        val WEEKDAYS: Set<DayOfWeek> = setOf(
            DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY,
            DayOfWeek.THURSDAY, DayOfWeek.FRIDAY,
        )

        fun decode(s: String?): RepeatRule? {
            if (s.isNullOrBlank()) return null
            val parts = s.split("|")
            if (parts.size < 3) return null
            return try {
                RepeatRule(
                    freq = Freq.valueOf(parts[0]),
                    interval = parts[1].toInt().coerceAtLeast(1),
                    days = parts[2].split(",").filter { it.isNotBlank() }.map { DayOfWeek.valueOf(it) }.toSet(),
                )
            } catch (e: IllegalArgumentException) {
                null
            }
        }
    }
}
