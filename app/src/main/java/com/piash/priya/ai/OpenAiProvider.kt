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
 * OpenAI-compatible provider. Works with OpenAI itself, OpenRouter,
 * LM Studio, Ollama (`/v1` mode), Together, Fireworks, DeepInfra,
 * Groq's `chat/completions` endpoint, and any other backend that
 * speaks the same `/chat/completions` SSE dialect.
 */
class OpenAiProvider(
    override val id: String,
    override val displayName: String,
    private val baseUrl: String,
    private val apiKey: String,
    private val model: String,
) : LlmProvider {

    override suspend fun complete(
        messages: List<ChatMessage>,
        onChunk: (String) -> Unit,
    ): String = withContext(Dispatchers.IO) {
        val payload = buildJsonObject {
            put("model", model)
            put("stream", true)
            put("temperature", 0.85)
            put("messages", buildJsonArray {
                messages.forEach { m ->
                    add(buildJsonObject {
                        put("role", when (m.role) {
                            Role.SYSTEM -> "system"
                            Role.USER -> "user"
                            Role.ASSISTANT -> "assistant"
                        })
                        put("content", m.content)
                    })
                }
            })
        }

        val url = baseUrl.trimEnd('/') + "/chat/completions"
        val req = Request.Builder()
            .url(url)
            .addHeader("Authorization", "Bearer $apiKey")
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
                if (data == "[DONE]") break
                val chunk = parseDelta(data) ?: continue
                if (chunk.isNotEmpty()) {
                    full.append(chunk)
                    onChunk(chunk)
                }
            }
        }
        full.toString()
    }

    private fun parseDelta(data: String): String? {
        return try {
            val obj = json.parseToJsonElement(data).jsonObject
            val choice = obj["choices"]?.jsonArray?.firstOrNull()?.jsonObject ?: return null
            // streaming format: {"delta":{"content":"..."}}
            val delta = choice["delta"]?.jsonObject
            delta?.get("content")?.jsonPrimitive?.contentOrNull
                // some backends only return final messages without delta wrapper
                ?: choice["message"]?.jsonObject?.get("content")?.jsonPrimitive?.contentOrNull
        } catch (_: Throwable) {
            null
        }
    }

    companion object {
        private val json = Json { ignoreUnknownKeys = true }
    }
}
