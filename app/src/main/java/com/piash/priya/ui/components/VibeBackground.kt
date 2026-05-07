package com.piash.priya.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.blur
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.dp
import com.piash.priya.ui.theme.VibeCyan
import com.piash.priya.ui.theme.VibeDark
import com.piash.priya.ui.theme.VibeDeepDark
import com.piash.priya.ui.theme.VibeMagenta
import com.piash.priya.ui.theme.VibePink
import com.piash.priya.ui.theme.VibeViolet
import kotlin.math.cos
import kotlin.math.sin

/**
 * Looping aurora-style background.
 *
 * Three soft-edged colour blobs orbit lazily over a deep violet gradient.
 * Each blob is drawn into its own offscreen layer with a [BlendMode.Plus]
 * composite so overlapping regions saturate into bright neon — the look is
 * much closer to a real "vibe" UI than a flat dark background.
 */
@Composable
fun VibeAuroraBackground(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit = {},
) {
    val transition = rememberInfiniteTransition(label = "aurora")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 14000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "aurora-phase"
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.linearGradient(
                    colors = listOf(VibeDeepDark, VibeDark, Color(0xFF130823))
                )
            )
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .blur(72.dp)
                .graphicsLayerWithCompositing()
        ) {
            val w = size.width
            val h = size.height
            val r = (w.coerceAtLeast(h)) * 0.55f

            // Three orbiting blobs at different speeds.
            blob(phase, 0.0f, w * 0.30f, h * 0.30f, r, VibePink)
            blob(phase, 0.33f, w * 0.75f, h * 0.55f, r * 0.9f, VibeViolet)
            blob(phase, 0.66f, w * 0.40f, h * 0.85f, r * 0.85f, VibeMagenta)
            blob(phase, 0.85f, w * 0.85f, h * 0.18f, r * 0.6f, VibeCyan)
        }
        content()
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.blob(
    phase: Float, offset: Float, cx: Float, cy: Float, r: Float, color: Color,
) {
    val angle = ((phase + offset) % 1f) * 2f * Math.PI.toFloat()
    val dx = cos(angle) * (size.width * 0.12f)
    val dy = sin(angle * 1.3f) * (size.height * 0.10f)
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(color.copy(alpha = 0.55f), color.copy(alpha = 0f)),
            center = Offset(cx + dx, cy + dy),
            radius = r,
        ),
        radius = r,
        center = Offset(cx + dx, cy + dy),
        blendMode = BlendMode.Plus,
    )
}

private fun Modifier.graphicsLayerWithCompositing() = this.graphicsLayer {
    compositingStrategy = CompositingStrategy.Offscreen
}
