package com.anshpandey.pingai

import android.app.Activity
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import java.util.Locale

object AlertCenter {

    const val ACTION_RECEIVE = "com.anshpandey.pingai.ALERT_RECEIVE"
    const val ACTION_CUT = "com.anshpandey.pingai.ALERT_CUT"
    const val ACTION_MESSAGE = "com.anshpandey.pingai.ALERT_MESSAGE"

    private const val CHANNEL = "call_v1"
    private const val NOTIF_ID = 4242

    private val handler = Handler(Looper.getMainLooper())
    private var appContext: Context? = null
    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private var queuedSpeech: String? = null

    @Volatile
    var active = false

    var key = ""
    var sender = ""
    var message = ""
    var hinglish = true
    var screen: Activity? = null

    private val loop = object : Runnable {
        override fun run() {
            if (!active) return
            playTone()
            handler.postDelayed(this, 1800)
        }
    }

    private val timeout = Runnable { appContext?.let { cut(it) } }

    fun start(context: Context, key: String, sender: String, message: String, hinglish: Boolean) {
        val ctx = context.applicationContext
        appContext = ctx
        this.key = key
        this.sender = sender
        this.message = message
        this.hinglish = hinglish
        handler.post {
            active = true
            ensureChannel(ctx)
            post(ctx, false)
            handler.removeCallbacks(loop)
            handler.post(loop)
            handler.removeCallbacks(timeout)
            handler.postDelayed(timeout, 60_000L)
        }
    }

    fun receive(context: Context) {
        val ctx = context.applicationContext
        handler.post {
            if (!active) return@post
            handler.removeCallbacks(loop)
            post(ctx, true)
            speak(ctx, "Call request from $sender. They said: ${message.take(300)}")
        }
    }

    fun cut(context: Context) {
        val ctx = context.applicationContext
        handler.post {
            active = false
            handler.removeCallbacks(loop)
            handler.removeCallbacks(timeout)
            tts?.stop()
            NotificationManagerCompat.from(ctx).cancel(NOTIF_ID)
            screen?.finish()
        }
    }

    fun quickMessage(context: Context) {
        val ctx = context.applicationContext
        val pair = ReplyBridge.actions[key]
        if (pair != null) {
            val text = ReplyEngine(ctx).callbackLine(hinglish)
            Thread {
                runCatching {
                    ReplyBridge.deliver(ctx, pair.first, pair.second, text)
                    Store(ctx).addEvent("out", sender, text)
                }
            }.start()
        }
        cut(ctx)
    }

    private fun playTone() {
        val ctx = appContext ?: return
        runCatching {
            val attrs = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
            val player = MediaPlayer.create(ctx, R.raw.urgent_tone, attrs, AudioManager.AUDIO_SESSION_ID_GENERATE)
            if (player != null) {
                player.setOnCompletionListener { it.release() }
                player.start()
            }
        }
    }

    private fun speak(ctx: Context, text: String) {
        val engine = tts
        if (engine != null && ttsReady) {
            engine.speak(text, TextToSpeech.QUEUE_FLUSH, null, "alert")
            return
        }
        queuedSpeech = text
        if (engine == null) {
            tts = TextToSpeech(ctx) { status ->
                ttsReady = status == TextToSpeech.SUCCESS
                val created = tts
                if (ttsReady && created != null) {
                    val r = created.setLanguage(Locale("en", "IN"))
                    if (r == TextToSpeech.LANG_MISSING_DATA || r == TextToSpeech.LANG_NOT_SUPPORTED) {
                        created.setLanguage(Locale.US)
                    }
                    queuedSpeech?.let { created.speak(it, TextToSpeech.QUEUE_FLUSH, null, "alert") }
                    queuedSpeech = null
                }
            }
        }
    }

    private fun ensureChannel(ctx: Context) {
        if (Build.VERSION.SDK_INT < 26) return
        val manager = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (manager.getNotificationChannel(CHANNEL) != null) return
        val channel = NotificationChannel(CHANNEL, "Call requests", NotificationManager.IMPORTANCE_HIGH)
        channel.description = "Rings like a call when someone asks you to call back"
        channel.setSound(null, null)
        channel.enableVibration(true)
        channel.lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
        manager.createNotificationChannel(channel)
    }

    private fun flags(): Int =
        PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= 23) PendingIntent.FLAG_IMMUTABLE else 0)

    private fun broadcast(ctx: Context, action: String, code: Int): PendingIntent =
        PendingIntent.getBroadcast(ctx, code, Intent(ctx, AlertReceiver::class.java).setAction(action), flags())

    private fun post(ctx: Context, receiving: Boolean) {
        val full = PendingIntent.getActivity(
            ctx, 1,
            Intent(ctx, EmergencyActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            flags()
        )
        val builder = NotificationCompat.Builder(ctx, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat)
            .setContentTitle(if (receiving) "Speaking: $sender" else "Call request: $sender")
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setOngoing(true)
            .setAutoCancel(false)
            .setFullScreenIntent(full, true)
            .setContentIntent(full)
        if (!receiving) builder.addAction(R.drawable.ic_stat, "Receive", broadcast(ctx, ACTION_RECEIVE, 11))
        builder.addAction(R.drawable.ic_stat, "Cut", broadcast(ctx, ACTION_CUT, 12))
        builder.addAction(R.drawable.ic_stat, "Message", broadcast(ctx, ACTION_MESSAGE, 13))
        NotificationManagerCompat.from(ctx).notify(NOTIF_ID, builder.build())
    }
}
