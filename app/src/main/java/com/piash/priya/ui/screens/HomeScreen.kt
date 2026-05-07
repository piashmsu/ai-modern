package com.piash.priya.ui.screens

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import com.piash.priya.PriyaApplication
import com.piash.priya.services.OverlayService
import com.piash.priya.services.PriyaForegroundService
import com.piash.priya.ui.components.AssistantOrb
import com.piash.priya.ui.components.GlassCard
import com.piash.priya.ui.components.VoiceWaveform
import com.piash.priya.ui.theme.VibeCyan
import com.piash.priya.ui.theme.VibeMagenta
import com.piash.priya.ui.theme.VibeMuted
import com.piash.priya.ui.theme.VibePink
import com.piash.priya.ui.theme.VibeViolet
import com.piash.priya.voice.VoicePipeline

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun HomeScreen() {
    val context = LocalContext.current
    val app = remember { PriyaApplication.get() }
    val state by app.voicePipeline.state.collectAsState()
    val transcript by app.voicePipeline.liveTranscript.collectAsState()
    val reply by app.voicePipeline.liveReply.collectAsState()
    val pipelineError by app.voicePipeline.lastError.collectAsState()
    val chatError by app.chatEngine.lastError.collectAsState()
    val settings by app.settings.state.collectAsState()
    val configured = remember(settings) { app.providers.isConfigured() }

    val perms = rememberMultiplePermissionsState(
        permissions = listOfNotNull(
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.POST_NOTIFICATIONS.takeIf {
                android.os.Build.VERSION.SDK_INT >= 33
            }
        )
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Spacer(Modifier.height(8.dp))
        TitleHeader()

        // Centered animated orb that reflects pipeline state.
        Box(
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
            contentAlignment = Alignment.Center,
        ) {
            AssistantOrb(state = state, size = 200.dp)
        }

        StatePill(state)

        AnimatedVisibility(visible = state == VoicePipeline.State.LISTENING) {
            VoiceWaveform(color = VibeCyan, accent = VibePink, modifier = Modifier.fillMaxWidth().height(48.dp))
        }
        AnimatedVisibility(visible = state == VoicePipeline.State.SPEAKING) {
            VoiceWaveform(color = VibePink, accent = VibeMagenta, modifier = Modifier.fillMaxWidth().height(48.dp))
        }

        // Surfaced errors so failures are never silent.
        val errorText = chatError ?: pipelineError
        AnimatedVisibility(visible = errorText != null) {
            GlassCard(
                accent = Brush.linearGradient(listOf(Color(0xFFFF6B7A), Color(0xFFFF9A5A))),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Warning, null, tint = Color(0xFFFF6B7A))
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text("কিছু একটা ভুল হয়েছে", style = MaterialTheme.typography.titleSmall, color = Color(0xFFFFB4BC))
                        Text(
                            errorText.orEmpty(),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                    IconButton(onClick = {
                        app.chatEngine.clearError()
                        app.voicePipeline.clearError()
                    }) { Icon(Icons.Filled.Close, "Dismiss", tint = VibeMuted) }
                }
            }
        }

        ActivateCard(
            state = state,
            live = settings.priyaLiveMode,
            configured = configured,
            onActivate = {
                if (!perms.allPermissionsGranted) {
                    perms.launchMultiplePermissionRequest()
                    return@ActivateCard
                }
                if (!configured) return@ActivateCard
                app.settings.update { it.copy(priyaLiveMode = true) }
                PriyaForegroundService.start(context)
                if (settings.overlayEnabled && Settings.canDrawOverlays(context)) {
                    OverlayService.start(context)
                }
            },
            onStop = {
                app.settings.update { it.copy(priyaLiveMode = false) }
                PriyaForegroundService.stop(context)
                OverlayService.stop(context)
            }
        )

        if (!configured) {
            GlassCard(
                accent = Brush.linearGradient(listOf(VibeMagenta, VibePink)),
            ) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.AutoAwesome, null, tint = VibePink)
                        Spacer(Modifier.width(8.dp))
                        Text("API key সেট করা নেই", style = MaterialTheme.typography.titleMedium)
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        app.providers.missingKeyMessage(),
                        style = MaterialTheme.typography.bodyMedium,
                        color = VibeMuted,
                    )
                }
            }
        }

        OverlayGlass(
            enabled = settings.overlayEnabled,
            granted = Settings.canDrawOverlays(context),
            onToggle = { wantOn ->
                if (wantOn && !Settings.canDrawOverlays(context)) {
                    val intent = Intent(
                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:${context.packageName}")
                    ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(intent)
                    return@OverlayGlass
                }
                app.settings.update { it.copy(overlayEnabled = wantOn) }
                if (wantOn && settings.priyaLiveMode) OverlayService.start(context) else OverlayService.stop(context)
            }
        )

        if (transcript.isNotBlank() || reply.isNotBlank()) {
            GlassCard {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (transcript.isNotBlank()) {
                        Text("আপনি", style = MaterialTheme.typography.labelMedium, color = VibeCyan, fontWeight = FontWeight.SemiBold)
                        Text(transcript, style = MaterialTheme.typography.bodyMedium)
                    }
                    if (reply.isNotBlank()) {
                        Text("Priya", style = MaterialTheme.typography.labelMedium, color = VibePink, fontWeight = FontWeight.SemiBold)
                        Text(reply, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }

        Spacer(Modifier.height(40.dp))
    }
}

@Composable
private fun TitleHeader() {
    Column {
        Text(
            "PRIYA",
            style = MaterialTheme.typography.displaySmall.copy(
                brush = Brush.linearGradient(listOf(VibePink, VibeMagenta, VibeViolet))
            ),
            fontWeight = FontWeight.Black,
        )
        Text(
            "Vibe Modern AI Voice Assistant • Bangla + English",
            style = MaterialTheme.typography.bodyMedium,
            color = VibeMuted,
        )
    }
}

@Composable
private fun StatePill(state: VoicePipeline.State) {
    val (label, color) = when (state) {
        VoicePipeline.State.IDLE -> "Idle • say something or activate" to VibeMuted
        VoicePipeline.State.LISTENING -> "শুনছি…" to VibeCyan
        VoicePipeline.State.THINKING -> "ভাবছি…" to VibeMagenta
        VoicePipeline.State.SPEAKING -> "বলছি…" to VibePink
        VoicePipeline.State.ERROR -> "Error" to Color(0xFFFF8A8A)
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(50))
            .background(color.copy(alpha = 0.10f))
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .clip(RoundedCornerShape(50))
                .background(color)
        )
        Spacer(Modifier.width(10.dp))
        Text(label, color = color, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun ActivateCard(
    state: VoicePipeline.State,
    live: Boolean,
    configured: Boolean,
    onActivate: () -> Unit,
    onStop: () -> Unit,
) {
    GlassCard(
        accent = Brush.linearGradient(listOf(VibePink, VibeViolet, VibeCyan)),
        contentPadding = 22.dp,
    ) {
        Column {
            Text(
                if (live) "Live mode চালু" else "Activate Priya",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                if (live) "Foreground service চলছে। যেকোনো অ্যাপে গেলেও Priya শুনছে।"
                else "Mic on, continuous loop, barge-in interruption।",
                style = MaterialTheme.typography.bodyMedium,
                color = VibeMuted,
            )
            Spacer(Modifier.height(16.dp))
            if (live) {
                Button(
                    onClick = onStop,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.White.copy(alpha = 0.95f),
                        contentColor = Color(0xFF1A0830),
                    ),
                    modifier = Modifier.fillMaxWidth().height(54.dp),
                    shape = RoundedCornerShape(16.dp),
                ) {
                    Icon(Icons.Filled.PowerSettingsNew, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Live mode বন্ধ করো", fontWeight = FontWeight.SemiBold)
                }
            } else {
                val brush = if (configured) Brush.linearGradient(listOf(VibePink, VibeMagenta, VibeViolet))
                            else Brush.linearGradient(listOf(VibeMuted.copy(alpha = 0.5f), VibeMuted.copy(alpha = 0.3f)))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .clip(RoundedCornerShape(18.dp))
                        .background(brush)
                ) {
                    Button(
                        onClick = onActivate,
                        enabled = configured,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color.Transparent,
                            contentColor = Color.White,
                            disabledContainerColor = Color.Transparent,
                            disabledContentColor = Color.White.copy(alpha = 0.7f),
                        ),
                        modifier = Modifier.fillMaxSize(),
                        shape = RoundedCornerShape(18.dp),
                        elevation = null,
                    ) {
                        Icon(Icons.Filled.Mic, null)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            if (configured) "Activate Priya — live mode" else "Settings এ API key দিন",
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }
            if (state == VoicePipeline.State.ERROR) {
                Spacer(Modifier.height(8.dp))
                Text("Pipeline error — উপরে details দেখুন।", color = Color(0xFFFF8A8A), style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun OverlayGlass(enabled: Boolean, granted: Boolean, onToggle: (Boolean) -> Unit) {
    GlassCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.OpenInNew, null, tint = VibeCyan)
                    Spacer(Modifier.width(8.dp))
                    Text("Floating overlay", style = MaterialTheme.typography.titleMedium)
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    if (granted) "Live mode চালু থাকলে যেকোনো অ্যাপের উপর Priya bubble দেখাবে।"
                    else "অনুমতি দরকার — \"Display over other apps\"।",
                    style = MaterialTheme.typography.bodySmall,
                    color = VibeMuted,
                )
            }
            Switch(
                checked = enabled && granted,
                onCheckedChange = onToggle,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.White,
                    checkedTrackColor = VibePink,
                    uncheckedThumbColor = VibeMuted,
                    uncheckedTrackColor = Color(0xFF221833),
                ),
            )
        }
    }
}

@Suppress("unused")
private fun _refsForR8() = Icons.Filled.Bolt to Icons.Filled.GraphicEq
