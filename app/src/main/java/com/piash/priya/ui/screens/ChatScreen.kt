package com.piash.priya.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.piash.priya.PriyaApplication
import com.piash.priya.ai.ChatMessage
import com.piash.priya.ai.Role
import com.piash.priya.ui.components.GlassCard
import com.piash.priya.ui.theme.VibeCyan
import com.piash.priya.ui.theme.VibeMagenta
import com.piash.priya.ui.theme.VibeMuted
import com.piash.priya.ui.theme.VibePink
import com.piash.priya.ui.theme.VibeViolet

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen() {
    val app = remember { PriyaApplication.get() }
    val transcript by app.chatEngine.transcript.collectAsState()
    val streaming by app.chatEngine.streaming.collectAsState()
    val live by app.voicePipeline.liveReply.collectAsState()
    val chatError by app.chatEngine.lastError.collectAsState()
    val pipelineError by app.voicePipeline.lastError.collectAsState()
    var input by rememberSaveable { mutableStateOf("") }
    val listState = rememberLazyListState()

    LaunchedEffect(transcript.size, live) {
        val total = transcript.size + (if (streaming && live.isNotBlank()) 1 else 0)
        if (total > 0) listState.animateScrollToItem(total - 1)
    }

    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 8.dp)) {
        val errorText = chatError ?: pipelineError
        AnimatedVisibility(visible = errorText != null) {
            Column {
                GlassCard(
                    accent = Brush.linearGradient(listOf(Color(0xFFFF6B7A), Color(0xFFFF9A5A))),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Warning, null, tint = Color(0xFFFF6B7A))
                        Spacer(Modifier.width(10.dp))
                        Text(
                            errorText.orEmpty(),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f),
                        )
                        IconButton(onClick = {
                            app.chatEngine.clearError()
                            app.voicePipeline.clearError()
                        }) { Icon(Icons.Filled.Close, "Dismiss", tint = VibeMuted) }
                    }
                }
                Spacer(Modifier.height(8.dp))
            }
        }

        if (transcript.isEmpty() && !streaming) {
            Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        "Priya-র সাথে কথা বলুন",
                        style = MaterialTheme.typography.headlineSmall.copy(
                            brush = Brush.linearGradient(listOf(VibePink, VibeMagenta, VibeViolet))
                        ),
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Type করুন বা Home → Activate Priya করে voice দিয়ে কথা বলুন।",
                        style = MaterialTheme.typography.bodyMedium,
                        color = VibeMuted,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(transcript) { msg -> Bubble(msg) }
                if (streaming && live.isNotBlank()) {
                    item { Bubble(ChatMessage(Role.ASSISTANT, live)) }
                }
                if (streaming && live.isBlank()) {
                    item { TypingBubble() }
                }
            }
        }

        Spacer(Modifier.height(8.dp))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value = input,
                onValueChange = { input = it },
                placeholder = { Text("Priya-কে কিছু বলো…", color = VibeMuted) },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(20.dp),
                maxLines = 4,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = VibePink,
                    unfocusedBorderColor = VibeMuted.copy(alpha = 0.6f),
                    cursorColor = VibePink,
                    focusedTextColor = MaterialTheme.colorScheme.onSurface,
                    unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                ),
            )
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(Brush.linearGradient(listOf(VibePink, VibeMagenta, VibeViolet)))
            ) {
                IconButton(
                    onClick = {
                        val text = input.trim()
                        if (text.isBlank()) return@IconButton
                        input = ""
                        // Route through VoicePipeline so the reply also speaks via TTS.
                        app.voicePipeline.submit(text)
                    },
                    modifier = Modifier.fillMaxSize()
                ) {
                    Icon(Icons.Filled.Send, contentDescription = "Send", tint = Color.White)
                }
            }
        }
    }
}

@Composable
private fun Bubble(msg: ChatMessage) {
    val isUser = msg.role == Role.USER
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
    ) {
        val shape = RoundedCornerShape(
            topStart = 18.dp, topEnd = 18.dp,
            bottomStart = if (isUser) 18.dp else 4.dp,
            bottomEnd = if (isUser) 4.dp else 18.dp
        )
        Box(
            modifier = Modifier
                .widthIn(max = 320.dp)
                .clip(shape)
                .background(
                    if (isUser) Brush.linearGradient(listOf(VibePink, VibeMagenta))
                    else Brush.linearGradient(
                        listOf(
                            Color.White.copy(alpha = 0.06f),
                            Color.White.copy(alpha = 0.03f),
                        )
                    )
                )
                .padding(horizontal = 14.dp, vertical = 10.dp),
        ) {
            Column {
                Text(
                    text = if (isUser) "আপনি" else "Priya",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (isUser) Color.White.copy(alpha = 0.85f) else VibeCyan,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = msg.content,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (isUser) Color.White else MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }
}

@Composable
private fun TypingBubble() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Start,
    ) {
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(18.dp))
                .background(Color.White.copy(alpha = 0.05f))
                .padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            Text("…", color = VibeMuted)
        }
    }
}
