package com.piash.priya.data

import android.content.Context
import com.piash.priya.ai.ChatMessage
import com.piash.priya.ai.Role
import org.json.JSONArray
import org.json.JSONObject

/**
 * Persists the chat transcript to disk so the user doesn't lose history
 * when the app process is killed. Stored as a JSON list in
 * SharedPreferences — we only persist user/assistant turns, never the
 * volatile system prompt.
 */
class ChatHistoryStore(context: Context) {

    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun load(): List<ChatMessage> {
        val raw = prefs.getString(KEY, null) ?: return emptyList()
        return try {
            val arr = JSONArray(raw)
            (0 until arr.length()).mapNotNull { idx ->
                val obj = arr.getJSONObject(idx)
                val role = runCatching { Role.valueOf(obj.optString("role")) }.getOrNull() ?: return@mapNotNull null
                if (role == Role.SYSTEM) return@mapNotNull null
                val content = obj.optString("content")
                if (content.isBlank()) null else ChatMessage(role, content)
            }
        } catch (_: Throwable) { emptyList() }
    }

    fun save(messages: List<ChatMessage>) {
        try {
            val arr = JSONArray()
            messages.forEach { m ->
                if (m.role == Role.SYSTEM) return@forEach
                arr.put(JSONObject().apply {
                    put("role", m.role.name)
                    put("content", m.content)
                })
            }
            prefs.edit().putString(KEY, arr.toString()).apply()
        } catch (_: Throwable) { /* best effort */ }
    }

    fun clear() {
        prefs.edit().remove(KEY).apply()
    }

    companion object {
        private const val PREFS = "priya_chat_history"
        private const val KEY = "messages"
        private const val MAX_PERSISTED = 200

        fun cap(messages: List<ChatMessage>): List<ChatMessage> =
            if (messages.size <= MAX_PERSISTED) messages else messages.takeLast(MAX_PERSISTED)
    }
}
