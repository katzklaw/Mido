package app.mido.pro

import app.mido.settings.WidgetTheme

/** What the free version gets. Pure rules, so they can be tested without Android. */
object ProPolicy {
    /** Text-to-task suggestions that work before Pro is needed. */
    const val FREE_TEXT_SUGGESTIONS = 5

    val FREE_THEMES = setOf(WidgetTheme.BLACK, WidgetTheme.LIGHT)

    fun themeAllowed(theme: WidgetTheme, pro: Boolean) = pro || theme in FREE_THEMES

    /** The theme actually drawn: a Pro colour falls back to Black when Pro isn't unlocked. */
    fun effectiveTheme(theme: WidgetTheme, pro: Boolean): WidgetTheme = if (themeAllowed(theme, pro)) theme else WidgetTheme.BLACK

    /** Free widgets are fully opaque. */
    fun effectiveOpacity(opacity: Int, pro: Boolean): Int = if (pro) opacity else 100

    fun textSuggestionAllowed(used: Int, pro: Boolean) = pro || used < FREE_TEXT_SUGGESTIONS

    fun freeTextSuggestionsLeft(used: Int) = (FREE_TEXT_SUGGESTIONS - used).coerceAtLeast(0)
}
