package com.piash.priya.automation

import com.piash.priya.util.AuditLog
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter

/**
 * Tiny `su` shell helper for rooted devices. Each [exec] starts a fresh
 * `su` process so a hung command can never block subsequent ones.
 *
 * Returns null when su isn't available or the command fails. Every command
 * is logged to [AuditLog] so the user can review what Priya ran with root.
 */
object RootShell {

    data class Result(val exitCode: Int, val stdout: String, val stderr: String) {
        val ok: Boolean get() = exitCode == 0
    }

    fun isAvailable(): Boolean = exec("id", audit = false).let {
        it != null && it.ok && it.stdout.contains("uid=0")
    }

    fun exec(cmd: String, timeoutMs: Long = 8000, audit: Boolean = true): Result? = runCatching {
        val proc = ProcessBuilder("su").redirectErrorStream(false).start()
        OutputStreamWriter(proc.outputStream).use { w ->
            w.write("$cmd\n")
            w.write("exit\n")
            w.flush()
        }
        val finished = proc.waitFor(timeoutMs, java.util.concurrent.TimeUnit.MILLISECONDS)
        if (!finished) {
            proc.destroyForcibly()
            if (audit) AuditLog.record(AuditLog.Kind.ROOT_CMD, "TIMEOUT", detail = cmd.take(160))
            return@runCatching null
        }
        val stdout = BufferedReader(InputStreamReader(proc.inputStream)).readText()
        val stderr = BufferedReader(InputStreamReader(proc.errorStream)).readText()
        val r = Result(proc.exitValue(), stdout.trim(), stderr.trim())
        if (audit) {
            AuditLog.record(
                AuditLog.Kind.ROOT_CMD,
                if (r.ok) "Root cmd OK" else "Root cmd exit=${r.exitCode}",
                detail = cmd.take(160),
            )
        }
        r
    }.getOrNull()
}
