package com.piash.priya.ai

import com.piash.priya.data.ProviderId
import com.piash.priya.data.SecureKeyStore
import com.piash.priya.data.SettingsRepository

/**
 * Resolves the active [LlmProvider] from current settings + secret store.
 *
 * `LlmProvider` instances are cheap (just a config holder) so they're built
 * fresh per call rather than cached — this avoids stale models when the user
 * tweaks settings mid-session.
 */
class ProviderRegistry(
    private val secureKeys: SecureKeyStore,
    private val settings: SettingsRepository,
) {

    fun active(): LlmProvider {
        val s = settings.state.value
        return when (s.activeProvider) {
            ProviderId.OPENAI -> {
                val key = requireKey(SettingsRepository.SECRET_OPENAI, "OpenAI-compatible")
                OpenAiProvider(
                    id = "openai",
                    displayName = "OpenAI-compatible",
                    baseUrl = s.openAiBaseUrl,
                    apiKey = key,
                    model = s.openAiModel,
                )
            }
            ProviderId.GROQ -> {
                val key = requireKey(SettingsRepository.SECRET_GROQ, "Groq")
                OpenAiProvider(
                    id = "groq",
                    displayName = "Groq",
                    baseUrl = "https://api.groq.com/openai/v1",
                    apiKey = key,
                    model = s.groqModel,
                )
            }
            ProviderId.GEMINI -> {
                val key = requireKey(SettingsRepository.SECRET_GEMINI, "Gemini")
                GeminiProvider(apiKey = key, model = s.geminiModel)
            }
        }
    }

    fun isConfigured(): Boolean = runCatching { active() }.isSuccess

    fun missingKeyMessage(): String {
        val s = settings.state.value
        val name = when (s.activeProvider) {
            ProviderId.OPENAI -> "OpenAI-compatible"
            ProviderId.GROQ -> "Groq"
            ProviderId.GEMINI -> "Gemini"
        }
        return "$name API key সেট করা নেই। Settings → AI Providers থেকে সেট করুন।"
    }

    private fun requireKey(name: String, friendly: String): String {
        val v = secureKeys.get(name).trim()
        if (v.isBlank()) throw LlmException("$friendly API key missing")
        return v
    }
}
