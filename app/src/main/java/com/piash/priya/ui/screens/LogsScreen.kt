package com.piash.priya.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.piash.priya.ui.components.GlassCard
import com.piash.priya.ui.theme.VibeCyan
import com.piash.priya.ui.theme.VibeMagenta
import com.piash.priya.ui.theme.VibeMuted
import com.piash.priya.ui.theme.VibePink
import com.piash.priya.util.DebugLog

@Composable
fun LogsScreen() {
    val context = LocalContext.current
    val entries by DebugLog.entries.collectAsState()
    val listState = rememberLazyListState()

    LaunchedEffect(entries.size) {
        if (entries.isNotEmpty()) listState.animateScrollToItem(entries.size - 1)
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Spacer(Modifier.height(8.dp))
        GlassCard {
            Column {
                Text(
                    "Live debug log",
                    style = MaterialTheme.typography.titleMedium,
                    color = VibePink,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "STT, LLM, TTS — সব events এখানে দেখাবে। Copy করে আমাকে paste করুন যদি bug investigate করতে হয়।",
                    style = MaterialTheme.typography.bodySmall,
                    color = VibeMuted,
                )
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilledTonalButton(onClick = { copyAll(context) }) {
                        Icon(Icons.Filled.ContentCopy, null)
                        Spacer(Modifier.width(6.dp))
                        Text("Copy all")
                    }
                    OutlinedButton(onClick = { DebugLog.clear() }) {
                        Icon(Icons.Filled.Delete, null)
                        Spacer(Modifier.width(6.dp))
                        Text("Clear")
                    }
                    Spacer(Modifier.weight(1f))
                    Text("${entries.size} entries", color = VibeMuted, style = MaterialTheme.typography.labelMedium)
                }
            }
        }

        if (entries.isEmpty()) {
            Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                Text(
                    "এখনো কোনো log নেই। Chat-এ কিছু type করুন বা Activate Priya চাপুন।",
                    color = VibeMuted,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color.Black.copy(alpha = 0.35f))
                    .padding(8.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                items(entries) { entry ->
                    LogRow(entry)
                }
            }
        }
    }
}

@Composable
private fun LogRow(entry: DebugLog.Entry) {
    val color = when (entry.level) {
        DebugLog.Level.DEBUG -> VibeMuted
        DebugLog.Level.INFO -> VibeCyan
        DebugLog.Level.WARN -> Color(0xFFFFB347)
        DebugLog.Level.ERROR -> Color(0xFFFF6B7A)
    }
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 1.dp),
    ) {
        Text(
            text = entry.format(),
            color = color,
            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
        )
    }
}

private fun copyAll(context: Context) {
    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    cm.setPrimaryClip(ClipData.newPlainText("priya-logs", DebugLog.dump()))
    Toast.makeText(context, "Logs copied to clipboard", Toast.LENGTH_SHORT).show()
}

@Suppress("unused")
private fun _refsForR8() = listOf(VibeMagenta)
