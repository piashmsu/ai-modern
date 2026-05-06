package com.piash.priya.voice

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import java.util.Locale
import java.util.UUID
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * Wrapper around the platform [TextToSpeech] engine.
 *
 * Public methods are suspend so callers can `await` the spoken phrase, but
 * [stop] is fully synchronous and can be invoked from a barge-in handler.
 */
class AndroidTts(context: Context) : TtsBackendImpl {

    private val tts: TextToSpeech
    @Volatile private var ready = false
    @Volatile private var currentUtterance: String? = null

    init {
        tts = TextToSpeech(context.applicationContext) { status ->
            ready = status == TextToSpeech.SUCCESS
        }
        tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {}
            override fun onDone(utteranceId: String?) {
                if (utteranceId == currentUtterance) currentUtterance = null
            }

            @Deprecated("legacy", level = DeprecationLevel.WARNING)
            override fun onError(utteranceId: String?) {
                if (utteranceId == currentUtterance) currentUtterance = null
            }

            override fun onError(utteranceId: String?, errorCode: Int) {
                if (utteranceId == currentUtterance) currentUtterance = null
            }
        })
    }

    override suspend fun speak(text: String, languageTag: String, voiceId: String?): Unit =
        suspendCancellableCoroutine { cont ->
            if (!waitForReady(2000)) {
                cont.resume(Unit)
                return@suspendCancellableCoroutine
            }
            try {
                val locale = Locale.forLanguageTag(languageTag)
                tts.language = locale
                if (!voiceId.isNullOrBlank()) {
                    tts.voices?.firstOrNull { it.name == voiceId }?.let { tts.voice = it }
                }
                val id = UUID.randomUUID().toString()
                currentUtterance = id
                tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, id)
            } catch (_: Throwable) {
                // ignore — best-effort speak
            }
            cont.resume(Unit)
        }

    override fun stop() {
        try { tts.stop() } catch (_: Throwable) {}
        currentUtterance = null
    }

    override fun shutdown() {
        try {
            tts.stop()
            tts.shutdown()
        } catch (_: Throwable) {
        }
    }

    private fun waitForReady(timeoutMs: Long): Boolean {
        val end = System.currentTimeMillis() + timeoutMs
        while (!ready && System.currentTimeMillis() < end) {
            try { Thread.sleep(50) } catch (_: InterruptedException) { return ready }
        }
        return ready
    }
}

interface TtsBackendImpl {
    suspend fun speak(text: String, languageTag: String, voiceId: String?)
    fun stop()
    fun shutdown()
}
