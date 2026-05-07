package com.piash.priya.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.piash.priya.PriyaApplication
import com.piash.priya.services.PriyaAccessibilityService
import com.piash.priya.ui.components.GlassCard
import com.piash.priya.ui.theme.VibeCyan
import com.piash.priya.ui.theme.VibeMagenta
import com.piash.priya.ui.theme.VibeMuted
import com.piash.priya.ui.theme.VibePink
import com.piash.priya.util.AuditLog
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * One-tab dashboard for Priya's auxiliary capabilities:
 *  - Audit log of actions she performed (apps opened, SMS sent, screen reads).
 *  - Ad-hoc screen-snapshot inspector (uses the accessibility tree).
 *  - Creator-mode quick prompts that route through the chat engine.
 */
@Composable
fun ToolsScreen() {
    val context = LocalContext.current
    val app = remember { PriyaApplication.get() }
    val auditEntries by AuditLog.entries.collectAsState()
    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Spacer(Modifier.height(4.dp))

        Text(
            "Tools & Audit",
            style = MaterialTheme.typography.headlineSmall,
            color = VibePink,
            fontWeight = FontWeight.Bold,
        )

        // ── Screen snapshot tool ──
        ScreenSnapshotCard()

        // ── Creator mode quick prompts ──
        CreatorPromptsCard(onPrompt = { prompt -> app.voicePipeline.submit(prompt) })

        // ── Audit log ──
        GlassCard {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "Action history",
                        style = MaterialTheme.typography.titleMedium,
                        color = VibePink,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(Modifier.weight(1f))
                    Text("${auditEntries.size} actions", color = VibeMuted, style = MaterialTheme.typography.labelMedium)
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    "Priya যা যা করেছে — apps খোলা, SMS পাঠানো, screen পড়া, root command।",
                    style = MaterialTheme.typography.bodySmall,
                    color = VibeMuted,
                )
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilledTonalButton(onClick = { copyAuditLog(context) }) {
                        Icon(Icons.Filled.ContentCopy, null)
                        Spacer(Modifier.width(6.dp))
                        Text("Copy")
                    }
                    OutlinedButton(onClick = { AuditLog.clear() }) {
                        Icon(Icons.Filled.Delete, null)
                        Spacer(Modifier.width(6.dp))
                        Text("Clear")
                    }
                }
            }
        }

        if (auditEntries.isEmpty()) {
            Text(
                "এখনো কোনো action record নেই।",
                color = VibeMuted,
                style = MaterialTheme.typography.bodyMedium,
            )
        } else {
            // Use a non-scrolling list of cards inside the parent vertical scroll
            // so audit entries appear inline without nested scroll containers.
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                auditEntries.asReversed().take(50).forEach { entry -> AuditRow(entry) }
            }
        }

        Spacer(Modifier.height(48.dp))
    }
}

@Composable
private fun ScreenSnapshotCard() {
    val context = LocalContext.current
    var snapshot by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }

    GlassCard {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Visibility, null, tint = VibeCyan)
                Spacer(Modifier.width(8.dp))
                Text(
                    "Screen snapshot (text-only)",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            Spacer(Modifier.height(4.dp))
            Text(
                "Foreground app-এর accessibility tree থেকে text বের করে দেখাবে। Priya screen analyse করতে এটাই ব্যবহার করে।",
                style = MaterialTheme.typography.bodySmall,
                color = VibeMuted,
            )
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilledTonalButton(onClick = {
                    val service = PriyaAccessibilityService.get()
                    if (service == null) {
                        error = "Accessibility service চলছে না — Settings → Accessibility-এ enable করুন।"
                        snapshot = null
                    } else {
                        val tree = service.snapshotForeground()
                        snapshot = tree.ifBlank { "<empty — কোনো text-যুক্ত foreground node পাওয়া যায়নি>" }
                        error = null
                        AuditLog.record(AuditLog.Kind.SCREEN_SNAPSHOT, "Captured screen text", detail = tree.take(120))
                    }
                }) {
                    Icon(Icons.Filled.AutoAwesome, null)
                    Spacer(Modifier.width(6.dp))
                    Text("Capture")
                }
                snapshot?.let {
                    OutlinedButton(onClick = {
                        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        cm.setPrimaryClip(ClipData.newPlainText("screen-snapshot", it))
                        Toast.makeText(context, "Snapshot copied", Toast.LENGTH_SHORT).show()
                    }) {
                        Icon(Icons.Filled.ContentCopy, null)
                        Spacer(Modifier.width(6.dp))
                        Text("Copy")
                    }
                }
            }
            error?.let {
                Spacer(Modifier.height(6.dp))
                Text(it, color = androidx.compose.ui.graphics.Color(0xFFFFB347), style = MaterialTheme.typography.bodySmall)
            }
            snapshot?.let {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = it.take(2000),
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }
}

