package com.piash.priya.voice

import android.content.Context
import com.piash.priya.ai.ChatEngine
import com.piash.priya.data.SecureKeyStore
import com.piash.priya.data.SettingsRepository
import com.piash.priya.data.SttBackend
import com.piash.priya.data.TtsBackend
import com.piash.priya.util.DebugLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * End-to-end voice loop:
 *
 *   STT partial → if TTS speaking → cancel TTS, drop in-flight LLM.
 *   STT final   → IntentRouter (deterministic commands) → fallback ChatEngine.send →
 *                stream tokens → TTS as soon as we have a complete sentence → repeat.
 *
 * Pauses the SpeechRecognizer for the entire duration of any TTS utterance
 * so the AI doesn't hear its own voice through the speaker. Listening
 * resumes once the utterance is fully finished or the user barges in.
 *
 * Also exposes [pttStart]/[pttStop] for push-to-talk, which records audio
 * locally and (when configured) routes it through cloud Whisper instead of
 * the platform recognizer.
 */
class VoicePipeline(
    private val context: Context,
    private val settings: SettingsRepository,
    private val secureKeys: SecureKeyStore,
    private val chatEngine: ChatEngine,
    private val intentRouter: IntentRouter,
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
    private val pcmRecorder = AudioRecorderPcm(context)
    @Volatile private var tts: TtsBackendImpl? = null
    @Volatile private var pipelineJob: Job? = null
    @Volatile private var inflightReplyJob: Job? = null
    @Volatile private var running = false
    @Volatile private var pttActive = false

    /** True while the AI's voice is actively coming out of the speaker. */
    @Volatile private var ttsActive = false

    fun start() {
        if (running) return
        running = true
        DebugLog.i("VoicePipeline", "start()")
        ensureTts()
        pipelineJob = scope.launch {
            stt.events.collectLatest { ev ->
                if (ttsActive) {
                    DebugLog.d("VoicePipeline", "drop STT event during TTS: ${ev::class.simpleName}")
                    return@collectLatest
                }
                when (ev) {
                    is SpeechRecognizerWrapper.Event.Ready -> _state.value = State.LISTENING
                    is SpeechRecognizerWrapper.Event.SpeechStart,
                    is SpeechRecognizerWrapper.Event.PartialResult -> handleBargeIn(ev)
                    is SpeechRecognizerWrapper.Event.FinalResult -> {
                        DebugLog.i("VoicePipeline", "STT final: \"${ev.text.take(120)}\"")
                        _liveTranscript.value = ev.text
                        if (ev.text.isNotBlank()) submit(ev.text)
                        if (running && !ttsActive) restartListening()
                    }
                    is SpeechRecognizerWrapper.Event.Error -> {
                        DebugLog.w("VoicePipeline", "STT error: ${ev.message}")
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
        DebugLog.i("VoicePipeline", "stop()")
        try {
            inflightReplyJob?.cancelAndJoin()
            pipelineJob?.cancelAndJoin()
        } catch (_: Throwable) {}
        stt.stop()
        tts?.stop()
        try { pcmRecorder.cancel() } catch (_: Throwable) {}
        ttsActive = false
        pttActive = false
        _state.value = State.IDLE
    }

    /**
     * Hard kill — used by the "Emergency stop" UI button. Cancels every
     * in-flight job and forces the state machine back to IDLE.
     */
    fun emergencyStop() {
        DebugLog.w("VoicePipeline", "EMERGENCY STOP")
        running = false
        try { inflightReplyJob?.cancel() } catch (_: Throwable) {}
        try { pipelineJob?.cancel() } catch (_: Throwable) {}
        try { stt.stop() } catch (_: Throwable) {}
        try { tts?.stop() } catch (_: Throwable) {}
        try { pcmRecorder.cancel() } catch (_: Throwable) {}
        ttsActive = false
        pttActive = false
        _state.value = State.IDLE
        _liveTranscript.value = ""
        _liveReply.value = ""
    }

    fun clearError() { _lastError.value = null }

    /** External one-shot — used by chat UI text input. Speaks reply via TTS too. */
    fun submit(text: String) {
        DebugLog.i("VoicePipeline", "submit(\"${text.take(120)}\")")
        // Try deterministic intent first to avoid LLM round-trip + cost.
        if (settings.state.value.voiceCommandsEnabled) {
            val outcome = intentRouter.route(text)
            if (outcome != null) {
                handleIntent(text, outcome)
                return
            }
        }
        inflightReplyJob?.cancel()
        if (tts == null) ensureTts()
        inflightReplyJob = scope.launch { runTurn(text) }
    }

    /** Push-to-talk: start capturing PCM audio. Caller must pair with [pttStop]. */
    fun pttStart(): Boolean {
        if (pttActive) return true
        DebugLog.i("VoicePipeline", "pttStart()")
        // Pause continuous listener so we don't double-record.
        try { stt.stop() } catch (_: Throwable) {}
        val ok = pcmRecorder.start()
        if (ok) {
            pttActive = true
            _state.value = State.LISTENING
            _liveTranscript.value = ""
        } else {
            _lastError.value = "Mic permission missing or unavailable"
        }
        return ok
    }

    /** Stops PTT capture, transcribes via Whisper or platform STT, then submits. */
    fun pttStop() {
        if (!pttActive) return
        pttActive = false
        DebugLog.i("VoicePipeline", "pttStop()")
        val wav = pcmRecorder.stopAndWav()
        if (wav.isEmpty()) {
            _state.value = if (running) State.LISTENING else State.IDLE
            return
        }
        _state.value = State.THINKING
        scope.launch {
            val s = settings.state.value
            val text = try {
                when (s.sttBackend) {
                    SttBackend.GROQ_WHISPER -> {
                        val key = secureKeys.get(SettingsRepository.SECRET_GROQ).trim()
                        if (key.isBlank()) {
                            _lastError.value = "Groq API key missing — Whisper STT requires Groq key"
                            ""
                        } else {
                            withContext(Dispatchers.IO) {
                                WhisperStt(
                                    baseUrl = "https://api.groq.com/openai/v1",
                                    apiKey = key,
                                    model = s.groqWhisperModel,
                                ).transcribe(wav, s.languageTag)
                            }
                        }
                    }
                    SttBackend.ANDROID -> {
                        DebugLog.w("VoicePipeline", "PTT used with Android STT — falling back to platform; transcription will be empty")
                        ""
                    }
                }
            } catch (t: Throwable) {
                DebugLog.e("VoicePipeline", "ptt transcribe failed", t)
                _lastError.value = t.message ?: "transcription failed"
                ""
            }
            if (text.isNotBlank()) {
                _liveTranscript.value = text
                submit(text)
            } else {
                _state.value = if (running) State.LISTENING else State.IDLE
            }
            if (running && !ttsActive) restartListening()
        }
    }

    private fun handleIntent(originalText: String, outcome: IntentRouter.Result) {
        val reply = when (outcome) {
            is IntentRouter.Result.AppLaunched -> "${outcome.label} খুললাম।"
            is IntentRouter.Result.AppNotFound -> "${outcome.query} খুঁজে পেলাম না।"
        }
        chatEngine.injectExchange(originalText, reply)
        _liveTranscript.value = originalText
        _liveReply.value = reply
        // Speak the confirmation so user gets audible feedback.
        if (tts == null) ensureTts()
        scope.launch {
            ttsActive = true
            try { speakSegment(reply) } finally {
                ttsActive = false
                if (running) {
                    delay(250)
                    if (running && !ttsActive) restartListening()
                }
            }
        }
    }

    private fun handleBargeIn(ev: SpeechRecognizerWrapper.Event) {
        if (!settings.state.value.bargeInEnabled) return
        if (_state.value == State.SPEAKING || _state.value == State.THINKING) {
            DebugLog.i("VoicePipeline", "barge-in: cancelling TTS + in-flight reply")
            tts?.stop()
            ttsActive = false
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
        stt.stop()
        ttsActive = true

        val sentenceQueue = Channel<String>(Channel.UNLIMITED)
        val speaker = scope.launch {
            for (segment in sentenceQueue) {
                speakSegment(segment)
            }
        }

        try {
            val sentenceBuf = StringBuilder()
            val full = chatEngine.send(userText) { chunk ->
                _liveReply.value = _liveReply.value + chunk
                sentenceBuf.append(chunk)
                val flushable = takeFlushableSentence(sentenceBuf)
                if (flushable.isNotBlank()) sentenceQueue.trySend(flushable)
            }
            if (sentenceBuf.isNotBlank()) sentenceQueue.trySend(sentenceBuf.toString())
            sentenceQueue.close()
            speaker.join()
            _liveReply.value = full
        } catch (t: Throwable) {
            DebugLog.e("VoicePipeline", "runTurn failed", t)
            _lastError.value = t.message ?: "error"
            _state.value = State.ERROR
            sentenceQueue.close()
            try { speaker.cancelAndJoin() } catch (_: Throwable) {}
        } finally {
            ttsActive = false
            if (_state.value != State.ERROR) _state.value = if (running) State.LISTENING else State.IDLE
            if (running) {
                delay(250)
                if (running && !ttsActive) restartListening()
            }
        }
    }

    private suspend fun speakSegment(text: String) {
        val s = settings.state.value
        _state.value = State.SPEAKING
        DebugLog.i("VoicePipeline", "TTS speak: \"${text.take(80)}\"")
        try {
            tts?.speak(text.trim(), s.languageTag, s.ttsVoiceId.takeIf { it.isNotBlank() })
        } catch (t: Throwable) {
            DebugLog.e("VoicePipeline", "speak failed", t)
        }
    }

    private fun takeFlushableSentence(buf: StringBuilder): String {
        val s = buf.toString()
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
        if (ttsActive) {
            DebugLog.d("VoicePipeline", "skip beginListening (TTS active)")
            return
        }
        // PTT mode + cloud Whisper: don't run platform STT in parallel.
        if (settings.state.value.sttBackend == SttBackend.GROQ_WHISPER) {
            DebugLog.d("VoicePipeline", "skip continuous STT (Whisper backend = push-to-talk only)")
            _state.value = if (running) State.IDLE else State.IDLE
            return
        }
        val tag = chooseLanguageTag(settings.state.value.languageTag)
        DebugLog.d("VoicePipeline", "beginListening lang=$tag")
        stt.start(tag)
    }

    private fun chooseLanguageTag(requested: String): String = requested

    private fun restartListening() {
        scope.launch {
            try { delay(150) } catch (_: Throwable) {}
            if (running && !ttsActive) beginListening()
        }
    }

    private fun ensureTts() {
        tts?.shutdown()
        val s = settings.state.value
        DebugLog.i("VoicePipeline", "ensureTts backend=${s.ttsBackend}")
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
