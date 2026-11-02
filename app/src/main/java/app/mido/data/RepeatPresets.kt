package app.mido.data

/** The repeat choices offered when editing a task. */
object RepeatPresets {
    data class Preset(val label: String, val rule: RepeatRule?)

    val all: List<Preset> = listOf(
        Preset("Doesn't repeat", null),
        Preset("Every day", RepeatRule(RepeatRule.Freq.DAILY)),
        Preset("Every weekday", RepeatRule(RepeatRule.Freq.WEEKLY, 1, RepeatRule.WEEKDAYS)),
        Preset("Every week", RepeatRule(RepeatRule.Freq.WEEKLY)),
        Preset("Every 2 weeks", RepeatRule(RepeatRule.Freq.WEEKLY, 2)),
        Preset("Every month", RepeatRule(RepeatRule.Freq.MONTHLY)),
        Preset("Every year", RepeatRule(RepeatRule.Freq.YEARLY)),
    )

    /** The presets, plus the task's own rule if it is something custom (like "Every Tue, Thu"). */
    fun optionsFor(current: RepeatRule?): List<Preset> =
        if (current == null || all.any { it.rule?.encode() == current.encode() }) {
            all
        } else {
            all + Preset("${current.describe()} (current)", current)
        }

    fun indexOf(options: List<Preset>, rule: RepeatRule?): Int =
        options.indexOfFirst { it.rule?.encode() == rule?.encode() }.coerceAtLeast(0)
}
