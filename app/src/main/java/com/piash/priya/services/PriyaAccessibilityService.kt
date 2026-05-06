package com.piash.priya.services

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.os.Bundle
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

/**
 * Foundation for screen-aware automation: exposes the current foreground
 * window's content tree and provides primitive actions (tap/swipe/type).
 *
 * Higher-level skills (Canva edit, CapCut export, etc.) compose these
 * primitives — those flows live in the `automation` package.
 */
class PriyaAccessibilityService : AccessibilityService() {

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Caller pulls fresh data via [snapshotForeground]; events themselves
        // are not buffered to keep memory usage bounded.
    }

    override fun onInterrupt() = Unit

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
    }

    override fun onUnbind(intent: android.content.Intent?): Boolean {
        instance = null
        return super.onUnbind(intent)
    }

    /** Returns a flattened text/desc summary of the current foreground window. */
    fun snapshotForeground(): String = buildString {
        val root = rootInActiveWindow ?: return@buildString
        flatten(root, this)
    }

    private fun flatten(node: AccessibilityNodeInfo?, sb: StringBuilder, depth: Int = 0) {
        if (node == null) return
        val text = node.text?.toString().orEmpty()
        val desc = node.contentDescription?.toString().orEmpty()
        if (text.isNotBlank() || desc.isNotBlank()) {
            repeat(depth) { sb.append(' ') }
            sb.append("- ")
            if (text.isNotBlank()) sb.append(text)
            if (desc.isNotBlank()) sb.append(" [").append(desc).append(']')
            sb.append('\n')
        }
        for (i in 0 until node.childCount) flatten(node.getChild(i), sb, depth + 1)
    }

    fun tapAt(x: Float, y: Float, durationMs: Long = 60L): Boolean {
        val path = Path().apply { moveTo(x, y) }
        val stroke = GestureDescription.StrokeDescription(path, 0L, durationMs)
        val gesture = GestureDescription.Builder().addStroke(stroke).build()
        return dispatchGesture(gesture, null, null)
    }

    fun swipe(fromX: Float, fromY: Float, toX: Float, toY: Float, durationMs: Long = 280L): Boolean {
        val path = Path().apply {
            moveTo(fromX, fromY)
            lineTo(toX, toY)
        }
        val stroke = GestureDescription.StrokeDescription(path, 0L, durationMs)
        return dispatchGesture(GestureDescription.Builder().addStroke(stroke).build(), null, null)
    }

    fun setText(node: AccessibilityNodeInfo, text: String): Boolean {
        val args = Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
        }
        return node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
    }

    companion object {
        @Volatile
        private var instance: PriyaAccessibilityService? = null

        fun get(): PriyaAccessibilityService? = instance
        fun isConnected(): Boolean = instance != null
    }
}
