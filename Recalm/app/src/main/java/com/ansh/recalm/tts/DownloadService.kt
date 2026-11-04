package com.ansh.recalm.tts

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import com.ansh.recalm.MainActivity
import com.ansh.recalm.R
import com.ansh.recalm.RecalmApp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class DownloadService : LifecycleService() {

    private val voices get() = (application as RecalmApp).voices

    override fun onCreate() {
        super.onCreate()
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL, "Voice downloads", NotificationManager.IMPORTANCE_LOW).apply { setShowBadge(false) }
        )
        foreground(build(voices.states.value))
        lifecycleScope.launch {
            voices.states.collect { states ->
                getSystemService(NotificationManager::class.java).notify(ID, build(states))
            }
        }
        lifecycleScope.launch {
            delay(3000)
            while (voices.busy) delay(1500)
            ServiceCompat.stopForeground(this@DownloadService, ServiceCompat.STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        foreground(build(voices.states.value))
        return START_NOT_STICKY
    }

    private fun foreground(notification: Notification) {
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC else 0
        ServiceCompat.startForeground(this, ID, notification, type)
    }

    private fun build(states: Map<String, PackState>): Notification {
        val active = states.entries.firstOrNull { it.value is PackState.Downloading || it.value == PackState.Installing }
        val title = active?.let { VoiceCatalog.pack(it.key)?.title } ?: "Voices"
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        val builder = NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentIntent(open)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setOngoing(true)
        when (val state = active?.value) {
            is PackState.Downloading -> builder.setContentTitle("Downloading $title")
                .setContentText("${(state.progress * 100).toInt()}%")
                .setProgress(100, (state.progress * 100).toInt(), state.progress <= 0f)
            PackState.Installing -> builder.setContentTitle("Installing $title").setContentText("Unpacking the voice").setProgress(0, 0, true)
            else -> builder.setContentTitle("Voices ready")
        }
        return builder.build()
    }

    companion object {
        private const val CHANNEL = "downloads"
        private const val ID = 8

        fun start(context: Context) {
            runCatching { ContextCompat.startForegroundService(context, Intent(context, DownloadService::class.java)) }
        }
    }
}
