package app.mido.ui

import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

data class NextUpLine(val headline: String, val sub: String)

/** The words for the "next up" strip: shared by the app header and the home screen widget. */
object NextUpText {
    private val dateFmt = DateTimeFormatter.ofPattern("EEEE, MMM d")

    fun of(next: NextUp?, now: LocalDateTime, zone: ZoneId = ZoneId.systemDefault()): NextUpLine {
        val nowMs = now.atZone(zone).toInstant().toEpochMilli()
        if (next == null) return NextUpLine("Nothing else scheduled today", now.format(dateFmt))

        val leg = next.leave
        val headline = when {
            leg != null && leg.leaveByMs <= nowMs -> "Leave now for “${next.title}”"
            leg != null -> "Leave for “${next.title}” in ${Fmt.minutesLabel(minutesUntil(leg.leaveByMs, nowMs))}"
            next.minutesAway < 1 -> "“${next.title}” is starting now"
            else -> "“${next.title}” starts in ${Fmt.minutesLabel(next.minutesAway)}"
        }
        val sub = when {
            leg != null ->
                "🚗 ${leg.driveMinutes} min drive · leave by ${Fmt.time(leg.leaveByMs, zone)} · starts ${Fmt.time(next.startMs, zone)}"
            next.location != null -> "📍 ${next.location}"
            else -> now.format(dateFmt)
        }
        return NextUpLine(headline, sub)
    }

    /** Whole minutes from now until [targetMs], rounded up so "1 min" shows until it is really time. */
    private fun minutesUntil(targetMs: Long, nowMs: Long): Long = ((targetMs - nowMs + 59_999L) / 60_000L).coerceAtLeast(1L)
}
