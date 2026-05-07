package com.piash.priya.ui.screens

import android.content.Intent
import android.net.Uri
import android.provider.Settings as AndroidSettings
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.piash.priya.PriyaApplication
import com.piash.priya.ai.ChatMessage
import com.piash.priya.ai.Role
import com.piash.priya.automation.AccessibilityEnabler
import com.piash.priya.data.ProviderId
import com.piash.priya.data.SettingsRepository
import com.piash.priya.data.SttBackend
import com.piash.priya.data.TtsBackend
import com.piash.priya.ui.theme.VibeMuted
import com.piash.priya.ui.theme.VibePink
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen() {
    val context = LocalContext.current
    val app = remember { PriyaApplication.get() }
    val s by app.settings.state.collectAsState()
    val keys = remember { app.secureKeys }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        SectionTitle("AI Provider")
        ProviderPicker(
            selected = s.activeProvider,
            onSelected = { p -> app.settings.update { it.copy(activeProvider = p) } }
        )

        when (s.activeProvider) {
            ProviderId.OPENAI -> {
                FormText("Base URL", s.openAiBaseUrl) { v -> app.settings.update { it.copy(openAiBaseUrl = v) } }
                FormText("Model", s.openAiModel) { v -> app.settings.update { it.copy(openAiModel = v) } }
                FormSecret("OpenAI API Key", SettingsRepository.SECRET_OPENAI, keys)
            }
            ProviderId.GROQ -> {
                FormText("Model", s.groqModel) { v -> app.settings.update { it.copy(groqModel = v) } }
                FormSecret("Groq API Key", SettingsRepository.SECRET_GROQ, keys)
            }
            ProviderId.GEMINI -> {
                FormText("Model", s.geminiModel) { v -> app.settings.update { it.copy(geminiModel = v) } }
                FormSecret("Gemini API Key", SettingsRepository.SECRET_GEMINI, keys)
            }
        }

        TestConnectionButton()

        Divider()
        SectionTitle("Voice — STT (যা শুনবে)")
        SttPicker(
            selected = s.sttBackend,
            onSelected = { v -> app.settings.update { it.copy(sttBackend = v) } }
        )
        if (s.sttBackend == SttBackend.GROQ_WHISPER) {
            FormText("Whisper model", s.groqWhisperModel) { v -> app.settings.update { it.copy(groqWhisperModel = v) } }
        }
        FormText("Language tag (BCP-47)", s.languageTag) { v -> app.settings.update { it.copy(languageTag = v) } }

        Divider()
        SectionTitle("Voice — TTS (যা বলবে)")
        TtsPicker(
            selected = s.ttsBackend,
            onSelected = { v -> app.settings.update { it.copy(ttsBackend = v) } }
        )
        if (s.ttsBackend != TtsBackend.ANDROID) {
            FormText("TTS Base URL", s.ttsBaseUrl) { v -> app.settings.update { it.copy(ttsBaseUrl = v) } }
            FormText("TTS Model", s.ttsModel) { v -> app.settings.update { it.copy(ttsModel = v) } }
            FormText("Voice ID", s.ttsVoiceId) { v -> app.settings.update { it.copy(ttsVoiceId = v) } }
            FormSecret("TTS API Key", SettingsRepository.SECRET_TTS, keys)
        } else {
            FormText("Voice (Android engine voice name, optional)", s.ttsVoiceId) {
                v -> app.settings.update { it.copy(ttsVoiceId = v) }
            }
        }

        Divider()
        SectionTitle("Personality")
        FormText("Your name (Priya আপনাকে যে নামে ডাকবে)", s.userName) {
            v -> app.settings.update { it.copy(userName = v) }
        }
        Text("Personality intensity: ${s.personalityIntensity}%")
        Slider(
            value = s.personalityIntensity.toFloat(),
            onValueChange = { v -> app.settings.update { it.copy(personalityIntensity = v.toInt()) } },
            valueRange = 0f..100f
        )
        FormMultiline("Custom system prompt (optional — empty হলে default Priya prompt)",
            s.customSystemPrompt) { v -> app.settings.update { it.copy(customSystemPrompt = v) } }

        Divider()
        SectionTitle("Behaviour")
        ToggleRow(
            "Barge-in (TTS-চলাকালে কথা বললে থামাও)", s.bargeInEnabled
        ) { v -> app.settings.update { it.copy(bargeInEnabled = v) } }
        ToggleRow(
            "Floating overlay enabled", s.overlayEnabled
        ) { v -> app.settings.update { it.copy(overlayEnabled = v) } }
        ToggleRow(
            "Accessibility helpers enabled (screen analysis / app automation)", s.accessibilityHelpersEnabled
        ) { v -> app.settings.update { it.copy(accessibilityHelpersEnabled = v) } }
        ToggleRow(
            "Root helpers (rooted device only)", s.rootHelpersEnabled
        ) { v -> app.settings.update { it.copy(rootHelpersEnabled = v) } }

        Divider()
        SectionTitle("Permissions")
        OutlinedButton(
            onClick = {
                context.startActivity(
                    Intent(AndroidSettings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        Uri.parse("package:${context.packageName}"))
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            },
            modifier = Modifier.fillMaxWidth()
        ) { Text("Open app permissions") }

        OutlinedButton(
            onClick = {
                context.startActivity(
                    Intent(AndroidSettings.ACTION_ACCESSIBILITY_SETTINGS)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            },
            modifier = Modifier.fillMaxWidth()
        ) { Text("Open Accessibility settings — enable Priya") }

        RootEnableAccessibilityButton()

        OutlinedButton(
            onClick = {
                context.startActivity(
                    Intent(AndroidSettings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:${context.packageName}"))
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            },
            modifier = Modifier.fillMaxWidth()
        ) { Text("Allow overlay (display over other apps)") }
        Spacer(Modifier.height(40.dp))
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
}

@Composable
private fun TestConnectionButton() {
    val app = remember { PriyaApplication.get() }
    val scope = rememberCoroutineScope()
    var status by remember { mutableStateOf<TestStatus>(TestStatus.Idle) }
    Column {
        Spacer(Modifier.height(4.dp))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilledTonalButton(
                onClick = {
                    status = TestStatus.Running
                    scope.launch {
                        status = try {
                            val provider = app.providers.active()
                            val reply = provider.complete(
                                listOf(
                                    ChatMessage(Role.SYSTEM, "Reply with a single short word."),
                                    ChatMessage(Role.USER, "ping"),
                                )
                            ) {}
                            TestStatus.Ok(reply.take(80))
                        } catch (t: Throwable) {
                            TestStatus.Fail(t.message ?: t.javaClass.simpleName)
                        }
                    }
                },
                enabled = status !is TestStatus.Running,
            ) {
                Icon(Icons.Filled.Bolt, null)
                Spacer(Modifier.width(6.dp))
                Text(if (status is TestStatus.Running) "Testing…" else "Test connection")
            }
            when (val st = status) {
                is TestStatus.Idle -> Text("API key valid কিনা check করুন।", color = VibeMuted, style = MaterialTheme.typography.bodySmall)
                is TestStatus.Running -> CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = VibePink)
                is TestStatus.Ok -> Text("OK: ${st.reply}", color = Color(0xFF7CFFB2), style = MaterialTheme.typography.bodySmall)
                is TestStatus.Fail -> Text(st.message, color = Color(0xFFFF8A8A), style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

private sealed interface TestStatus {
    data object Idle : TestStatus
    data object Running : TestStatus
    data class Ok(val reply: String) : TestStatus
    data class Fail(val message: String) : TestStatus
}

@Composable
private fun RootEnableAccessibilityButton() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var status by remember { mutableStateOf<String?>(null) }
    var ok by remember { mutableStateOf(false) }
    Column {
        Button(
            onClick = {
                status = "চলছে…"
                scope.launch {
                    val outcome = withContext(kotlinx.coroutines.Dispatchers.IO) {
                        AccessibilityEnabler.enableViaRoot(context)
                    }
                    ok = outcome.success
                    status = outcome.message
                }
            },
            colors = ButtonDefaults.buttonColors(containerColor = VibePink, contentColor = Color.White),
            modifier = Modifier.fillMaxWidth()
        ) { Text("Enable accessibility automatically (root)") }
        if (status != null) {
            Spacer(Modifier.height(4.dp))
            Text(
                status!!,
                color = if (ok) Color(0xFF7CFFB2) else Color(0xFFFFB347),
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun ProviderPicker(selected: ProviderId, onSelected: (ProviderId) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        ProviderId.values().forEach { p ->
            FilterChip(
                selected = selected == p,
                onClick = { onSelected(p) },
                label = { Text(p.label()) },
                modifier = Modifier.weight(1f)
            )
        }
    }
}

private fun ProviderId.label(): String = when (this) {
    ProviderId.OPENAI -> "OpenAI-compat"
    ProviderId.GROQ -> "Groq"
    ProviderId.GEMINI -> "Gemini"
}

@Composable
private fun TtsPicker(selected: TtsBackend, onSelected: (TtsBackend) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        TtsBackend.values().forEach { b ->
            FilterChip(
                selected = selected == b,
                onClick = { onSelected(b) },
                label = { Text(b.label()) },
                modifier = Modifier.weight(1f)
            )
        }
    }
}

private fun TtsBackend.label(): String = when (this) {
    TtsBackend.ANDROID -> "Android"
    TtsBackend.ELEVENLABS -> "ElevenLabs"
    TtsBackend.OPENAI_COMPATIBLE -> "OpenAI-compat"
}

@Composable
private fun SttPicker(selected: SttBackend, onSelected: (SttBackend) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        SttBackend.values().forEach { b ->
            FilterChip(
                selected = selected == b,
                onClick = { onSelected(b) },
                label = { Text(b.label()) },
                modifier = Modifier.weight(1f)
            )
        }
    }
}

private fun SttBackend.label(): String = when (this) {
    SttBackend.ANDROID -> "Android STT"
    SttBackend.GROQ_WHISPER -> "Groq Whisper"
}

@Composable
private fun FormText(label: String, value: String, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
    )
}

@Composable
private fun FormMultiline(label: String, value: String, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        modifier = Modifier.fillMaxWidth().heightIn(min = 96.dp),
        minLines = 3,
    )
}

@Composable
private fun FormSecret(label: String, prefKey: String, keys: com.piash.priya.data.SecureKeyStore) {
    var value by remember(prefKey) { mutableStateOf(keys.get(prefKey)) }
    OutlinedTextField(
        value = value,
        onValueChange = {
            value = it
            keys.put(prefKey, it.trim())
        },
        label = { Text(label) },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        visualTransformation = PasswordVisualTransformation(),
    )
}

@Composable
private fun ToggleRow(label: String, value: Boolean, onToggle: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
    ) {
        Text(label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        Switch(checked = value, onCheckedChange = onToggle)
    }
}
