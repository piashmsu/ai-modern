package com.piash.priya.voice

import android.content.Context
import com.piash.priya.ai.ChatEngine
import com.piash.priya.data.SecureKeyStore
import com.piash.priya.data.SettingsRepository
import com.piash.priya.data.TtsBackend
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * End-to-end voice loop:
 *
 *   STT partial → if TTS speaking → cancel TTS, drop in-flight LLM.
 *   STT final   → ChatEngine.send → stream tokens → TTS as soon as we have
 *                a complete sentence → repeat.
 *
 * Lifecycle is owned by the caller (foreground service). Call [start] to
 * begin a continuous loop and [stop] to shut everything down.
 */
class VoicePipeline(
    private val context: Context,
    private val settings: SettingsRepository,
    private val chatEngine: ChatEngine,
) {

    enum class State { IDLE, LISTENING, THINKING, SPEAKING, ERROR }

    private val _state = MutableStateFlow(State.IDLE)
    val state: StateFlow<State> = _state.asStateFlow()

    private val _lastError = MutableStateFlow<String?>(null)
    val lastError: StateFlow<String?> = _lastError.asStateFlow()

    private val _liveTranscript = MutableStateFlow("")
    val liveTranscript: StateFlow<String> = _liveTranscript.asStateFlow()

    private val _liveReply = MutableStateFlow("")
    val liveReply: StateFlow<String> = _liveReply.asStateFlow()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val stt = SpeechRecognizerWrapper(context)
    @Volatile private var tts: TtsBackendImpl? = null
    @Volatile private var pipelineJob: Job? = null
    @Volatile private var inflightReplyJob: Job? = null
    @Volatile private var running = false

    fun start() {
        if (running) return
        running = true
        ensureTts()
        pipelineJob = scope.launch {
            stt.events.collectLatest { ev ->
                when (ev) {
                    is SpeechRecognizerWrapper.Event.Ready -> _state.value = State.LISTENING
                    is SpeechRecognizerWrapper.Event.SpeechStart,
                    is SpeechRecognizerWrapper.Event.PartialResult -> handleBargeIn(ev)
                    is SpeechRecognizerWrapper.Event.FinalResult -> {
                        _liveTranscript.value = ev.text
                        if (ev.text.isNotBlank()) submit(ev.text)
                        if (running) restartListening()
                    }
                    is SpeechRecognizerWrapper.Event.Error -> {
                        _lastError.value = ev.message
                        if (running) restartListening()
                    }
                    is SpeechRecognizerWrapper.Event.EndOfSpeech -> {
                        if (_state.value == State.LISTENING) _state.value = State.THINKING
                    }
                }
            }
        }
        beginListening()
    }

    suspend fun stop() {
        running = false
        try {
            inflightReplyJob?.cancelAndJoin()
            pipelineJob?.cancelAndJoin()
        } catch (_: Throwable) {}
        stt.stop()
        tts?.stop()
        _state.value = State.IDLE
    }

    fun clearError() { _lastError.value = null }

    /** External one-shot — used by chat UI text input. Speaks reply via TTS too. */
    fun submit(text: String) {
        inflightReplyJob?.cancel()
        if (tts == null) ensureTts()
        inflightReplyJob = scope.launch { runTurn(text) }
    }

    private fun handleBargeIn(ev: SpeechRecognizerWrapper.Event) {
        if (!settings.state.value.bargeInEnabled) return
        if (_state.value == State.SPEAKING || _state.value == State.THINKING) {
            tts?.stop()
            inflightReplyJob?.cancel()
            _state.value = State.LISTENING
        }
        if (ev is SpeechRecognizerWrapper.Event.PartialResult) {
            _liveTranscript.value = ev.text
        }
    }

    private suspend fun runTurn(userText: String) {
        _state.value = State.THINKING
        _liveReply.value = ""
        try {
            val sentenceBuf = StringBuilder()
            val full = chatEngine.send(userText) { chunk ->
                _liveReply.value = _liveReply.value + chunk
                sentenceBuf.append(chunk)
                // Stream sentence-by-sentence to TTS for snappier playback.
                val flushable = takeFlushableSentence(sentenceBuf)
                if (flushable.isNotBlank()) {
                    scope.launch { speakSegment(flushable) }
                }
            }
            // Speak any final residual that didn't end with punctuation.
            if (sentenceBuf.isNotBlank()) speakSegment(sentenceBuf.toString())
            _liveReply.value = full
        } catch (t: Throwable) {
            _lastError.value = t.message ?: "error"
            _state.value = State.ERROR
        } finally {
            if (_state.value != State.ERROR) _state.value = if (running) State.LISTENING else State.IDLE
        }
    }

    private suspend fun speakSegment(text: String) {
        val s = settings.state.value
        _state.value = State.SPEAKING
        tts?.speak(text.trim(), s.languageTag, s.ttsVoiceId.takeIf { it.isNotBlank() })
    }

    private fun takeFlushableSentence(buf: StringBuilder): String {
        val s = buf.toString()
        // Find last sentence terminator (Bangla ।, English . ! ?, newline).
        val idx = maxOf(
            s.lastIndexOf('।'),
            s.lastIndexOf('.'),
            s.lastIndexOf('!'),
            s.lastIndexOf('?'),
            s.lastIndexOf('\n'),
        )
        if (idx < 0) return ""
        val toFlush = s.substring(0, idx + 1)
        buf.delete(0, idx + 1)
        return toFlush.trim()
    }

    private fun beginListening() {
        stt.start(settings.state.value.languageTag)
    }

    private fun restartListening() {
        scope.launch {
            try { kotlinx.coroutines.delay(150) } catch (_: Throwable) {}
            if (running) beginListening()
        }
    }

    private fun ensureTts() {
        tts?.shutdown()
        val s = settings.state.value
        tts = when (s.ttsBackend) {
            TtsBackend.ANDROID -> AndroidTts(context)
            TtsBackend.ELEVENLABS -> CloudTts(
                cacheDir = context.cacheDir,
                baseUrl = s.ttsBaseUrl,
                apiKey = SecureKeyStore(context).get(SettingsRepository.SECRET_TTS),
                voiceId = s.ttsVoiceId,
                model = s.ttsModel,
                openAiCompatible = false,
            )
            TtsBackend.OPENAI_COMPATIBLE -> CloudTts(
                cacheDir = context.cacheDir,
                baseUrl = s.ttsBaseUrl,
                apiKey = SecureKeyStore(context).get(SettingsRepository.SECRET_TTS),
                voiceId = s.ttsVoiceId,
                model = s.ttsModel,
                openAiCompatible = true,
            )
        }
    }
}
