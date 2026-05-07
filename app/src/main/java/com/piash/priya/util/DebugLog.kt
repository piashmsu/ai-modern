package com.piash.priya.util

import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * In-app ring-buffer log so the user can copy/paste exactly what went wrong
 * without needing `adb logcat`. Mirrors all entries to the standard Android
 * logger as well, so logcat still works for development.
 */
object DebugLog {
    enum class Level(val tag: String) { DEBUG("D"), INFO("I"), WARN("W"), ERROR("E") }

    data class Entry(
        val tsMillis: Long,
        val level: Level,
        val tag: String,
        val message: String,
    ) {
        fun format(): String =
            "${TIME.format(Date(tsMillis))} ${level.tag}/$tag: $message"
    }

    private const val MAX_ENTRIES = 500
    private val TIME = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)

    private val _entries = MutableStateFlow<List<Entry>>(emptyList())
    val entries: StateFlow<List<Entry>> = _entries.asStateFlow()

    fun d(tag: String, msg: String) = log(Level.DEBUG, tag, msg).also { Log.d(tag, msg) }
    fun i(tag: String, msg: String) = log(Level.INFO, tag, msg).also { Log.i(tag, msg) }
    fun w(tag: String, msg: String, t: Throwable? = null) {
        log(Level.WARN, tag, if (t != null) "$msg :: ${t.message}" else msg)
        if (t != null) Log.w(tag, msg, t) else Log.w(tag, msg)
    }
    fun e(tag: String, msg: String, t: Throwable? = null) {
        log(Level.ERROR, tag, if (t != null) "$msg :: ${t.javaClass.simpleName}: ${t.message}" else msg)
        if (t != null) Log.e(tag, msg, t) else Log.e(tag, msg)
    }

    fun clear() { _entries.value = emptyList() }

    fun dump(): String = _entries.value.joinToString("\n") { it.format() }

    private fun log(level: Level, tag: String, msg: String) {
        val entry = Entry(System.currentTimeMillis(), level, tag, msg)
        val cur = _entries.value
        val next = if (cur.size >= MAX_ENTRIES) cur.takeLast(MAX_ENTRIES - 1) + entry else cur + entry
        _entries.value = next
    }
}
