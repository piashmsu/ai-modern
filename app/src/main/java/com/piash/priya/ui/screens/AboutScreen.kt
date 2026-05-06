package com.piash.priya.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun AboutScreen() {
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text("Priya", style = MaterialTheme.typography.headlineMedium)
        Text("Vibe Modern AI Voice Assistant", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))
        Text(
            "Designed by Shorif Uddin Piash. " +
                "Bangla-aware girlfriend-style assistant that listens, responds, and " +
                "(with proper permissions) controls your phone — open apps, send SMS, " +
                "and act on what's on screen.",
            style = MaterialTheme.typography.bodyMedium,
        )
        Spacer(Modifier.height(8.dp))
        Text("Capabilities", style = MaterialTheme.typography.titleMedium)
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
        Spacer(Modifier.height(16.dp))
        Text("v1.0.0", style = MaterialTheme.typography.labelSmall)
    }
}
