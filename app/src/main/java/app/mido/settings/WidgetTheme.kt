package app.mido.settings

/** Colour schemes for the home screen widget. Colours are ARGB. */
enum class WidgetTheme(
    val label: String,
    val background: Long,
    val text: Long,
    val muted: Long,
    val accent: Long,
    val onAccent: Long,
) {
    BLUE("Blue", 0xFF12315A, 0xFFEAF2FF, 0xFF9DB7DB, 0xFF5BB8FF, 0xFF06223F),
    BLACK("Black", 0xFF000000, 0xFFF2F2F2, 0xFF9A9A9A, 0xFF00B8D4, 0xFF00252B),
    GREEN("Green", 0xFF123524, 0xFFEAF7EF, 0xFF9CC4AC, 0xFF5FD38D, 0xFF06301A),
    PURPLE("Purple", 0xFF2B1D4A, 0xFFF1EBFF, 0xFFB9A8DD, 0xFFB794F6, 0xFF2A1450),
    CRIMSON("Crimson", 0xFF4A1D26, 0xFFFFEEF1, 0xFFD3A3AC, 0xFFFF7A8C, 0xFF4A0F1A),
    LIGHT("Light", 0xFFF4F7FB, 0xFF152033, 0xFF5B6B80, 0xFF1E88E5, 0xFFFFFFFF);

    companion object {
        val DEFAULT = BLUE
        val OPACITIES = listOf(100, 85, 70, 50, 30)

        fun fromName(name: String?): WidgetTheme = entries.firstOrNull { it.name == name } ?: DEFAULT
    }
}
