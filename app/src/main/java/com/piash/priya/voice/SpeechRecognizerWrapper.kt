package com.piash.priya.voice

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import androidx.core.content.ContextCompat
import com.piash.priya.util.DebugLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Thin wrapper around Android's [SpeechRecognizer] that emits hot events as
 * they happen so the rest of the pipeline (e.g. barge-in TTS cancellation)
 * can react before the user finishes speaking.
 *
 * Events:
 *  - [Event.PartialResult] — fired ~200-400ms after speech onset; triggers TTS stop.
 *  - [Event.FinalResult]   — fired when SpeechRecognizer detects end of speech.
 *  - [Event.Error]         — recoverable; caller decides whether to restart.
 *  - [Event.SpeechStart]   — RMS rose above silence threshold (best-effort).
 */
class SpeechRecognizerWrapper(private val context: Context) {

    sealed interface Event {
        data object SpeechStart : Event
        data class PartialResult(val text: String) : Event
        data class FinalResult(val text: String) : Event
        data class Error(val code: Int, val message: String) : Event
        data object EndOfSpeech : Event
        data object Ready : Event
    }

    private val _events = MutableSharedFlow<Event>(
        replay = 0,
        extraBufferCapacity = 16
    )
    val events: SharedFlow<Event> = _events.asSharedFlow()

    private val scope = CoroutineScope(Dispatchers.Main)
    private val active = AtomicBoolean(false)
    private var recognizer: SpeechRecognizer? = null

    fun hasPermission(): Boolean = ContextCompat.checkSelfPermission(
        context, Manifest.permission.RECORD_AUDIO
    ) == PackageManager.PERMISSION_GRANTED

    fun isAvailable(): Boolean = SpeechRecognizer.isRecognitionAvailable(context)

    fun start(languageTag: String) {
        if (!hasPermission() || !isAvailable()) {
            scope.launch { _events.emit(Event.Error(-1, "RECORD_AUDIO not granted or recognizer unavailable")) }
            return
        }
        if (active.getAndSet(true)) return

        val listener = object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                scope.launch { _events.emit(Event.Ready) }
            }

            override fun onBeginningOfSpeech() {
                scope.launch { _events.emit(Event.SpeechStart) }
            }

            override fun onRmsChanged(rmsdB: Float) = Unit
            override fun onBufferReceived(buffer: ByteArray?) = Unit

            override fun onEndOfSpeech() {
                scope.launch { _events.emit(Event.EndOfSpeech) }
            }

            override fun onError(error: Int) {
                active.set(false)
                scope.launch { _events.emit(Event.Error(error, errorMessage(error))) }
            }

            override fun onResults(results: Bundle?) {
                val text = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    ?.firstOrNull().orEmpty()
                active.set(false)
                scope.launch { _events.emit(Event.FinalResult(text)) }
            }

            override fun onPartialResults(partialResults: Bundle?) {
                val text = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    ?.firstOrNull().orEmpty()
                if (text.isNotBlank()) {
                    scope.launch { _events.emit(Event.PartialResult(text)) }
                }
            }

            override fun onEvent(eventType: Int, params: Bundle?) = Unit
        }

        try {
            recognizer?.destroy()
            recognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
                setRecognitionListener(listener)
            }
            // The user's preferred tag is the first hint, but we also pass a
            // fallback chain via EXTRA_LANGUAGE_PREFERENCE so devices without
            // bn-BD installed fall back to bn-IN, then en-IN, then en-US
            // instead of failing silently.
            val fallbacks = arrayListOf(languageTag).apply {
                addAll(listOf("bn-BD", "bn-IN", "en-IN", "en-US"))
            }.distinct().toTypedArray()
            DebugLog.i(TAG, "STT start lang=$languageTag fallbacks=${fallbacks.joinToString()}")
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, languageTag)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, languageTag)
                putExtra("android.speech.extra.EXTRA_LANGUAGE_PREFERENCE", fallbacks)
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
                putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, false)
                putExtra("android.speech.extra.DICTATION_MODE", true)
            }
            recognizer?.startListening(intent)
        } catch (t: Throwable) {
            Log.e(TAG, "start failed", t)
            DebugLog.e(TAG, "STT start failed", t)
            active.set(false)
            scope.launch { _events.emit(Event.Error(-2, t.message ?: "start failed")) }
        }
    }

    fun stop() {
        try {
            recognizer?.stopListening()
            recognizer?.cancel()
            recognizer?.destroy()
        } catch (_: Throwable) {
        } finally {
            recognizer = null
            active.set(false)
        }
    }

    private fun errorMessage(code: Int): String = when (code) {
        SpeechRecognizer.ERROR_AUDIO -> "audio error"
        SpeechRecognizer.ERROR_CLIENT -> "client error"
        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "no mic permission"
        SpeechRecognizer.ERROR_NETWORK -> "network error"
        SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "network timeout"
        SpeechRecognizer.ERROR_NO_MATCH -> "no match"
        SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "recognizer busy"
        SpeechRecognizer.ERROR_SERVER -> "server error"
        SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "speech timeout"
        else -> "unknown ($code)"
    }

    companion object {
        private const val TAG = "PriyaSTT"
    }
}
