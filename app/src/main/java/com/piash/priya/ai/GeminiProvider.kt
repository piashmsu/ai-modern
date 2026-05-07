package com.piash.priya.ai

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import com.piash.priya.util.DebugLog
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * Google Gemini (Generative Language API) provider.
 *
 * Uses the streaming `streamGenerateContent` endpoint with `alt=sse`. All
 * `system`-role messages from the abstract chat layer are merged into a
 * `systemInstruction` block per Gemini's preferred shape.
 */
class GeminiProvider(
    private val apiKey: String,
    private val model: String,
) : LlmProvider {

    override val id: String = "gemini"
    override val displayName: String = "Gemini"

    override suspend fun complete(
        messages: List<ChatMessage>,
        onChunk: (String) -> Unit,
    ): String = withContext(Dispatchers.IO) {
        val systemText = messages.filter { it.role == Role.SYSTEM }
            .joinToString("\n\n") { it.content }
        val convo = messages.filter { it.role != Role.SYSTEM }

        val payload = buildJsonObject {
            put("contents", buildJsonArray {
                convo.forEach { m ->
                    add(buildJsonObject {
                        put("role", if (m.role == Role.ASSISTANT) "model" else "user")
                        put("parts", buildJsonArray {
                            add(buildJsonObject { put("text", m.content) })
                        })
                    })
                }
            })
            if (systemText.isNotBlank()) {
                // Gemini's systemInstruction must NOT carry a role field —
                // adding "role": "system" causes HTTP 400 because the only
                // valid roles for content blocks are "user" and "model".
                put("systemInstruction", buildJsonObject {
                    put("parts", buildJsonArray {
                        add(buildJsonObject { put("text", systemText) })
                    })
                })
            }
            put("generationConfig", buildJsonObject {
                put("temperature", 0.85)
                put("topP", 0.95)
                put("maxOutputTokens", 1024)
            })
        }

        // Google deprecated `-latest` aliases. Auto-rewrite the most common
        // dead names so users with old defaults don't get a silent 404.
        val effectiveModel = normalizeModelName(model)
        if (effectiveModel != model) {
            DebugLog.w("GeminiProvider", "rewriting deprecated model '$model' → '$effectiveModel'")
        }
        val url = "https://generativelanguage.googleapis.com/v1beta/models/$effectiveModel:streamGenerateContent?alt=sse&key=$apiKey"
        val safeUrl = url.replace(apiKey, "***")
        DebugLog.d("GeminiProvider", "POST $safeUrl")
        val req = Request.Builder()
            .url(url)
            .addHeader("Accept", "text/event-stream")
            .post(payload.toString().toRequestBody("application/json".toMediaType()))
            .build()

        val full = StringBuilder()
        Http.client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) {
                val body = resp.body?.string()?.take(500).orEmpty()
                val hint = if (resp.code == 404)
                    " — model '$effectiveModel' not found। Try gemini-2.0-flash, gemini-2.5-flash, gemini-1.5-flash"
                else ""
                throw LlmException("$displayName HTTP ${resp.code}: ${body.ifBlank { "(empty body)" }}$hint")
            }
            val source = resp.body?.source() ?: throw LlmException("Empty body")
            while (!source.exhausted()) {
                val line = source.readUtf8Line() ?: break
                if (line.isBlank() || !line.startsWith("data:")) continue
                val data = line.removePrefix("data:").trim()
                val text = parseChunk(data) ?: continue
                if (text.isNotEmpty()) {
                    full.append(text)
                    onChunk(text)
                }
            }
        }
        full.toString()
    }

    private fun parseChunk(data: String): String? {
        return try {
            val obj = json.parseToJsonElement(data).jsonObject
            val candidate = obj["candidates"]?.jsonArray?.firstOrNull()?.jsonObject ?: return null
            val parts = candidate["content"]?.jsonObject?.get("parts")?.jsonArray ?: return null
            parts.joinToString(separator = "") {
                it.jsonObject["text"]?.jsonPrimitive?.contentOrNull.orEmpty()
            }.takeIf { it.isNotEmpty() }
        } catch (_: Throwable) {
            null
        }
    }

    /**
     * Map deprecated `-latest` aliases to currently-served versions so a
     * stale default doesn't return 404. Anything not on the rewrite list is
     * passed through unchanged so users can still hand-pick previews.
     */
    private fun normalizeModelName(name: String): String = when (name.trim()) {
        "gemini-1.5-flash-latest" -> "gemini-1.5-flash"
        "gemini-1.5-pro-latest" -> "gemini-1.5-pro"
        "gemini-pro" -> "gemini-1.5-flash"
        "gemini-pro-latest" -> "gemini-1.5-pro"
        else -> name.trim()
    }

    companion object {
        private val json = Json { ignoreUnknownKeys = true }
    }
}
