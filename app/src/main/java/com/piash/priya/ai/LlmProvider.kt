package com.piash.priya.ai

/**
 * Single chat message in a conversation.
 *
 * Roles align with OpenAI's chat-completions vocabulary so providers that
 * speak that dialect can pass messages through unchanged. Gemini-shaped
 * providers map [Role.USER] to "user", [Role.ASSISTANT] to "model" and
 * collapse [Role.SYSTEM] into the leading user turn.
 */
data class ChatMessage(val role: Role, val content: String)

enum class Role { SYSTEM, USER, ASSISTANT }

/**
 * Abstraction over a remote LLM. Providers must:
 *  - be cancellable (caller may abandon a stream when the user starts speaking),
 *  - emit incremental text chunks via [onChunk] for streaming UIs,
 *  - return the full final answer.
 */
interface LlmProvider {
    val id: String
    val displayName: String

    suspend fun complete(
        messages: List<ChatMessage>,
        onChunk: (String) -> Unit = {},
    ): String
}

class LlmException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)
