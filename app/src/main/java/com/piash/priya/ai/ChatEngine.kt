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

    private val turnLock = Mutex()

    fun reset() {
        _transcript.value = emptyList()
    }

    suspend fun send(
        userText: String,
        onChunk: (String) -> Unit = {},
    ): String = turnLock.withLock {
        val provider = providers.active()
        val current = _transcript.value + ChatMessage(Role.USER, userText)
        _transcript.value = current

        val systemPrompt = Personality.systemPrompt(settings.state.value)
        val window = trim(current, maxTurns = MAX_HISTORY_TURNS)
        val payload = listOf(ChatMessage(Role.SYSTEM, systemPrompt)) + window

        _streaming.value = true
        val reply = try {
            provider.complete(payload, onChunk)
        } finally {
            _streaming.value = false
        }
        _transcript.value = current + ChatMessage(Role.ASSISTANT, reply)
        reply
    }

    private fun trim(messages: List<ChatMessage>, maxTurns: Int): List<ChatMessage> {
        if (messages.size <= maxTurns) return messages
        return messages.takeLast(maxTurns)
    }

    companion object {
        private const val MAX_HISTORY_TURNS = 20
    }
}
