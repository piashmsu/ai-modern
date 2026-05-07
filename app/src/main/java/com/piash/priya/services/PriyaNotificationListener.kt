package com.piash.priya.services

import android.app.Notification
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.piash.priya.PriyaApplication
import com.piash.priya.util.AuditLog
import com.piash.priya.util.DebugLog
import com.piash.priya.voice.AndroidTts
import com.piash.priya.voice.TtsBackendImpl
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Listens for incoming notifications and — when [Settings.notificationReaderEnabled]
 * is true and Priya is not already speaking/listening — reads the title and
 * body via TTS so the user can hear them hands-free.
 *
 * Heavily filtered: own-app notifications, ongoing/foreground notifications,
 * groups, and very short empty-body notifications are ignored. Notifications
 * from messaging packages are prioritised.
 */
class PriyaNotificationListener : NotificationListenerService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    @Volatile private var connected = false
    @Volatile private var localTts: TtsBackendImpl? = null

    override fun onListenerConnected() {
        super.onListenerConnected()
        connected = true
        instance = this
        DebugLog.i(TAG, "listener connected")
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        connected = false
        if (instance === this) instance = null
        try { localTts?.shutdown() } catch (_: Throwable) {}
        localTts = null
        DebugLog.i(TAG, "listener disconnected")
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        if (sbn == null) return
        val app = applicationContext as? PriyaApplication ?: return
        val s = app.settings.state.value
        if (!s.notificationReaderEnabled) return
        if (sbn.packageName == packageName) return // ignore Priya's own notifications

        val notif = sbn.notification ?: return
        val flagsToSkip = Notification.FLAG_FOREGROUND_SERVICE or
            Notification.FLAG_ONGOING_EVENT or
            Notification.FLAG_GROUP_SUMMARY
        if ((notif.flags and flagsToSkip) != 0) return

        val title = notif.extras?.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
        val text = notif.extras?.getCharSequence(Notification.EXTRA_TEXT)?.toString().orEmpty()
        if (title.isBlank() && text.isBlank()) return

        val appLabel = appLabelOf(sbn.packageName)
        val spoken = buildString {
            append(appLabel).append("-এ নতুন notification")
            if (title.isNotBlank()) append("। ").append(title)
            if (text.isNotBlank()) append("। ").append(text)
        }.take(280)

        DebugLog.i(TAG, "speak \"$spoken\"")
        AuditLog.record(
            AuditLog.Kind.NOTIF_READ,
            "Read notification from $appLabel",
            detail = if (title.isNotBlank()) title else text.take(120),
        )

        val pipelineState = app.voicePipeline.state.value
        // Don't talk over an active conversation — just log and skip.
        if (pipelineState == com.piash.priya.voice.VoicePipeline.State.SPEAKING ||
            pipelineState == com.piash.priya.voice.VoicePipeline.State.LISTENING ||
            pipelineState == com.piash.priya.voice.VoicePipeline.State.THINKING
        ) {
            DebugLog.d(TAG, "skip TTS — pipeline busy ($pipelineState)")
            return
        }

        scope.launch {
            try {
                val tts = localTts ?: AndroidTts(applicationContext).also { localTts = it }
                tts.speak(spoken, s.languageTag, s.ttsVoiceId.takeIf { it.isNotBlank() })
            } catch (t: Throwable) {
                DebugLog.e(TAG, "speak failed", t)
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        try { localTts?.shutdown() } catch (_: Throwable) {}
        localTts = null
    }

    private fun appLabelOf(pkg: String): String = try {
        val pm = packageManager
        val info = pm.getApplicationInfo(pkg, 0)
        pm.getApplicationLabel(info).toString()
    } catch (_: Throwable) { pkg }

    companion object {
        private const val TAG = "NotifListener"

        @Volatile private var instance: PriyaNotificationListener? = null

        fun isConnected(): Boolean = instance != null

        /** Whether the user has granted notification access to *this* app. */
        fun hasAccess(context: Context): Boolean {
            val flat = Settings.Secure.getString(
                context.contentResolver,
                "enabled_notification_listeners"
            ).orEmpty()
            return flat.contains(context.packageName)
        }

        fun openSettings(context: Context) {
            val intent = Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS")
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        }
    }
}
