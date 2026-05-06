package com.piash.priya.automation

import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter

/**
 * Tiny `su` shell helper for rooted devices. Each [exec] starts a fresh
 * `su` process so a hung command can never block subsequent ones.
 *
 * Returns null when su isn't available or the command fails.
 */
object RootShell {

    data class Result(val exitCode: Int, val stdout: String, val stderr: String) {
        val ok: Boolean get() = exitCode == 0
    }

    fun isAvailable(): Boolean = exec("id").let { it != null && it.ok && it.stdout.contains("uid=0") }

    fun exec(cmd: String, timeoutMs: Long = 8000): Result? = runCatching {
        val proc = ProcessBuilder("su").redirectErrorStream(false).start()
        OutputStreamWriter(proc.outputStream).use { w ->
            w.write("$cmd\n")
            w.write("exit\n")
            w.flush()
        }
        val finished = proc.waitFor(timeoutMs, java.util.concurrent.TimeUnit.MILLISECONDS)
        if (!finished) {
            proc.destroyForcibly()
            return@runCatching null
        }
        val stdout = BufferedReader(InputStreamReader(proc.inputStream)).readText()
        val stderr = BufferedReader(InputStreamReader(proc.errorStream)).readText()
        Result(proc.exitValue(), stdout.trim(), stderr.trim())
    }.getOrNull()
}
