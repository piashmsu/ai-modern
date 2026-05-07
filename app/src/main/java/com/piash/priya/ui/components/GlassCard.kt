package com.piash.priya.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.piash.priya.ui.theme.VibePink
import com.piash.priya.ui.theme.VibeViolet

/**
 * Translucent panel with a soft neon outline — the standard surface used
 * everywhere in the Priya UI. Mimics the look of frosted glass over the
 * aurora background: low-opacity fill + subtle gradient stroke.
 */
@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 22.dp,
    contentPadding: Dp = 18.dp,
    accent: Brush = Brush.linearGradient(listOf(VibePink.copy(alpha = 0.55f), VibeViolet.copy(alpha = 0.40f))),
    content: @Composable () -> Unit,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = Color.Transparent,
        shape = RoundedCornerShape(cornerRadius),
        border = BorderStroke(1.dp, accent),
    ) {
        androidx.compose.foundation.layout.Box(
            modifier = Modifier
                .background(
                    color = Color.White.copy(alpha = 0.04f),
                    shape = RoundedCornerShape(cornerRadius),
                )
                .padding(contentPadding),
        ) {
            androidx.compose.runtime.CompositionLocalProvider(
                androidx.compose.material3.LocalContentColor provides MaterialTheme.colorScheme.onSurface
            ) { content() }
        }
    }
}
