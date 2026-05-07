package com.piash.priya.voice

import com.piash.priya.automation.AppLauncher
import com.piash.priya.util.AuditLog
import com.piash.priya.util.DebugLog

/**
 * Lightweight rule-based intent matcher that runs *before* the LLM so the
 * common voice commands ("WhatsApp খোলো", "open Facebook") have zero-latency
 * deterministic handling and don't burn an LLM call.
 *
 * Returns a [Result] when an intent matches; otherwise returns null and the
 * caller routes the original text to the LLM.
 */
class IntentRouter(private val launcher: AppLauncher) {

    sealed interface Result {
        data class AppLaunched(val label: String) : Result
        data class AppNotFound(val query: String) : Result
    }

    fun route(text: String): Result? {
        val cleaned = text.trim()
        if (cleaned.isBlank()) return null
        val normalized = cleaned.lowercase()
            // common Bangla normalisations
            .replace("খোলো", " khol ")
            .replace("খুলো", " khol ")
            .replace("খোল", " khol ")
            .replace("চালু কর", " khol ")
            .replace("ওপেন", " open ")
            .replace("লঞ্চ", " launch ")
            .replace("চালাও", " open ")
            .replace("দেখাও", " open ")
            .replace(Regex("\\s+"), " ")
            .trim()

        val target = extractAppQuery(normalized) ?: return null
        DebugLog.i("IntentRouter", "matched app intent: \"$target\"")
        return tryLaunch(target)
    }

    /**
     * Pulls the app-name fragment out of phrases like:
     *   "open whatsapp"            -> "whatsapp"
     *   "whatsapp khol"            -> "whatsapp"
     *   "open the canva app"       -> "canva"
     *   "khol facebook"            -> "facebook"
     */
    private fun extractAppQuery(s: String): String? {
        val tokens = s.split(' ').filter { it.isNotBlank() }
        if (tokens.isEmpty()) return null

        val verbs = setOf("open", "launch", "khol", "khulo", "khole", "start", "run")
        val verbIdx = tokens.indexOfFirst { it in verbs }
        if (verbIdx < 0) return null

        val before = tokens.subList(0, verbIdx)
        val after = tokens.subList(verbIdx + 1, tokens.size)

        // pattern: "<app> khol" -> take everything before
        if (verbIdx > 0 && (tokens[verbIdx] == "khol" || tokens[verbIdx] == "khulo" || tokens[verbIdx] == "khole")) {
            return before.joinToString(" ").stripFiller()
        }
        // pattern: "open <app>" -> take everything after
        if (after.isNotEmpty()) {
            return after.joinToString(" ").stripFiller()
        }
        return null
    }

    private fun String.stripFiller(): String? {
        val cleaned = this
            .replace(Regex("\\b(the|app|application|please)\\b"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
        return cleaned.takeIf { it.isNotBlank() }
    }

    private fun tryLaunch(query: String): Result {
        val ok = launcher.launch(query)
        return if (ok) {
            AuditLog.record(AuditLog.Kind.APP_LAUNCH, "Opened $query")
            Result.AppLaunched(query)
        } else {
            DebugLog.w("IntentRouter", "no app matched query \"$query\"")
            Result.AppNotFound(query)
        }
    }
}
