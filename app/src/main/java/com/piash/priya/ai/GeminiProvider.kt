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
                put("systemInstruction", buildJsonObject {
                    put("role", "system")
                    put("parts", buildJsonArray {
                        add(buildJsonObject { put("text", systemText) })
                    })
                })
            }
            put("generationConfig", buildJsonObject {
                put("temperature", 0.85)
                put("topP", 0.95)
            })
        }

        val url = "https://generativelanguage.googleapis.com/v1beta/models/$model:streamGenerateContent?alt=sse&key=$apiKey"
        val req = Request.Builder()
            .url(url)
            .addHeader("Accept", "text/event-stream")
            .post(payload.toString().toRequestBody("application/json".toMediaType()))
            .build()

        val full = StringBuilder()
        Http.client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) {
                throw LlmException("$displayName HTTP ${resp.code}: ${resp.body?.string()?.take(400)}")
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

    companion object {
        private val json = Json { ignoreUnknownKeys = true }
    }
}
