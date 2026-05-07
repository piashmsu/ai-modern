package com.piash.priya.ai

import com.piash.priya.data.SettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Turn-based conversation manager.
 *
 * Holds the in-memory transcript, prepends the personality system prompt,
 * trims old turns when context grows too large, and routes streaming
 * completions through the active [LlmProvider].
 */
class ChatEngine(
    private val providers: ProviderRegistry,
    private val settings: SettingsRepository,
) {
    private val _transcript = MutableStateFlow<List<ChatMessage>>(emptyList())
    val transcript: StateFlow<List<ChatMessage>> = _transcript.asStateFlow()

    private val _streaming = MutableStateFlow(false)
    val streaming: StateFlow<Boolean> = _streaming.asStateFlow()

    private val _lastError = MutableStateFlow<String?>(null)
    val lastError: StateFlow<String?> = _lastError.asStateFlow()

    private val turnLock = Mutex()

    fun reset() {
        _transcript.value = emptyList()
        _lastError.value = null
    }

    fun clearError() { _lastError.value = null }

    suspend fun send(
        userText: String,
        onChunk: (String) -> Unit = {},
    ): String = turnLock.withLock {
        _lastError.value = null
        val provider = try {
            providers.active()
        } catch (t: Throwable) {
            _lastError.value = friendly(t)
            throw t
        }
        val current = _transcript.value + ChatMessage(Role.USER, userText)
        _transcript.value = current

        val systemPrompt = Personality.systemPrompt(settings.state.value)
        val window = trim(current, maxTurns = MAX_HISTORY_TURNS)
        val payload = listOf(ChatMessage(Role.SYSTEM, systemPrompt)) + window

        _streaming.value = true
        val reply = try {
            provider.complete(payload, onChunk)
        } catch (t: Throwable) {
            _lastError.value = friendly(t)
            throw t
        } finally {
            _streaming.value = false
        }
        _transcript.value = current + ChatMessage(Role.ASSISTANT, reply)
        reply
    }

    private fun friendly(t: Throwable): String {
        val raw = t.message ?: t.javaClass.simpleName
        return when {
            raw.contains("API key", ignoreCase = true) -> raw
            raw.contains("401", ignoreCase = true) -> "Provider বলছে: API key invalid (401)। Settings → AI Provider check করুন।"
            raw.contains("403", ignoreCase = true) -> "Provider বলছে: forbidden (403)। Key valid কিন্তু এই model এ access নেই।"
            raw.contains("404", ignoreCase = true) -> "Model পাওয়া যায়নি (404)। Settings এ model name ঠিক করুন।"
            raw.contains("429", ignoreCase = true) -> "Rate limit (429)। কিছুক্ষণ পরে আবার চেষ্টা করুন।"
            raw.contains("Unable to resolve host", ignoreCase = true) ||
            raw.contains("UnknownHostException", ignoreCase = true) -> "Internet নেই বা DNS fail করছে।"
            raw.contains("timeout", ignoreCase = true) -> "Network timeout — slow connection?"
            else -> raw
        }
    }

    private fun trim(messages: List<ChatMessage>, maxTurns: Int): List<ChatMessage> {
        if (messages.size <= maxTurns) return messages
        return messages.takeLast(maxTurns)
    }

    companion object {
        private const val MAX_HISTORY_TURNS = 20
    }
}
