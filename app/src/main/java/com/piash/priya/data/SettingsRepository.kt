package com.piash.priya.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Plain-text user-preference repository.
 *
 * Holds non-secret data: which provider is active, model name, voice id,
 * personality strength, automation toggles, etc. Secrets live in
 * [SecureKeyStore]; never store API keys here.
 */
class SettingsRepository(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("priya_settings", Context.MODE_PRIVATE)

    private val _state = MutableStateFlow(load())
    val state: StateFlow<Settings> = _state.asStateFlow()

    fun update(transform: (Settings) -> Settings) {
        val next = transform(_state.value)
        save(next)
        _state.value = next
    }

    private fun load(): Settings = Settings(
        activeProvider = ProviderId.valueOf(
            prefs.getString(K_ACTIVE_PROVIDER, ProviderId.GEMINI.name) ?: ProviderId.GEMINI.name
        ),
        openAiBaseUrl = prefs.getString(K_OPENAI_BASE, "https://api.openai.com/v1") ?: "https://api.openai.com/v1",
        openAiModel = prefs.getString(K_OPENAI_MODEL, "gpt-4o-mini") ?: "gpt-4o-mini",
        groqModel = prefs.getString(K_GROQ_MODEL, "llama-3.3-70b-versatile") ?: "llama-3.3-70b-versatile",
        groqWhisperModel = prefs.getString(K_GROQ_WHISPER, "whisper-large-v3") ?: "whisper-large-v3",
        geminiModel = prefs.getString(K_GEMINI_MODEL, "gemini-2.0-flash") ?: "gemini-2.0-flash",
        ttsBackend = TtsBackend.valueOf(
            prefs.getString(K_TTS_BACKEND, TtsBackend.ANDROID.name) ?: TtsBackend.ANDROID.name
        ),
        ttsBaseUrl = prefs.getString(K_TTS_BASE, "https://api.elevenlabs.io/v1") ?: "https://api.elevenlabs.io/v1",
        ttsVoiceId = prefs.getString(K_TTS_VOICE, "Rachel") ?: "Rachel",
        ttsModel = prefs.getString(K_TTS_MODEL, "eleven_multilingual_v2") ?: "eleven_multilingual_v2",
        sttBackend = SttBackend.valueOf(
            prefs.getString(K_STT_BACKEND, SttBackend.ANDROID.name) ?: SttBackend.ANDROID.name
        ),
        languageTag = prefs.getString(K_LANG_TAG, "bn-BD") ?: "bn-BD",
        priyaLiveMode = prefs.getBoolean(K_LIVE_MODE, false),
        overlayEnabled = prefs.getBoolean(K_OVERLAY, false),
        accessibilityHelpersEnabled = prefs.getBoolean(K_AX_HELPERS, false),
        rootHelpersEnabled = prefs.getBoolean(K_ROOT, false),
        bargeInEnabled = prefs.getBoolean(K_BARGE_IN, true),
        personalityIntensity = prefs.getInt(K_PERSONALITY, 70),
        userName = prefs.getString(K_USER_NAME, "") ?: "",
        customSystemPrompt = prefs.getString(K_CUSTOM_PROMPT, "") ?: "",
    )

    private fun save(s: Settings) {
        prefs.edit().apply {
            putString(K_ACTIVE_PROVIDER, s.activeProvider.name)
            putString(K_OPENAI_BASE, s.openAiBaseUrl)
            putString(K_OPENAI_MODEL, s.openAiModel)
            putString(K_GROQ_MODEL, s.groqModel)
            putString(K_GROQ_WHISPER, s.groqWhisperModel)
            putString(K_GEMINI_MODEL, s.geminiModel)
            putString(K_TTS_BACKEND, s.ttsBackend.name)
            putString(K_TTS_BASE, s.ttsBaseUrl)
            putString(K_TTS_VOICE, s.ttsVoiceId)
            putString(K_TTS_MODEL, s.ttsModel)
            putString(K_STT_BACKEND, s.sttBackend.name)
            putString(K_LANG_TAG, s.languageTag)
            putBoolean(K_LIVE_MODE, s.priyaLiveMode)
            putBoolean(K_OVERLAY, s.overlayEnabled)
            putBoolean(K_AX_HELPERS, s.accessibilityHelpersEnabled)
            putBoolean(K_ROOT, s.rootHelpersEnabled)
            putBoolean(K_BARGE_IN, s.bargeInEnabled)
            putInt(K_PERSONALITY, s.personalityIntensity)
            putString(K_USER_NAME, s.userName)
            putString(K_CUSTOM_PROMPT, s.customSystemPrompt)
        }.apply()
    }

    companion object {
        private const val K_ACTIVE_PROVIDER = "active_provider"
        private const val K_OPENAI_BASE = "openai_base"
        private const val K_OPENAI_MODEL = "openai_model"
        private const val K_GROQ_MODEL = "groq_model"
        private const val K_GROQ_WHISPER = "groq_whisper"
        private const val K_GEMINI_MODEL = "gemini_model"
        private const val K_TTS_BACKEND = "tts_backend"
        private const val K_TTS_BASE = "tts_base"
        private const val K_TTS_VOICE = "tts_voice"
        private const val K_TTS_MODEL = "tts_model"
        private const val K_STT_BACKEND = "stt_backend"
        private const val K_LANG_TAG = "lang_tag"
        private const val K_LIVE_MODE = "live_mode"
        private const val K_OVERLAY = "overlay"
        private const val K_AX_HELPERS = "ax_helpers"
        private const val K_ROOT = "root"
        private const val K_BARGE_IN = "barge_in"
        private const val K_PERSONALITY = "personality"
        private const val K_USER_NAME = "user_name"
        private const val K_CUSTOM_PROMPT = "custom_prompt"

        const val SECRET_OPENAI = "openai_api_key"
        const val SECRET_GROQ = "groq_api_key"
        const val SECRET_GEMINI = "gemini_api_key"
        const val SECRET_TTS = "tts_api_key"
    }
}

enum class ProviderId { OPENAI, GROQ, GEMINI }
enum class TtsBackend { ANDROID, ELEVENLABS, OPENAI_COMPATIBLE }
enum class SttBackend { ANDROID, GROQ_WHISPER }

data class Settings(
    val activeProvider: ProviderId,
    val openAiBaseUrl: String,
    val openAiModel: String,
    val groqModel: String,
    val groqWhisperModel: String,
    val geminiModel: String,
    val ttsBackend: TtsBackend,
    val ttsBaseUrl: String,
    val ttsVoiceId: String,
    val ttsModel: String,
    val sttBackend: SttBackend,
    val languageTag: String,
    val priyaLiveMode: Boolean,
    val overlayEnabled: Boolean,
    val accessibilityHelpersEnabled: Boolean,
    val rootHelpersEnabled: Boolean,
    val bargeInEnabled: Boolean,
    val personalityIntensity: Int,
    val userName: String,
    val customSystemPrompt: String,
)