@Composable
private fun CreatorPromptsCard(onPrompt: (String) -> Unit) {
    var topic by remember { mutableStateOf("") }
    GlassCard {
        Column {
            Text(
                "Creator quick prompts",
                style = MaterialTheme.typography.titleMedium,
                color = VibeMagenta,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "Topic দিন, Priya viral caption / hashtag / voiceover script / thumbnail text generate করবে।",
                style = MaterialTheme.typography.bodySmall,
                color = VibeMuted,
            )
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                value = topic,
                onValueChange = { topic = it },
                label = { Text("Topic / video idea") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = false,
                maxLines = 3,
            )
            Spacer(Modifier.height(10.dp))
            val t = topic.trim()
            val enabled = t.isNotBlank()
            CreatorButton("Caption (3 versions)", enabled) {
                onPrompt("Generate 3 short, hook-first social media captions for: \"$t\". Include emojis sparingly.")
            }
            CreatorButton("Viral hashtags", enabled) {
                onPrompt("Generate 15 trending viral hashtags (one line, space-separated, no #️⃣ filler) for: \"$t\".")
            }
            CreatorButton("Voiceover script (45s)", enabled) {
                onPrompt("Write a punchy 45-second voiceover script for a Reel/Short about: \"$t\". Hook in first 3 seconds. Conversational tone.")
            }
            CreatorButton("Thumbnail text (5 options)", enabled) {
                onPrompt("Give 5 punchy 3-6 word thumbnail texts for: \"$t\". Each on its own line.")
            }
            CreatorButton("YouTube title + description", enabled) {
                onPrompt("Generate one click-worthy YouTube title and a 4-sentence description for: \"$t\". SEO-aware.")
            }
        }
    }
}

@Composable
private fun CreatorButton(label: String, enabled: Boolean, onClick: () -> Unit) {
    FilledTonalButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
    ) { Text(label) }
}

@Composable
private fun AuditRow(entry: AuditLog.Entry) {
    val color = when (entry.kind) {
        AuditLog.Kind.APP_LAUNCH -> VibeCyan
        AuditLog.Kind.SMS_SENT -> VibePink
        AuditLog.Kind.ROOT_CMD -> androidx.compose.ui.graphics.Color(0xFFFFB347)
        AuditLog.Kind.NOTIF_READ -> VibeMagenta
        AuditLog.Kind.SCREEN_SNAPSHOT -> VibeMuted
        AuditLog.Kind.CONFIRMATION -> VibeMuted
    }
    val time = TIME.format(Date(entry.tsMillis))
    GlassCard(contentPadding = 12.dp) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(entry.kind.name.replace('_', ' '), color = color, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.weight(1f))
                Text(time, color = VibeMuted, style = MaterialTheme.typography.labelSmall)
            }
            Spacer(Modifier.height(2.dp))
            Text(entry.summary, style = MaterialTheme.typography.bodyMedium)
            if (entry.detail.isNotBlank()) {
                Text(entry.detail, style = MaterialTheme.typography.bodySmall, color = VibeMuted)
            }
        }
    }
}

private fun copyAuditLog(context: Context) {
    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    cm.setPrimaryClip(ClipData.newPlainText("priya-audit", AuditLog.dump()))
    Toast.makeText(context, "Audit log copied", Toast.LENGTH_SHORT).show()
}

private val TIME = SimpleDateFormat("MM-dd HH:mm:ss", Locale.US)
