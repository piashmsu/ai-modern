package com.piash.priya.voice

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import com.piash.priya.util.DebugLog
import java.util.Locale
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * Wrapper around the platform [TextToSpeech] engine.
 *
 * `speak` suspends until the utterance fully finishes (or errors out / is
 * stopped) so the caller — typically [VoicePipeline] — can hold the
 * SPEAKING state for exactly as long as the audio plays. This is what lets
 * the pipeline reliably pause the SpeechRecognizer for the duration and
 * avoid the AI hearing its own output.
 */
class AndroidTts(context: Context) : TtsBackendImpl {

    private val tts: TextToSpeech
    @Volatile private var ready = false
    private val pending = ConcurrentHashMap<String, CancellableContinuation<Unit>>()

    init {
        tts = TextToSpeech(context.applicationContext) { status ->
            ready = status == TextToSpeech.SUCCESS
            DebugLog.i("AndroidTts", if (ready) "engine ready" else "init failed status=$status")
        }
        tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {
                DebugLog.d("AndroidTts", "onStart $utteranceId")
            }
            override fun onDone(utteranceId: String?) {
                DebugLog.d("AndroidTts", "onDone $utteranceId")
                resumeAndRemove(utteranceId)
            }

            @Deprecated("legacy", level = DeprecationLevel.WARNING)
            override fun onError(utteranceId: String?) {
                DebugLog.w("AndroidTts", "onError(legacy) $utteranceId")
                resumeAndRemove(utteranceId)
            }

            override fun onError(utteranceId: String?, errorCode: Int) {
                DebugLog.w("AndroidTts", "onError $utteranceId code=$errorCode")
                resumeAndRemove(utteranceId)
            }

            override fun onStop(utteranceId: String?, interrupted: Boolean) {
                DebugLog.d("AndroidTts", "onStop $utteranceId interrupted=$interrupted")
                resumeAndRemove(utteranceId)
            }
        })
    }

    override suspend fun speak(text: String, languageTag: String, voiceId: String?): Unit =
        suspendCancellableCoroutine { cont ->
            if (!waitForReady(2500)) {
                DebugLog.w("AndroidTts", "engine not ready — skipping speak")
                cont.resume(Unit)
                return@suspendCancellableCoroutine
            }
            try {
                val locale = Locale.forLanguageTag(languageTag)
                val supported = tts.isLanguageAvailable(locale)
                if (supported < TextToSpeech.LANG_AVAILABLE) {
                    DebugLog.w("AndroidTts", "lang $languageTag not installed (code=$supported); falling back to system default")
                } else {
                    tts.language = locale
                }
                if (!voiceId.isNullOrBlank()) {
                    tts.voices?.firstOrNull { it.name == voiceId }?.let { tts.voice = it }
                }
                val id = UUID.randomUUID().toString()
                pending[id] = cont
                cont.invokeOnCancellation {
                    try { tts.stop() } catch (_: Throwable) {}
                    pending.remove(id)
                }
                val rc = tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, id)
                if (rc != TextToSpeech.SUCCESS) {
                    DebugLog.w("AndroidTts", "speak() returned $rc")
                    pending.remove(id)
                    cont.resume(Unit)
                }
            } catch (t: Throwable) {
                DebugLog.e("AndroidTts", "speak failed", t)
                cont.resume(Unit)
            }
        }

    override fun stop() {
        try { tts.stop() } catch (_: Throwable) {}
        // Resume any pending coroutines so we don't deadlock the pipeline.
        val keys = pending.keys.toList()
        keys.forEach { resumeAndRemove(it) }
    }

    override fun shutdown() {
        try {
            tts.stop()
            tts.shutdown()
        } catch (_: Throwable) {}
        val keys = pending.keys.toList()
        keys.forEach { resumeAndRemove(it) }
    }

    private fun resumeAndRemove(utteranceId: String?) {
        val cont = pending.remove(utteranceId) ?: return
        if (!cont.isCompleted) cont.resume(Unit)
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
