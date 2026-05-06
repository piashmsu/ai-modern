package com.piash.priya.voice

import android.media.AudioAttributes
import android.media.MediaPlayer
import com.piash.priya.ai.Http
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.coroutines.resume
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * Cloud TTS backed by ElevenLabs or any OpenAI-compatible `/audio/speech`
 * endpoint. Strategy is selected by the [openAiCompatible] flag — when true
 * the request uses OpenAI's `{model, voice, input}` schema, otherwise it
 * uses ElevenLabs' `text-to-speech/<voice-id>` with `{text, model_id}`.
 */
class CloudTts(
    private val cacheDir: File,
    private val baseUrl: String,
    private val apiKey: String,
    private val voiceId: String,
    private val model: String,
    private val openAiCompatible: Boolean,
) : TtsBackendImpl {

    @Volatile private var current: MediaPlayer? = null

    override suspend fun speak(text: String, languageTag: String, voiceId: String?) {
        if (text.isBlank() || apiKey.isBlank()) return
        val effectiveVoice = voiceId?.takeIf { it.isNotBlank() } ?: this.voiceId
        val mp3 = withContext(Dispatchers.IO) { fetch(text, effectiveVoice) } ?: return
        play(mp3)
    }

    override fun stop() {
        try {
            current?.stop()
            current?.release()
        } catch (_: Throwable) {
        } finally {
            current = null
        }
    }

    override fun shutdown() = stop()

    private fun fetch(text: String, voice: String): File? {
        val (url, body, headers) = if (openAiCompatible) {
            val payload = buildJsonObject {
                put("model", model)
                put("voice", voice)
                put("input", text)
                put("format", "mp3")
            }
            Triple(
                baseUrl.trimEnd('/') + "/audio/speech",
                payload.toString().toRequestBody(JSON),
                mapOf("Authorization" to "Bearer $apiKey")
            )
        } else {
            val payload = buildJsonObject {
                put("text", text)
                put("model_id", model)
            }
            Triple(
                baseUrl.trimEnd('/') + "/text-to-speech/" + voice,
                payload.toString().toRequestBody(JSON),
                mapOf("xi-api-key" to apiKey, "Accept" to "audio/mpeg")
            )
        }

        val req = Request.Builder().url(url).post(body).apply {
            headers.forEach { (k, v) -> addHeader(k, v) }
        }.build()

        return try {
            Http.client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return null
                val out = File(cacheDir, "tts_${System.currentTimeMillis()}.mp3")
                out.outputStream().use { sink -> resp.body?.byteStream()?.copyTo(sink) }
                out
            }
        } catch (_: Throwable) {
            null
        }
    }

    private suspend fun play(file: File) = suspendCancellableCoroutine<Unit> { cont ->
        val mp = MediaPlayer().apply {
            setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANT)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
            setDataSource(file.absolutePath)
            prepare()
            setOnCompletionListener {
                file.delete()
                release()
                if (current === this) current = null
                if (!cont.isCompleted) cont.resume(Unit)
            }
            setOnErrorListener { _, _, _ ->
                file.delete()
                release()
                if (current === this) current = null
                if (!cont.isCompleted) cont.resume(Unit)
                true
            }
            start()
        }
        current = mp
        cont.invokeOnCancellation { stop() }
    }

    companion object {
        private val JSON = "application/json".toMediaType()
        @Suppress("unused")
        private val json = Json { ignoreUnknownKeys = true }
    }
}
