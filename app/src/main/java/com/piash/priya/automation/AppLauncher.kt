package com.piash.priya.automation

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo

/**
 * Find and launch installed apps by display name (fuzzy) or package id.
 *
 * Used by the assistant for prompts like "Canva খোলো" / "open CapCut".
 */
class AppLauncher(private val context: Context) {

    data class Installed(val packageName: String, val label: String)

    fun installed(): List<Installed> {
        val pm = context.packageManager
        val intent = Intent(Intent.ACTION_MAIN, null).addCategory(Intent.CATEGORY_LAUNCHER)
        val flag = if (android.os.Build.VERSION.SDK_INT >= 33)
            PackageManager.ResolveInfoFlags.of(0L)
        else null
        val resolved: List<ResolveInfo> = if (flag != null) {
            pm.queryIntentActivities(intent, flag)
        } else {
            @Suppress("DEPRECATION") pm.queryIntentActivities(intent, 0)
        }
        return resolved.mapNotNull { info ->
            val pkg = info.activityInfo?.packageName ?: return@mapNotNull null
            val label = info.loadLabel(pm)?.toString() ?: pkg
            Installed(pkg, label)
        }.distinctBy { it.packageName }
    }

    fun launch(query: String): Boolean {
        val needle = query.lowercase().trim()
        if (needle.isBlank()) return false
        val match = installed().firstOrNull {
            it.label.lowercase() == needle || it.packageName.lowercase() == needle
        } ?: installed().firstOrNull {
            it.label.lowercase().contains(needle) || it.packageName.lowercase().contains(needle)
        } ?: return false
        return launchPackage(match.packageName)
    }

    fun launchPackage(packageName: String): Boolean {
        val pm = context.packageManager
        val intent = pm.getLaunchIntentForPackage(packageName) ?: return false
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return runCatching { context.startActivity(intent); true }.getOrDefault(false)
    }
}
