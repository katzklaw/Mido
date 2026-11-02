package app.mido.parser

import app.mido.data.LocationTrigger
import app.mido.data.RepeatRule
import java.time.DateTimeException
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.temporal.TemporalAdjusters

data class ParsedTask(
    val title: String,
    val due: LocalDateTime?,
    val allDay: Boolean,
    val repeat: RepeatRule?,
    val locationName: String?,
    val locationTrigger: LocationTrigger?,
    /** "every time I get to work": the place reminder comes back each time instead of finishing once. */
    val locationRepeat: Boolean = false,
)

/**
 * Turns typed text like "take out trash every tuesday at 7pm" into a [ParsedTask].
 * Everything runs on-device; no network or API needed.
 *
 * Each recognised phrase is cut out of the text; whatever is left becomes the title.
 */
object QuickAddParser {

    private const val DAY =
        "(?:monday|mon|tuesday|tues|tue|wednesday|weds|wed|thursday|thurs|thur|thu|friday|fri|saturday|sat|sunday|sun)s?"
    private const val MONTH =
        "jan(?:uary)?|feb(?:ruary)?|mar(?:ch)?|apr(?:il)?|may|jun(?:e)?|jul(?:y)?|aug(?:ust)?|sept?(?:ember)?|oct(?:ober)?|nov(?:ember)?|dec(?:ember)?"

    /** Lookahead that ends a location name. */
    private const val END =
        "(?=\\s*$|\\s*[,.;]|\\s+(?:every|tomorrow|today|tonight|next|this|on|in|at\\s+(?:\\d|noon|midnight))\\b)"

    private val I = RegexOption.IGNORE_CASE

    // --- locations (parsed first so place names are not mistaken for dates) ---
    private val R_ARRIVE = Regex("""\b(when\s+i|whenever\s+i|every\s+time\s+i)\s+(?:get|got|arrive|reach|am)\s+(?:to\s+|at\s+)?(home|work|[^,.;]+?)$END""", I)
    private val R_LEAVE = Regex("""\b(when\s+i|whenever\s+i|every\s+time\s+i)\s+(?:leave|left|exit)\s+([^,.;]+?)$END""", I)
    private val R_AT_MARK = Regex("""@\s*([^,;@]+?)$END""")

    // "at Costco" / "at home" (only when capitalised, so "look at the sky" is left alone)
    private val R_AT_PLACE = Regex("""\b(?i:at)\s+((?i:home|work)\b|[A-Z][^,.;]*?)$END""")

    // Lowercase "at costco" / "at smile dental": accepted as a fallback unless it reads like ordinary English
    // ("look at the sky", "at least", "at night"). The place lookup sorts out whether it is a real place.
    private val R_AT_LOWER = Regex(
        """\bat\s+(?!(?:the|a|an|my|your|our|his|her|their|this|that|these|those|least|most|once|night|noon|midnight|""" +
            """first|last|some|any|all|it|them|him|me|us|home|work|\d)\b)([a-z][^,.;]*?)$END""",
    )
    private val NOT_A_PLACE_AFTER =
        setOf("look", "looks", "looking", "laugh", "laughing", "stare", "glance", "smile", "shout", "yell", "good", "bad", "great")

    // --- repeats ---
    private val R_EVERY_DAYS = Regex("""\bevery\s+($DAY(?:\s*(?:,|and|&)\s*$DAY)*)\b""", I)
    private val R_EVERY_UNIT = Regex("""\bevery\s+(?:(other)\s+|(\d+)\s+)?(weekday|day|week|month|year)s?\b""", I)
    private val R_REPEAT_WORD = Regex("""\b(daily|weekly|monthly|yearly|annually)\b""", I)

    // --- dates ---
    private val R_IN = Regex("""\bin\s+(\d+|an?|half an)\s+(minutes?|mins?|hours?|hrs?|days?|weeks?|months?)\b""", I)
    private val R_DAY_AFTER = Regex("""\bday after tomorrow\b""", I)
    private val R_TOMORROW = Regex("""\b(?:tomorrow|tmrw|tmr)\b""", I)
    private val R_TODAY = Regex("""\btoday\b""", I)
    private val R_TONIGHT = Regex("""\btonight\b""", I)
    private val R_NEXT_WEEK = Regex("""\bnext week\b""", I)
    private val R_MONTH_DAY = Regex("""\b(?:on\s+)?($MONTH)\.?\s+(\d{1,2})(?:st|nd|rd|th)?\b(?:,?\s+(\d{4}))?""", I)
    private val R_NUM_DATE = Regex("""\b(?:on\s+)?(\d{1,2})/(\d{1,2})(?:/(\d{2,4}))?\b""")
    private val R_ORDINAL = Regex("""\b(?:on\s+)?the\s+(\d{1,2})(?:st|nd|rd|th)\b""", I)
    private val R_WEEKDAY = Regex("""\b(?:(next|this|on)\s+)?($DAY)\b""", I)

