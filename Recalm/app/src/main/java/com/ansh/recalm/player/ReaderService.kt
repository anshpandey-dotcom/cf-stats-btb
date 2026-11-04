package com.ansh.recalm.player

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.os.Build
import android.os.PowerManager
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import com.ansh.recalm.MainActivity
import com.ansh.recalm.R
import com.ansh.recalm.RecalmApp
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class ReaderService : LifecycleService() {

    private lateinit var session: MediaSessionCompat
    private var wakeLock: PowerManager.WakeLock? = null
    private var stopJob: Job? = null
    private var coverFor: String? = null
    private var cover: Bitmap? = null

    private val app get() = application as RecalmApp

    override fun onCreate() {
        super.onCreate()
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL, "Reading", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Shows what Recalm is reading"
                setShowBadge(false)
            }
        )
        session = MediaSessionCompat(this, "Recalm").apply {
            setCallback(object : MediaSessionCompat.Callback() {
                override fun onPlay() = app.engine.toggle()
                override fun onPause() = app.engine.pause()
                override fun onSkipToNext() = app.engine.skipSentence(1)
                override fun onSkipToPrevious() = app.engine.skipSentence(-1)
                override fun onStop() = app.engine.stop()
            })
            isActive = true
        }
        startInForeground(build())
        lifecycleScope.launch {
            app.engine.state.collect { refresh() }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        when (intent?.action) {
            ACTION_TOGGLE -> app.engine.toggle()
            ACTION_NEXT -> app.engine.skipSentence(1)
            ACTION_PREV -> app.engine.skipSentence(-1)
            ACTION_STOP -> app.engine.stop()
        }
        startInForeground(build())
        return START_NOT_STICKY
    }

    private fun startInForeground(notification: Notification) {
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK else 0
        ServiceCompat.startForeground(this, NOTIFICATION_ID, notification, type)
    }

    private fun refresh() {
        val s = app.engine.state.value
        val busy = s.playing || s.musicPlaying || s.loading != null
        if (busy) acquire() else release()
        val alive = s.active || s.musicPlaying || s.sleepEndsAt != null
        session.setPlaybackState(
            PlaybackStateCompat.Builder()
                .setActions(
                    PlaybackStateCompat.ACTION_PLAY_PAUSE or PlaybackStateCompat.ACTION_PLAY or
                        PlaybackStateCompat.ACTION_PAUSE or PlaybackStateCompat.ACTION_SKIP_TO_NEXT or
                        PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS or PlaybackStateCompat.ACTION_STOP
                )
                .setState(if (s.playing) PlaybackStateCompat.STATE_PLAYING else PlaybackStateCompat.STATE_PAUSED,
                    PlaybackStateCompat.PLAYBACK_POSITION_UNKNOWN, 1f)
                .build()
        )
        getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, build())
        if (!alive) {
            if (stopJob == null) stopJob = lifecycleScope.launch {
                delay(1500)
                val now = app.engine.state.value
                if (!now.active && !now.musicPlaying && now.sleepEndsAt == null) {
                    ServiceCompat.stopForeground(this@ReaderService, ServiceCompat.STOP_FOREGROUND_REMOVE)
                    stopSelf()
                }
            }
        } else {
            stopJob?.cancel()
            stopJob = null
        }
    }

    private fun build(): Notification {
        val s = app.engine.state.value
        val book = s.bookId?.let { app.library.book(it) }
        if (book != null && coverFor != book.id) {
            coverFor = book.id
            cover = app.library.cover(book.id)
        }
        val title = book?.title ?: "Recalm"
        val text = when {
            s.loading != null -> s.loading
            book != null && s.active -> "Page ${s.page + 1} of ${book.pageCount}" + timerText(s.sleepEndsAt)
            s.musicPlaying -> "Sleep sounds" + timerText(s.sleepEndsAt)
            else -> "Paused"
        }
        session.setMetadata(
            MediaMetadataCompat.Builder()
                .putString(MediaMetadataCompat.METADATA_KEY_TITLE, title)
                .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, text)
                .apply { cover?.let { putBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART, it) } }
                .build()
        )
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }, PendingIntent.FLAG_IMMUTABLE)
        return NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(text)
            .setLargeIcon(cover)
            .setContentIntent(open)
            .setOngoing(s.playing)
            .setSilent(true)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .addAction(R.drawable.ic_prev, "Previous", action(ACTION_PREV, 1))
            .addAction(if (s.playing) R.drawable.ic_pause else R.drawable.ic_play, if (s.playing) "Pause" else "Play", action(ACTION_TOGGLE, 2))
            .addAction(R.drawable.ic_next, "Next", action(ACTION_NEXT, 3))
            .addAction(R.drawable.ic_stop, "Stop", action(ACTION_STOP, 4))
            .setStyle(
                androidx.media.app.NotificationCompat.MediaStyle()
                    .setMediaSession(session.sessionToken)
                    .setShowActionsInCompactView(0, 1, 2)
            )
            .build()
    }

    private fun timerText(endsAt: Long?): String {
        if (endsAt == null) return ""
        val minutes = ((endsAt - System.currentTimeMillis()) / 60_000L).coerceAtLeast(0) + 1
        return "  ·  Sleep in $minutes min"
    }

    private fun action(name: String, code: Int): PendingIntent =
        PendingIntent.getService(this, code, Intent(this, ReaderService::class.java).setAction(name), PendingIntent.FLAG_IMMUTABLE)

    private fun acquire() {
        if (wakeLock?.isHeld == true) return
        wakeLock = getSystemService(PowerManager::class.java)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Recalm:reading")
            .apply { acquire(3 * 60 * 60 * 1000L) }
    }

    private fun release() {
        wakeLock?.let { if (it.isHeld) it.release() }
        wakeLock = null
    }

    override fun onDestroy() {
        release()
        session.release()
        super.onDestroy()
    }

    companion object {
        private const val CHANNEL = "reading"
        private const val NOTIFICATION_ID = 7
        const val ACTION_TOGGLE = "recalm.toggle"
        const val ACTION_NEXT = "recalm.next"
        const val ACTION_PREV = "recalm.prev"
        const val ACTION_STOP = "recalm.stop"
    }
}
