package com.piash.priya.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.piash.priya.ui.components.GlassCard
import com.piash.priya.ui.theme.VibeMagenta
import com.piash.priya.ui.theme.VibeMuted
import com.piash.priya.ui.theme.VibePink
import com.piash.priya.ui.theme.VibeViolet

@Composable
fun AboutScreen() {
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Spacer(Modifier.height(8.dp))
        Text(
            "PRIYA",
            style = MaterialTheme.typography.displaySmall.copy(
                brush = Brush.linearGradient(listOf(VibePink, VibeMagenta, VibeViolet))
            ),
            fontWeight = FontWeight.Black,
        )
        Text("Vibe Modern AI Voice Assistant", style = MaterialTheme.typography.titleMedium, color = VibeMuted)
        Spacer(Modifier.height(8.dp))
        GlassCard {
            Text(
                "Designed by Shorif Uddin Piash. Bangla-aware girlfriend-style assistant " +
                    "that listens, responds, and (with proper permissions) controls your phone — " +
                    "open apps, send SMS, and act on what's on screen.",
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        GlassCard {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Capabilities", style = MaterialTheme.typography.titleMedium, color = VibePink, fontWeight = FontWeight.SemiBold)
                Text(
                    "• OpenAI-compatible / Groq / Gemini LLM (your key, your model)\n" +
                        "• Android speech recognizer + Whisper hook (cloud)\n" +
                        "• Android TTS + ElevenLabs / OpenAI-compatible TTS\n" +
                        "• Barge-in interruption (user-talking → TTS stops)\n" +
                        "• Persistent foreground service + draggable floating bubble\n" +
                        "• Accessibility-driven screen analysis & gesture automation\n" +
                        "• SMS send/read, app launcher, root shell\n",
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        Text("v1.0.1", style = MaterialTheme.typography.labelSmall, color = VibeMuted)
        Spacer(Modifier.height(40.dp))
    }
}
