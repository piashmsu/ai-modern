package com.piash.priya.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val DarkColors = darkColorScheme(
    primary = VibePink,
    onPrimary = VibeDark,
    secondary = VibeViolet,
    onSecondary = VibeDark,
    tertiary = VibeCyan,
    background = VibeDark,
    onBackground = VibeOnDark,
    surface = VibeCard,
    onSurface = VibeOnDark,
    surfaceVariant = VibeCard,
    onSurfaceVariant = VibeMuted,
)

private val LightColors = lightColorScheme(
    primary = VibePink,
    secondary = VibeViolet,
    tertiary = VibeCyan,
)

@Composable
fun PriyaTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colors = if (darkTheme) DarkColors else LightColors
    MaterialTheme(colorScheme = colors, content = content)
}
