package com.piash.priya.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.piash.priya.ui.theme.VibeCyan
import com.piash.priya.ui.theme.VibePink
import kotlin.math.abs
import kotlin.math.sin
import kotlin.random.Random

/**
 * Decorative bar-graph waveform.
 *
 * Heights are seeded once and breathe via a single time-driven sine, so it
 * looks alive without requiring real-time mic level data.
 */
@Composable
fun VoiceWaveform(
    color: Color = VibePink,
    accent: Color = VibeCyan,
    bars: Int = 28,
    modifier: Modifier = Modifier.fillMaxWidth().height(36.dp),
) {
    val seeds = remember { FloatArray(bars) { Random.nextFloat() * 2f * Math.PI.toFloat() } }
    val transition = rememberInfiniteTransition(label = "wave")
    val t by transition.animateFloat(
        initialValue = 0f,
        targetValue = 2f * Math.PI.toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1300, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "wave-phase",
    )
    Canvas(modifier = modifier) {
        val barWidth = size.width / (bars * 1.6f)
        val gap = (size.width - barWidth * bars) / (bars - 1)
        val cy = size.height / 2f
        for (i in 0 until bars) {
            val phase = seeds[i] + t
            val mag = (0.30f + 0.70f * (0.5f + 0.5f * sin(phase))) * (0.6f + 0.4f * abs(sin(phase * 1.3f)))
            val barHeight = size.height * mag
            val x = i * (barWidth + gap)
            val mix = (i.toFloat() / bars)
            val barColor = lerp(color, accent, mix)
            drawLine(
                color = barColor,
                start = Offset(x + barWidth / 2f, cy - barHeight / 2f),
                end = Offset(x + barWidth / 2f, cy + barHeight / 2f),
                strokeWidth = barWidth,
                cap = StrokeCap.Round,
            )
        }
    }
    Spacer(Modifier)  // anchor in column flows
}

private fun lerp(a: Color, b: Color, t: Float): Color = Color(
    red = a.red + (b.red - a.red) * t,
    green = a.green + (b.green - a.green) * t,
    blue = a.blue + (b.blue - a.blue) * t,
    alpha = a.alpha + (b.alpha - a.alpha) * t,
)

@Composable
@Suppress("unused")
private fun _stroke(): Stroke = Stroke(2f)
