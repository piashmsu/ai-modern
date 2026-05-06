package com.piash.priya.ui.screens

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.SmartToy
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
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Spacer(Modifier.height(8.dp))
        HeroCard(
            state = state,
            live = settings.priyaLiveMode,
            configured = configured,
            onActivate = {
                if (!perms.allPermissionsGranted) {
                    perms.launchMultiplePermissionRequest()
                    return@HeroCard
                }
                if (!configured) return@HeroCard
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
            ElevatedCard {
                Column(Modifier.padding(16.dp)) {
                    Text("API key সেট করা নেই", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        app.providers.missingKeyMessage(),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        OverlayCard(
            enabled = settings.overlayEnabled,
            granted = Settings.canDrawOverlays(context),
            onToggle = { wantOn ->
                if (wantOn && !Settings.canDrawOverlays(context)) {
                    val intent = Intent(
                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:${context.packageName}")
                    ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(intent)
                    return@OverlayCard
                }
                app.settings.update { it.copy(overlayEnabled = wantOn) }
                if (wantOn && settings.priyaLiveMode) OverlayService.start(context) else OverlayService.stop(context)
            }
        )

        if (transcript.isNotBlank() || reply.isNotBlank()) {
            ElevatedCard {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (transcript.isNotBlank()) {
                        Text("আপনি", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                        Text(transcript, style = MaterialTheme.typography.bodyMedium)
                    }
                    if (reply.isNotBlank()) {
                        Text("Priya", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.tertiary)
                        Text(reply, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
    }
}

@Composable
private fun HeroCard(
    state: VoicePipeline.State,
    live: Boolean,
    configured: Boolean,
    onActivate: () -> Unit,
    onStop: () -> Unit,
) {
    val gradient = Brush.linearGradient(listOf(VibePink, VibeViolet))
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        color = Color.Transparent,
    ) {
        Box(modifier = Modifier.background(gradient).padding(24.dp)) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.SmartToy, null, tint = Color.White)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "Priya — your AI girlfriend",
                        style = MaterialTheme.typography.titleLarge,
                        color = Color.White,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    "Vibe Modern AI Voice Assistant • Bangla + English",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.85f)
                )
                Spacer(Modifier.height(16.dp))
                StateRow(state)
                Spacer(Modifier.height(16.dp))
                if (live) {
                    Button(
                        onClick = onStop,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color.White,
                            contentColor = Color.Black,
                        ),
                        modifier = Modifier.fillMaxWidth().height(54.dp).clip(RoundedCornerShape(16.dp))
                    ) {
                        Icon(Icons.Filled.PowerSettingsNew, null)
                        Spacer(Modifier.width(8.dp))
                        Text("Live mode বন্ধ করো")
                    }
                } else {
                    Button(
                        onClick = onActivate,
                        enabled = configured,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color.White,
                            contentColor = Color.Black,
                            disabledContainerColor = Color.White.copy(alpha = 0.45f),
                            disabledContentColor = Color.Black.copy(alpha = 0.6f),
                        ),
                        modifier = Modifier.fillMaxWidth().height(54.dp).clip(RoundedCornerShape(16.dp))
                    ) {
                        Icon(Icons.Filled.Mic, null)
                        Spacer(Modifier.width(8.dp))
                        Text("Activate Priya — live mode")
                    }
                }
            }
        }
    }
}

@Composable
private fun StateRow(state: VoicePipeline.State) {
    val (label, icon, color) = when (state) {
        VoicePipeline.State.IDLE -> Triple("Idle", Icons.Filled.Bolt, Color.White.copy(alpha = 0.7f))
        VoicePipeline.State.LISTENING -> Triple("শুনছি…", Icons.Filled.Mic, Color(0xFFA5F3CC))
        VoicePipeline.State.THINKING -> Triple("ভাবছি…", Icons.Filled.GraphicEq, Color(0xFFFFE680))
        VoicePipeline.State.SPEAKING -> Triple("বলছি…", Icons.Filled.GraphicEq, Color(0xFF8FD6FF))
        VoicePipeline.State.ERROR -> Triple("Error", Icons.Filled.Bolt, Color(0xFFFF8A8A))
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = color)
        Spacer(Modifier.width(8.dp))
        Text(label, color = color, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun OverlayCard(enabled: Boolean, granted: Boolean, onToggle: (Boolean) -> Unit) {
    ElevatedCard {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text("Floating overlay", style = MaterialTheme.typography.titleMedium)
                Text(
                    if (granted) "Live mode চালু থাকলে যেকোনো অ্যাপের উপর Priya bubble দেখাবে।"
                    else "অনুমতি দরকার — \"Display over other apps\"।",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Switch(checked = enabled, onCheckedChange = onToggle)
        }
    }
}
