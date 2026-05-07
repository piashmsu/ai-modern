package com.piash.priya.voice

import android.content.Context
import com.piash.priya.ai.ChatEngine
import com.piash.priya.data.SecureKeyStore
import com.piash.priya.data.SettingsRepository
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

/**
 * End-to-end voice loop:
 *
 *   STT partial → if TTS speaking → cancel TTS, drop in-flight LLM.
 *   STT final   → ChatEngine.send → stream tokens → TTS as soon as we have
 *                a complete sentence → repeat.
 *
 * The pipeline pauses the SpeechRecognizer for the entire duration of any
 * TTS utterance so the AI doesn't hear its own voice through the speaker
 * and end up arguing with itself. Listening resumes once the utterance is
 * fully finished or the user barges in.
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

    /** True while the AI's voice is actively coming out of the speaker. */
    @Volatile private var ttsActive = false

    fun start() {
        if (running) return
        running = true
        DebugLog.i("VoicePipeline", "start()")
        ensureTts()
        pipelineJob = scope.launch {
            stt.events.collectLatest { ev ->
                // Suppress every STT event while the AI is speaking — this is
                // what stops the assistant from listening to its own audio.
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
        ttsActive = false
        _state.value = State.IDLE
    }

    fun clearError() { _lastError.value = null }

    /** External one-shot — used by chat UI text input. Speaks reply via TTS too. */
    fun submit(text: String) {
        DebugLog.i("VoicePipeline", "submit(\"${text.take(120)}\")")
        inflightReplyJob?.cancel()
        if (tts == null) ensureTts()
        inflightReplyJob = scope.launch { runTurn(text) }
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
        // Pause the recognizer up-front — we'll be speaking soon and we
        // don't want to capture a syllable of TTS audio.
        stt.stop()
        ttsActive = true

        // Sentences are pushed to this channel as they're flushed from the
        // streaming reply, then a single speaker coroutine awaits each one
        // sequentially. This is what guarantees TTS plays a complete reply
        // instead of being cut off when the next sentence arrives.
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
            // Grace period so the speaker stops echoing before mic re-arms.
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
        val tag = chooseLanguageTag(settings.state.value.languageTag)
        DebugLog.d("VoicePipeline", "beginListening lang=$tag")
        stt.start(tag)
    }

    /**
     * Many devices don't ship the bn-BD recogniser. Walk a fallback chain so
     * the user gets *some* recognition rather than silent failure. The
     * SpeechRecognizer doesn't expose installed locales reliably, so we
     * just hand the chosen tag through and let it fall back implicitly via
     * the EXTRA_LANGUAGE_PREFERENCE preference list.
     */
    private fun chooseLanguageTag(requested: String): String {
        return requested
    }

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