    // --- times ---
    private val R_TIME_AMPM = Regex("""\b(?:at\s+)?(\d{1,2})(?::(\d{2}))?\s*([ap])\.?m\.?(?![a-z])""", I)
    private val R_TIME_24 = Regex("""\b(?:at\s+)?([01]?\d|2[0-3]):([0-5]\d)\b""", I)
    private val R_NOON = Regex("""\b(?:at\s+)?(noon|midnight)\b""", I)
    private val R_AT_BARE = Regex("""\bat\s+(\d{1,2})\b(?![:/])(?!\s*(?:st|nd|rd|th|am|pm|%))""", I)
    private val R_PART_OF_DAY = Regex("""\b(?:in the|this)\s+(morning|afternoon|evening)\b""", I)

    fun parse(input: String, now: LocalDateTime): ParsedTask {
        var text = " ${input.trim()} "

        fun take(re: Regex): MatchResult? {
            val m = re.find(text) ?: return null
            text = text.replaceRange(m.range, " ")
            return m
        }

        val today = now.toLocalDate()

        // ---- location ----
        var locationName: String? = null
        var trigger: LocationTrigger? = null
        var everyTime = false

        val arrive = take(R_ARRIVE)
        if (arrive != null) {
            locationName = arrive.groupValues[2]
            trigger = LocationTrigger.ARRIVE
            everyTime = !arrive.groupValues[1].startsWith("when ", ignoreCase = true)
        }
        if (locationName == null) {
            val leave = take(R_LEAVE)
            if (leave != null) {
                locationName = leave.groupValues[2]
                trigger = LocationTrigger.LEAVE
                everyTime = !leave.groupValues[1].startsWith("when ", ignoreCase = true)
            }
        }
        if (locationName == null) {
            val mark = take(R_AT_MARK)
            if (mark != null) locationName = mark.groupValues[1]
        }

        // ---- repeat ----
        var repeat: RepeatRule? = null

        val everyDays = take(R_EVERY_DAYS)
        if (everyDays != null) {
            val days = Regex(DAY, I).findAll(everyDays.groupValues[1]).map { dayOf(it.value) }.toSet()
            repeat = RepeatRule(RepeatRule.Freq.WEEKLY, 1, days)
        }
        if (repeat == null) {
            val unit = take(R_EVERY_UNIT)
            if (unit != null) {
                val interval = when {
                    unit.groupValues[1].isNotEmpty() -> 2
                    unit.groupValues[2].isNotEmpty() -> unit.groupValues[2].toInt()
                    else -> 1
                }
                repeat = when (unit.groupValues[3].lowercase()) {
                    "weekday" -> RepeatRule(RepeatRule.Freq.WEEKLY, 1, RepeatRule.WEEKDAYS)
                    "day" -> RepeatRule(RepeatRule.Freq.DAILY, interval)
                    "week" -> RepeatRule(RepeatRule.Freq.WEEKLY, interval)
                    "month" -> RepeatRule(RepeatRule.Freq.MONTHLY, interval)
                    else -> RepeatRule(RepeatRule.Freq.YEARLY, interval)
                }
            }
        }
        if (repeat == null) {
            val word = take(R_REPEAT_WORD)
            if (word != null) {
                repeat = when (word.groupValues[1].lowercase()) {
                    "daily" -> RepeatRule(RepeatRule.Freq.DAILY)
                    "weekly" -> RepeatRule(RepeatRule.Freq.WEEKLY)
                    "monthly" -> RepeatRule(RepeatRule.Freq.MONTHLY)
                    else -> RepeatRule(RepeatRule.Freq.YEARLY)
                }
            }
        }

        // ---- date ----
        var date: LocalDate? = null
        var exact: LocalDateTime? = null // for "in 2 hours"
        var tonight = false

        val relative = take(R_IN)
        if (relative != null) {
            val amountToken = relative.groupValues[1].lowercase()
            val unit = relative.groupValues[2].lowercase()
            val half = amountToken.startsWith("half")
            val amount = when {
                half -> 30L
                amountToken == "a" || amountToken == "an" -> 1L
                else -> amountToken.toLong()
            }
            if (unit.startsWith("min")) {
                exact = now.plusMinutes(amount)
            } else if (unit.startsWith("h")) {
                exact = if (half) now.plusMinutes(30) else now.plusHours(amount)
            } else if (unit.startsWith("d")) {
                date = today.plusDays(amount)
            } else if (unit.startsWith("w")) {
                date = today.plusWeeks(amount)
            } else {
                date = today.plusMonths(amount)
            }
        }
        if (exact == null && date == null) {
            if (take(R_DAY_AFTER) != null) {
                date = today.plusDays(2)
            } else if (take(R_TOMORROW) != null) {
                date = today.plusDays(1)
            } else if (take(R_TODAY) != null) {
                date = today
            } else if (take(R_TONIGHT) != null) {
                date = today
                tonight = true
            } else if (take(R_NEXT_WEEK) != null) {
                date = today.with(TemporalAdjusters.next(DayOfWeek.MONDAY))
            }
        }
        if (exact == null && date == null) {
            val m = take(R_MONTH_DAY)
            if (m != null) {
                val month = monthOf(m.groupValues[1])
                val day = m.groupValues[2].toInt()
                val explicitYear = m.groupValues[3].takeIf { it.isNotEmpty() }?.toInt()
                date = resolveMonthDay(month, day, explicitYear, today)
            }
        }
        if (exact == null && date == null) {
            val m = take(R_NUM_DATE)
            if (m != null) {
                val month = m.groupValues[1].toInt()
                val day = m.groupValues[2].toInt()
                val year = m.groupValues[3].takeIf { it.isNotEmpty() }?.toInt()?.let { if (it < 100) 2000 + it else it }
                if (month in 1..12 && day in 1..31) date = resolveMonthDay(month, day, year, today)
            }
        }
        if (exact == null && date == null) {
            val m = take(R_ORDINAL)
            if (m != null) {
                val day = m.groupValues[1].toInt()
                if (day in 1..31) date = nextDayOfMonth(day, today)
            }
        }
        if (exact == null && date == null) {
            val m = take(R_WEEKDAY)
            if (m != null) {
                val modifier = m.groupValues[1].lowercase()
                val dow = dayOf(m.groupValues[2])
                var d = today.with(TemporalAdjusters.next(dow))
                val endOfThisWeek = today.with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY))
                if (modifier == "next" && !d.isAfter(endOfThisWeek)) d = d.plusWeeks(1)
                date = d
            }
        }

        // ---- time ----
        var time: LocalTime? = null

        val ampm = take(R_TIME_AMPM)
        if (ampm != null) {
            var h = ampm.groupValues[1].toInt()
            val min = ampm.groupValues[2].takeIf { it.isNotEmpty() }?.toInt() ?: 0
            val pm = ampm.groupValues[3].equals("p", ignoreCase = true)
            if (h in 1..12 && min in 0..59) {
                if (h == 12) h = 0
                if (pm) h += 12
                time = LocalTime.of(h, min)
            }
        }
        if (time == null) {
            val m = take(R_TIME_24)
            if (m != null) time = LocalTime.of(m.groupValues[1].toInt(), m.groupValues[2].toInt())
        }
        if (time == null) {
            val m = take(R_NOON)
            if (m != null) {
                time = if (m.groupValues[1].equals("noon", ignoreCase = true)) LocalTime.NOON else LocalTime.MIDNIGHT
            }
        }
        if (time == null) {
            val m = take(R_AT_BARE)
            if (m != null) {
                // "at 5" -> 5 PM, "at 9" -> 9 AM, "at 12" -> noon
                val h = m.groupValues[1].toInt()
                if (h in 1..12) {
                    val hour = when (h) {
                        in 1..6 -> h + 12
                        else -> h
                    }
                    time = LocalTime.of(hour, 0)
                }
            }
        }
        if (time == null) {
            val m = take(R_PART_OF_DAY)
            if (m != null) {
                time = when (m.groupValues[1].lowercase()) {
                    "morning" -> LocalTime.of(9, 0)
                    "afternoon" -> LocalTime.of(14, 0)
                    else -> LocalTime.of(18, 0)
                }
            }
        }
        if (time == null && tonight) time = LocalTime.of(20, 0)

        // ---- location: "at Costco" (after times are removed) ----
        if (locationName == null) {
            val m = take(R_AT_PLACE)
            if (m != null) locationName = m.groupValues[1]
        }
        if (locationName == null) {
            val m = R_AT_LOWER.findAll(text).firstOrNull { match ->
                val before = text.substring(0, match.range.first).trimEnd().substringAfterLast(' ').lowercase()
                before !in NOT_A_PLACE_AFTER && match.groupValues[1].trim().split(Regex("\\s+")).size <= 4
            }
            if (m != null) {
                text = text.replaceRange(m.range, " ")
                locationName = m.groupValues[1].trim().split(Regex("\\s+")).joinToString(" ") { w -> w.replaceFirstChar { it.uppercase() } }
            }
        }

        // ---- put date and time together ----
        val rule = repeat
        var allDay = false
        val due: LocalDateTime?
        val exactDue = exact
        if (exactDue != null) {
            due = exactDue
        } else {
            if (date == null && rule != null) {
                date = firstOccurrenceDate(rule, today, time, now)
            }
            val t = time
            if (date == null && t != null) {
                date = if (!LocalDateTime.of(today, t).isAfter(now)) today.plusDays(1) else today
            }
            val d = date
            if (d == null) {
                due = null
            } else if (t == null) {
                allDay = true
                due = d.atStartOfDay()
            } else {
                due = LocalDateTime.of(d, t)
            }
        }

        val place = locationName?.trim()?.trimEnd('.', ',', ';')
            ?.replaceFirstChar { it.uppercase() }
            ?.takeIf { it.isNotBlank() }

        return ParsedTask(
            title = cleanTitle(text, input),
            due = due,
            allDay = allDay,
            repeat = rule,
            locationName = place,
            locationTrigger = if (place == null) null else trigger,
            locationRepeat = place != null && trigger != null && everyTime,
        )
    }

    private fun firstOccurrenceDate(rule: RepeatRule, today: LocalDate, time: LocalTime?, now: LocalDateTime): LocalDate {
        val timeStillAhead = time == null || LocalDateTime.of(today, time).isAfter(now)
        if (rule.freq == RepeatRule.Freq.WEEKLY && rule.days.isNotEmpty()) {
            if (rule.days.contains(today.dayOfWeek) && timeStillAhead) return today
            return (1..7).map { today.plusDays(it.toLong()) }.first { rule.days.contains(it.dayOfWeek) }
        }
        return if (timeStillAhead) today else today.plusDays(1)
    }

    private fun resolveMonthDay(month: Int, day: Int, explicitYear: Int?, today: LocalDate): LocalDate? {
        return try {
            if (explicitYear != null) {
                LocalDate.of(explicitYear, month, day)
            } else {
                val thisYear = LocalDate.of(today.year, month, day)
                if (thisYear.isBefore(today)) thisYear.plusYears(1) else thisYear
            }
        } catch (e: DateTimeException) {
            null
        }
    }

    private fun nextDayOfMonth(day: Int, today: LocalDate): LocalDate {
        var cursor = today.withDayOfMonth(1)
        for (i in 0 until 14) {
            val candidate = try {
                cursor.withDayOfMonth(day)
            } catch (e: DateTimeException) {
                null
            }
            if (candidate != null && candidate.isAfter(today)) return candidate
            cursor = cursor.plusMonths(1)
        }
        return today.plusMonths(1)
    }

    private val DAY_MAP = mapOf(
        "mon" to DayOfWeek.MONDAY, "monday" to DayOfWeek.MONDAY,
        "tue" to DayOfWeek.TUESDAY, "tues" to DayOfWeek.TUESDAY, "tuesday" to DayOfWeek.TUESDAY,
        "wed" to DayOfWeek.WEDNESDAY, "weds" to DayOfWeek.WEDNESDAY, "wednesday" to DayOfWeek.WEDNESDAY,
        "thu" to DayOfWeek.THURSDAY, "thur" to DayOfWeek.THURSDAY, "thurs" to DayOfWeek.THURSDAY,
        "thursday" to DayOfWeek.THURSDAY,
        "fri" to DayOfWeek.FRIDAY, "friday" to DayOfWeek.FRIDAY,
        "sat" to DayOfWeek.SATURDAY, "saturday" to DayOfWeek.SATURDAY,
        "sun" to DayOfWeek.SUNDAY, "sunday" to DayOfWeek.SUNDAY,
    )

    private fun dayOf(token: String): DayOfWeek {
        val t = token.lowercase()
        return DAY_MAP[t] ?: DAY_MAP.getValue(t.removeSuffix("s"))
    }

    private fun monthOf(token: String): Int {
        val names = listOf("jan", "feb", "mar", "apr", "may", "jun", "jul", "aug", "sep", "oct", "nov", "dec")
        return names.indexOf(token.lowercase().take(3)) + 1
    }

    private fun cleanTitle(leftover: String, original: String): String {
        var t = leftover.replace(Regex("\\s+"), " ").trim()
        t = t.replace(Regex("^(?:please\\s+)?remind me(?:\\s+to)?\\s+", I), "")
        var previous: String
        do {
            previous = t
            t = t.replace(Regex("^(?:on|at|by|for|to|and|-)\\s+", I), "")
            t = t.replace(Regex("\\s+(?:on|at|by|for|to|and|in|-)$", I), "")
            t = t.trim(' ', ',', ';', '.', '-')
        } while (t != previous)
        if (t.isBlank()) return original.trim()
        return t.replaceFirstChar { it.uppercase() }
    }
}
