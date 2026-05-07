package com.piash.priya.automation

import android.content.ComponentName
import android.content.Context
import android.provider.Settings
import com.piash.priya.services.PriyaAccessibilityService
import com.piash.priya.util.DebugLog

/**
 * One-shot helper that enables Priya's [PriyaAccessibilityService] on
 * rooted devices. We can't just write to `Settings.Secure` from a regular
 * app — that requires `WRITE_SECURE_SETTINGS` which is shell/system only.
 * On a rooted phone, we shell out via `su` and have ADB-level shell do it.
 *
 * On non-rooted devices the user has to walk through Settings →
 * Accessibility → Priya manually; we fall back to opening that screen.
 */
object AccessibilityEnabler {

    data class Outcome(val success: Boolean, val message: String)

    fun enableViaRoot(context: Context): Outcome {
        if (!RootShell.isAvailable()) {
            return Outcome(false, "Root (su) available না। Manual: Settings → Accessibility → Priya → enable।")
        }
        val service = ComponentName(context, PriyaAccessibilityService::class.java).flattenToString()
        // Read existing entry so we don't clobber other accessibility services
        // the user has enabled (TalkBack, etc.).
        val existing = readEnabled() ?: ""
        val parts = existing.split(":").filter { it.isNotBlank() }.toMutableSet()
        parts.add(service)
        val merged = parts.joinToString(":")

        DebugLog.i("AccessibilityEnabler", "current enabled='$existing' → '$merged'")

        val cmds = listOf(
            "settings put secure enabled_accessibility_services '$merged'",
            "settings put secure accessibility_enabled 1",
        )
        for (c in cmds) {
            val r = RootShell.exec(c)
            DebugLog.i("AccessibilityEnabler", "exec '$c' → exit=${r?.exitCode} err='${r?.stderr?.take(120)}'")
            if (r == null || !r.ok) {
                return Outcome(false, "Root command fail: $c (${r?.stderr ?: "no su output"})")
            }
        }
        return Outcome(true, "Enabled — service running।")
    }

    fun isPriyaServiceEnabled(context: Context): Boolean {
        val service = ComponentName(context, PriyaAccessibilityService::class.java).flattenToString()
        val enabled = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
        ) ?: return false
        return enabled.split(":").any { it.equals(service, ignoreCase = true) }
    }

    private fun readEnabled(): String? {
        val r = RootShell.exec("settings get secure enabled_accessibility_services") ?: return null
        return if (r.ok) r.stdout.trim().takeIf { it != "null" } else null
    }
}
