package com.piash.priya

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import com.piash.priya.ai.ChatEngine
import com.piash.priya.ai.ProviderRegistry
import com.piash.priya.automation.AppLauncher
import com.piash.priya.data.ChatHistoryStore
import com.piash.priya.data.SecureKeyStore
import com.piash.priya.data.SettingsRepository
import com.piash.priya.util.AuditLog
import com.piash.priya.voice.IntentRouter
import com.piash.priya.voice.VoicePipeline

class PriyaApplication : Application() {

    val secureKeys: SecureKeyStore by lazy { SecureKeyStore(this) }
    val settings: SettingsRepository by lazy { SettingsRepository(this) }
    val providers: ProviderRegistry by lazy { ProviderRegistry(secureKeys, settings) }
    val chatHistory: ChatHistoryStore by lazy { ChatHistoryStore(this) }
    val chatEngine: ChatEngine by lazy { ChatEngine(providers, settings, chatHistory) }
    val appLauncher: AppLauncher by lazy { AppLauncher(this) }
    val intentRouter: IntentRouter by lazy { IntentRouter(appLauncher) }
    val voicePipeline: VoicePipeline by lazy {
        VoicePipeline(this, settings, secureKeys, chatEngine, intentRouter)
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        AuditLog.init(this)
        createNotificationChannels()
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = getSystemService(NotificationManager::class.java) ?: return
        nm.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ASSISTANT,
                getString(R.string.notif_channel_assistant),
                NotificationManager.IMPORTANCE_LOW
            ).apply { description = getString(R.string.notif_channel_assistant_desc) }
        )
        nm.createNotificationChannel(
            NotificationChannel(
                CHANNEL_OVERLAY,
                getString(R.string.notif_channel_overlay),
                NotificationManager.IMPORTANCE_MIN
            ).apply { description = getString(R.string.notif_channel_overlay_desc) }
        )
    }

    companion object {
        const val CHANNEL_ASSISTANT = "priya_assistant"
        const val CHANNEL_OVERLAY = "priya_overlay"

        @Volatile
        private var instance: PriyaApplication? = null
        fun get(): PriyaApplication = instance!!
    }
}
