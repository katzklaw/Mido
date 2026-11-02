package app.mido.reminders

/** Pure rules for repeat alerts, so they can be tested without Android. */
object NagPolicy {
    val INTERVAL_CHOICES = listOf(1, 2, 5, 10)

    /** Stop after about an hour so an ignored alert doesn't ring all day. */
    const val MAX_MINUTES = 60

    fun maxNags(intervalMin: Int): Int = MAX_MINUTES / intervalMin.coerceAtLeast(1)

    /** When to alert again after [sent] repeats already went out, or null once the cap is reached. */
    fun nextAt(sent: Int, nowMs: Long, intervalMin: Int): Long? {
        val interval = intervalMin.coerceAtLeast(1)
        if (sent >= maxNags(interval)) return null
        return nowMs + interval * 60_000L
    }

    /** True for the last repeat; it's posted as an ordinary (swipeable) notification. */
    fun isLast(sent: Int, intervalMin: Int): Boolean = sent + 1 >= maxNags(intervalMin)
}
