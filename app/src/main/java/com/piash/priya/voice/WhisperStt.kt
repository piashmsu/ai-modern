package com.piash.priya.voice

import com.piash.priya.ai.Http
import com.piash.priya.util.DebugLog
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

/**
 * Cloud Whisper transcription via the OpenAI-compatible /audio/transcriptions
 * endpoint. Compatible with both Groq and OpenAI itself; only the base URL
 * differs.
 *
 * Uploads a single WAV blob and returns the recognised text. Used by the
 * push-to-talk flow when [com.piash.priya.data.SttBackend.GROQ_WHISPER] is
 * the active backend.
 */
class WhisperStt(
    private val baseUrl: String,
    private val apiKey: String,
    private val model: String,
) {

    suspend fun transcribe(wavBytes: ByteArray, languageTag: String): String {
        if (apiKey.isBlank()) error("Whisper API key missing")
        if (wavBytes.isEmpty()) return ""

        val multipart = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("model", model)
            .addFormDataPart("response_format", "json")
            .addFormDataPart(
                "language",
                languageTag.substringBefore('-').lowercase().ifBlank { "bn" }
            )
            .addFormDataPart(
                "file", "speech.wav",
                wavBytes.toRequestBody("audio/wav".toMediaType())
            )
            .build()

        val url = baseUrl.trimEnd('/') + "/audio/transcriptions"
        DebugLog.d("WhisperStt", "POST $url model=$model bytes=${wavBytes.size}")
        val req = Request.Builder()
            .url(url)
            .addHeader("Authorization", "Bearer $apiKey")
            .post(multipart)
            .build()

        Http.client.newCall(req).execute().use { resp ->
            val body = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) {
                DebugLog.e("WhisperStt", "HTTP ${resp.code}: ${body.take(400)}")
                error("Whisper HTTP ${resp.code}: ${body.take(200)}")
            }
            val text = try {
                JSONObject(body).optString("text", "")
            } catch (_: Throwable) { body }
            DebugLog.i("WhisperStt", "transcript: \"${text.take(120)}\" (${text.length} chars)")
            return text.trim()
        }
    }
}
