package com.piash.priya.ai

import com.piash.priya.data.ChatHistoryStore
import com.piash.priya.data.SettingsRepository
import com.piash.priya.util.DebugLog
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
 * completions through the active [LlmProvider]. Persists user/assistant
 * turns to disk so chat history survives process death.
 */
class ChatEngine(
    private val providers: ProviderRegistry,
    private val settings: SettingsRepository,
    private val history: ChatHistoryStore,
) {
    private val _transcript = MutableStateFlow(loadInitial())
    val transcript: StateFlow<List<ChatMessage>> = _transcript.asStateFlow()

    private val _streaming = MutableStateFlow(false)
    val streaming: StateFlow<Boolean> = _streaming.asStateFlow()

    private val _lastError = MutableStateFlow<String?>(null)
    val lastError: StateFlow<String?> = _lastError.asStateFlow()

    private val turnLock = Mutex()

    private fun loadInitial(): List<ChatMessage> {
        if (!settings.state.value.persistConversation) return emptyList()
        val loaded = history.load()
        if (loaded.isNotEmpty()) DebugLog.i("ChatEngine", "loaded ${loaded.size} persisted messages")
        return loaded
    }

    fun reset() {
        _transcript.value = emptyList()
        _lastError.value = null
        history.clear()
    }

    fun clearError() { _lastError.value = null }

    /**
     * Inject a deterministic exchange into the transcript without hitting
     * the LLM. Used by [com.piash.priya.voice.IntentRouter] so users see
     * "open whatsapp" → "WhatsApp খুললাম" right in the chat history.
     */
    fun injectExchange(userText: String, assistantReply: String) {
        val next = _transcript.value +
            ChatMessage(Role.USER, userText) +
            ChatMessage(Role.ASSISTANT, assistantReply)
        _transcript.value = next
        persistIfEnabled(next)
    }

    suspend fun send(
        userText: String,
        onChunk: (String) -> Unit = {},
    ): String = turnLock.withLock {
        _lastError.value = null
        DebugLog.i("ChatEngine", "send: \"${userText.take(120)}\"")
        val provider = try {
            providers.active()
        } catch (t: Throwable) {
            DebugLog.e("ChatEngine", "provider missing", t)
            _lastError.value = friendly(t)
            throw t
        }
        DebugLog.d("ChatEngine", "active provider=${provider.id}")
        val current = _transcript.value + ChatMessage(Role.USER, userText)
        _transcript.value = current

        val systemPrompt = Personality.systemPrompt(settings.state.value)
        val window = trim(current, maxTurns = MAX_HISTORY_TURNS)
        val payload = listOf(ChatMessage(Role.SYSTEM, systemPrompt)) + window

        _streaming.value = true
        val started = System.currentTimeMillis()
        val reply = try {
            provider.complete(payload, onChunk)
        } catch (t: Throwable) {
            DebugLog.e("ChatEngine", "provider.complete failed", t)
            _lastError.value = friendly(t)
            throw t
        } finally {
            _streaming.value = false
        }
        DebugLog.i("ChatEngine", "reply received in ${System.currentTimeMillis() - started}ms (${reply.length} chars)")
        val updated = current + ChatMessage(Role.ASSISTANT, reply)
        _transcript.value = updated
        persistIfEnabled(updated)
        reply
    }

    private fun persistIfEnabled(messages: List<ChatMessage>) {
        if (!settings.state.value.persistConversation) return
        history.save(ChatHistoryStore.cap(messages))
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
