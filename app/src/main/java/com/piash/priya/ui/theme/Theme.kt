package com.piash.priya.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

/**
 * Priya is dark-only by design — the vibe relies on neon accents over a deep
 * violet base, so we ignore the system [isSystemInDarkTheme] flag entirely.
 */
private val PriyaColors = darkColorScheme(
    primary = VibePink,
    onPrimary = Color.White,
    primaryContainer = VibePink.copy(alpha = 0.18f),
    onPrimaryContainer = VibePink,
    secondary = VibeViolet,
    onSecondary = Color.White,
    secondaryContainer = VibeViolet.copy(alpha = 0.18f),
    onSecondaryContainer = VibeOnDark,
    tertiary = VibeCyan,
    onTertiary = VibeDark,
    background = VibeDark,
    onBackground = VibeOnDark,
    surface = VibeCard,
    onSurface = VibeOnDark,
    surfaceVariant = Color(0xFF221833),
    onSurfaceVariant = VibeMuted,
    outline = Color(0xFF3A2C52),
    error = Color(0xFFFF6B7A),
    onError = Color.White,
)

@Composable
fun PriyaTheme(content: @Composable () -> Unit) {
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? android.app.Activity)?.window ?: return@SideEffect
            window.statusBarColor = Color.Transparent.toArgb()
            window.navigationBarColor = Color.Transparent.toArgb()
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = false
                isAppearanceLightNavigationBars = false
            }
        }
    }
    MaterialTheme(colorScheme = PriyaColors, content = content)
}
