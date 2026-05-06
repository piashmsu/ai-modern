package com.piash.priya.services

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.piash.priya.MainActivity
import com.piash.priya.PriyaApplication
import com.piash.priya.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Long-lived foreground service that keeps the [voice pipeline] alive while
 * the user has live mode toggled on. Runs with the `microphone` and
 * `specialUse` foreground-service types so it can record audio in the
 * background on Android 14+.
 */
class PriyaForegroundService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startInForeground()
        when (intent?.action) {
            ACTION_STOP -> {
                stopPipeline()
                stopSelf()
                return START_NOT_STICKY
            }
            else -> startPipeline()
        }
        return START_STICKY
    }

    private fun startPipeline() {
        scope.launch {
            try {
                PriyaApplication.get().voicePipeline.start()
            } catch (_: Throwable) { /* surfaced via state flow */ }
        }
    }

    private fun stopPipeline() {
        scope.launch {
            try { PriyaApplication.get().voicePipeline.stop() } catch (_: Throwable) {}
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        stopPipeline()
        scope.cancel()
    }

    private fun startInForeground() {
        val openIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val stopIntent = PendingIntent.getService(
            this, 1,
            Intent(this, PriyaForegroundService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val notif: Notification = NotificationCompat.Builder(this, PriyaApplication.CHANNEL_ASSISTANT)
            .setContentTitle(getString(R.string.notif_listening))
            .setContentText(getString(R.string.notif_listening_text))
            .setSmallIcon(R.drawable.ic_priya)
            .setOngoing(true)
            .setContentIntent(openIntent)
            .addAction(0, getString(R.string.action_stop), stopIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                NOTIF_ID, notif,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE or
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
        } else {
            startForeground(NOTIF_ID, notif)
        }
    }

    companion object {
        private const val NOTIF_ID = 4242
        const val ACTION_STOP = "com.piash.priya.STOP_ASSISTANT"

        fun start(context: Context) {
            val intent = Intent(context, PriyaForegroundService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            context.startService(
                Intent(context, PriyaForegroundService::class.java).setAction(ACTION_STOP)
            )
        }
    }
}
