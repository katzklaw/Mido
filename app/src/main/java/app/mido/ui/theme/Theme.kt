package app.mido.ui.theme

import android.app.Activity
import android.content.Context
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import app.mido.pro.Entitlements
import app.mido.pro.ProPolicy
import app.mido.settings.AppSettings
import app.mido.settings.WidgetTheme

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

/** The colour theme chosen for the app itself, or null to follow the phone's light/dark mode. */
object ThemeState {
    var choice: WidgetTheme? by mutableStateOf(null)
        private set

    /** Re-reads the settings (and Pro status); call after anything that can change the theme. */
    fun load(context: Context) {
        val settings = AppSettings(context)
        choice = if (settings.appUsesTheme) {
            ProPolicy.effectiveTheme(settings.widgetTheme, Entitlements.isPro(context))
        } else {
            null
        }
    }
}

/** Builds the app's colours from a widget theme so the app and widget match. */
fun schemeFor(t: WidgetTheme): ColorScheme {
    val base = Color(t.background)
    val text = Color(t.text)
    val muted = Color(t.muted)
    val accent = Color(t.accent)
    val onAccent = Color(t.onAccent)
    if (t == WidgetTheme.LIGHT) {
        return lightColorScheme(
            primary = accent, onPrimary = onAccent,
            background = base, onBackground = text,
            surface = Color.White, onSurface = text,
            surfaceVariant = lerp(base, accent, 0.10f), onSurfaceVariant = muted,
            surfaceContainerLowest = Color.White, surfaceContainerLow = Color.White,
            surfaceContainer = lerp(base, Color.White, 0.5f), surfaceContainerHigh = lerp(base, accent, 0.06f),
            surfaceContainerHighest = lerp(base, accent, 0.12f),
            secondaryContainer = lerp(base, accent, 0.22f), onSecondaryContainer = text,
            outline = muted,
        )
    }
    // Dark themes: the widget colour becomes the card colour and the page behind it is darker.
    val bg = if (t == WidgetTheme.BLACK) base else lerp(base, Color.Black, 0.45f)
    val surface = lerp(bg, Color.White, 0.07f)
    return darkColorScheme(
        primary = accent, onPrimary = onAccent,
        background = bg, onBackground = text,
        surface = surface, onSurface = text,
        surfaceVariant = lerp(bg, Color.White, 0.14f), onSurfaceVariant = muted,
        surfaceContainerLowest = bg, surfaceContainerLow = surface,
        surfaceContainer = lerp(bg, Color.White, 0.09f), surfaceContainerHigh = lerp(bg, Color.White, 0.12f),
        surfaceContainerHighest = lerp(bg, Color.White, 0.15f),
        secondaryContainer = lerp(bg, accent, 0.28f), onSecondaryContainer = text,
        outline = muted,
    )
}

@Composable
fun MidoTheme(content: @Composable () -> Unit) {
    val chosen = ThemeState.choice
    val systemDark = isSystemInDarkTheme()
    val scheme = chosen?.let(::schemeFor) ?: if (systemDark) DarkColors else LightColors
    val lightBars = if (chosen != null) chosen == WidgetTheme.LIGHT else !systemDark
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window
            if (window != null) {
                WindowCompat.getInsetsController(window, view).apply {
                    isAppearanceLightStatusBars = lightBars
                    isAppearanceLightNavigationBars = lightBars
                }
            }
        }
    }
    MaterialTheme(colorScheme = scheme, content = content)
}
