package app.mido.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val Accent = Color(0xFF00B8D4)
val HeaderDark = Color(0xFF343437)
val OverdueRed = Color(0xFFE5484D)

private val LightColors = lightColorScheme(
    primary = Color(0xFF008CA3),
    onPrimary = Color.White,
    background = Color(0xFFF6F8FA),
    surface = Color.White,
    surfaceVariant = Color(0xFFE9EEF2),
    onSurfaceVariant = Color(0xFF5B6770),
)

private val DarkColors = darkColorScheme(
    primary = Accent,
    onPrimary = Color(0xFF00363F),
    background = Color(0xFF111416),
    surface = Color(0xFF1B1F22),
    surfaceVariant = Color(0xFF2A3035),
    onSurfaceVariant = Color(0xFFA7B1B8),
)

@Composable
fun MidoTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors,
        content = content,
    )
}
