package com.piash.priya.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.piash.priya.ui.theme.VibeCyan
import com.piash.priya.ui.theme.VibeMagenta
import com.piash.priya.ui.theme.VibePink
import com.piash.priya.ui.theme.VibeViolet
import com.piash.priya.voice.VoicePipeline
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Animated assistant orb whose colour and motion reflect the live voice
 * pipeline state. Idle = slow violet swirl. Listening = cyan ripple.
 * Thinking = pink double-pulse. Speaking = magenta breathe.
 */
@Composable
fun AssistantOrb(
    state: VoicePipeline.State,
    modifier: Modifier = Modifier,
    size: Dp = 180.dp,
) {
    val transition = rememberInfiniteTransition(label = "orb")

    val pulseSpeed = when (state) {
        VoicePipeline.State.IDLE -> 4200
        VoicePipeline.State.LISTENING -> 1200
        VoicePipeline.State.THINKING -> 900
        VoicePipeline.State.SPEAKING -> 1700
        VoicePipeline.State.ERROR -> 2400
    }
    val pulse by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(pulseSpeed, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "orb-pulse",
    )
    val rotate by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(11000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "orb-rotate",
    )

    val (coreA, coreB, halo) = remember(state) {
        when (state) {
            VoicePipeline.State.IDLE -> Triple(VibeViolet, VibePink, VibeViolet)
            VoicePipeline.State.LISTENING -> Triple(VibeCyan, VibePink, VibeCyan)
            VoicePipeline.State.THINKING -> Triple(VibeMagenta, VibePink, VibeMagenta)
            VoicePipeline.State.SPEAKING -> Triple(VibePink, VibeMagenta, VibePink)
            VoicePipeline.State.ERROR -> Triple(Color(0xFFFF5577), Color(0xFFFF8855), Color(0xFFFF5577))
        }
    }

    Box(modifier = modifier.size(size)) {
        // Soft outer halo.
        Canvas(modifier = Modifier.size(size).blur(40.dp)) {
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(halo.copy(alpha = 0.55f), halo.copy(alpha = 0f)),
                ),
                radius = (this.size.minDimension / 2f) * (0.85f + pulse * 0.15f),
            )
        }
        // Core gradient sphere with rotating highlight.
        Canvas(modifier = Modifier.size(size).blur(8.dp)) {
            val r = this.size.minDimension / 2f
            val center = Offset(this.size.width / 2f, this.size.height / 2f)
            // base core
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(coreA, coreB.copy(alpha = 0.6f), Color.Transparent),
                    center = center,
                    radius = r * (0.78f + pulse * 0.06f),
                ),
                radius = r * 0.78f,
                center = center,
            )
            // travelling highlight
            val angle = rotate * 2f * PI.toFloat()
            val hx = center.x + cos(angle) * r * 0.30f
            val hy = center.y + sin(angle) * r * 0.30f
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(Color.White.copy(alpha = 0.55f), Color.Transparent),
                    center = Offset(hx, hy),
                    radius = r * 0.45f,
                ),
                radius = r * 0.45f,
                center = Offset(hx, hy),
            )
        }
        // Crisp neon ring.
        Canvas(modifier = Modifier.size(size)) {
            val r = this.size.minDimension / 2f
            val center = Offset(this.size.width / 2f, this.size.height / 2f)
            drawCircle(
                color = halo.copy(alpha = 0.45f + pulse * 0.30f),
                radius = r * (0.85f + pulse * 0.05f),
                center = center,
                style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2f),
            )
        }
    }
}
