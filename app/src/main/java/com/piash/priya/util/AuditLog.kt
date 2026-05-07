package com.piash.priya.util

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Persistent record of *actions* the assistant performed — distinct from
 * [DebugLog] which is for diagnostic events. Audit entries answer "what did
 * Priya do for me?" and are user-visible by design.
 *
 * Stored in SharedPreferences as a JSON list (capped to MAX_ENTRIES). Disk
 * sync is best-effort fire-and-forget; if it fails we still keep the
 * in-memory state.
 */
object AuditLog {
    enum class Kind { APP_LAUNCH, SMS_SENT, ROOT_CMD, NOTIF_READ, SCREEN_SNAPSHOT, CONFIRMATION }

    data class Entry(
        val tsMillis: Long,
        val kind: Kind,
        val summary: String,
        val detail: String,
    ) {
        fun toJson(): JSONObject = JSONObject().apply {
            put("ts", tsMillis)
            put("kind", kind.name)
            put("summary", summary)
            put("detail", detail)
        }

        companion object {
            fun fromJson(o: JSONObject): Entry = Entry(
                tsMillis = o.optLong("ts"),
                kind = runCatching { Kind.valueOf(o.optString("kind")) }.getOrDefault(Kind.CONFIRMATION),
                summary = o.optString("summary"),
                detail = o.optString("detail"),
            )
        }
    }

    private const val MAX_ENTRIES = 200
    private const val PREFS = "priya_audit"
    private const val KEY = "entries"
    private val TIME = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)

    private val _entries = MutableStateFlow<List<Entry>>(emptyList())
    val entries: StateFlow<List<Entry>> = _entries.asStateFlow()

    @Volatile private var appCtx: Context? = null

    fun init(context: Context) {
        if (appCtx != null) return
        appCtx = context.applicationContext
        load()
    }

    fun record(kind: Kind, summary: String, detail: String = "") {
        val entry = Entry(System.currentTimeMillis(), kind, summary, detail)
        val cur = _entries.value
        val next = if (cur.size >= MAX_ENTRIES) cur.takeLast(MAX_ENTRIES - 1) + entry else cur + entry
        _entries.value = next
        DebugLog.i("Audit", "${kind.name}: $summary")
        persist()
    }

    fun clear() {
        _entries.value = emptyList()
        persist()
    }

    fun dump(): String = _entries.value.joinToString("\n") { e ->
        "${TIME.format(Date(e.tsMillis))} ${e.kind} :: ${e.summary}${if (e.detail.isNotBlank()) " — ${e.detail}" else ""}"
    }

    private fun prefs() = appCtx?.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun load() {
        val raw = prefs()?.getString(KEY, null) ?: return
        try {
            val arr = JSONArray(raw)
            val list = (0 until arr.length()).map { Entry.fromJson(arr.getJSONObject(it)) }
            _entries.value = list
        } catch (_: Throwable) { /* fresh start */ }
    }

    private fun persist() {
        val p = prefs() ?: return
        try {
            val arr = JSONArray()
            _entries.value.forEach { arr.put(it.toJson()) }
            p.edit().putString(KEY, arr.toString()).apply()
        } catch (_: Throwable) { /* best effort */ }
    }
}
