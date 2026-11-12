package com.ansh.memyclock.alarm

import android.animation.ValueAnimator
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.core.app.ServiceCompat
import com.ansh.memyclock.data.Alarm
import com.ansh.memyclock.data.AlarmStore
import com.ansh.memyclock.data.Repeat
import com.ansh.memyclock.data.Settings
import com.ansh.memyclock.data.Tones
import kotlinx.coroutines.flow.MutableStateFlow
import org.json.JSONObject
import java.io.File
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

enum class RingKind { ALARM, PREVIEW, TIMER }

data class Ringing(val alarm: Alarm, val kind: RingKind)

class AlarmService : Service() {
    private val handler = Handler(Looper.getMainLooper())
    private var tone: MediaPlayer? = null
    private var speech: MediaPlayer? = null
    private var fade: ValueAnimator? = null
    private var toneVolume = 1f
    private var wakeLock: PowerManager.WakeLock? = null
    private var focus: AudioFocusRequest? = null
    private val alarmAudio = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_ALARM)
        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
        .build()

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Settings.init(this)
        AlarmStore.init(this)
        when (intent?.action) {
            ACTION_RING -> {
                val alarm = AlarmStore.get(intent.getIntExtra(EXTRA_ID, 0))
                if (alarm == null) skip() else begin(Ringing(alarm, RingKind.ALARM))
            }
            ACTION_PREVIEW -> {
                val alarm = Alarm.fromJson(JSONObject(intent.getStringExtra(EXTRA_ALARM) ?: "{}"))
                begin(Ringing(alarm, RingKind.PREVIEW))
            }
            ACTION_TIMER -> {
                Countdown.load()
                Countdown.clear()
                begin(Ringing(Alarm(label = "Timer finished", tone = Settings.timerTone), RingKind.TIMER))
            }
            ACTION_SNOOZE -> finish(Outcome.SNOOZE)
            ACTION_DISMISS -> finish(Outcome.DISMISS)
            else -> idle()
        }
        return START_NOT_STICKY
    }

    private fun idle() {
        if (ringing.value == null) stopSelf()
    }

    private fun skip() {
        if (ringing.value != null) return
        val type = if (Build.VERSION.SDK_INT >= 29) ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK else 0
        ServiceCompat.startForeground(this, Notifications.RING_ID, Notifications.ringing(this, "Alarm", "", false), type)
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun begin(next: Ringing) {
        if (ringing.value != null) settle(Outcome.DISMISS)
        ringing.value = next
        val alarm = next.alarm
        val title = alarm.label.ifBlank { if (next.kind == RingKind.TIMER) "Timer" else "Alarm" }
        val text = when (next.kind) {
            RingKind.TIMER -> "Time is up"
            RingKind.PREVIEW -> "Preview"
            RingKind.ALARM -> LocalTime.now().format(DateTimeFormatter.ofPattern("hh:mm a", Locale.ENGLISH))
        }
        val notification = Notifications.ringing(this, title, text, next.kind == RingKind.ALARM)
        val type = if (Build.VERSION.SDK_INT >= 29) ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK else 0
        ServiceCompat.startForeground(this, Notifications.RING_ID, notification, type)

        wakeLock = getSystemService(PowerManager::class.java)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "memyclock:ring")
            .apply { acquire(TIMEOUT + 10_000) }
        requestFocus()
        startTone(alarm)
        if (alarm.vibrate) startVibration()
        if (alarm.voiceOn && alarm.voiceFile.isNotEmpty() && File(alarm.voiceFile).exists()) {
            handler.postDelayed({ speak(alarm.voiceFile) }, 2500)
        }
        handler.postDelayed({ finish(Outcome.MISSED) }, TIMEOUT)
        if (next.kind == RingKind.PREVIEW) {
            startActivity(Intent(this, RingActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }

    private fun startTone(alarm: Alarm) {
        toneVolume = 1f
        tone = runCatching {
            MediaPlayer().apply {
                setAudioAttributes(alarmAudio)
                setDataSource(this@AlarmService, Uri.parse("android.resource://$packageName/${Tones.byId(alarm.tone).res}"))
                isLooping = true
                prepare()
                start()
            }
        }.getOrNull()
    }

    private fun speak(path: String) {
        if (ringing.value == null) return
        fadeTone(DUCKED) {
            speech = runCatching {
                MediaPlayer().apply {
                    setAudioAttributes(alarmAudio)
                    setDataSource(path)
                    setOnCompletionListener {
                        it.release()
                        speech = null
                        fadeTone(1f) {}
                        handler.postDelayed({ speak(path) }, Settings.voiceGapSeconds * 1000L)
                    }
                    prepare()
                    start()
                }
            }.getOrNull()
            if (speech == null) fadeTone(1f) {}
        }
    }

    private fun fadeTone(target: Float, then: () -> Unit) {
        fade?.cancel()
        fade = ValueAnimator.ofFloat(toneVolume, target).apply {
            duration = 450
            addUpdateListener {
                toneVolume = it.animatedValue as Float
                runCatching { tone?.setVolume(toneVolume, toneVolume) }
            }
            start()
        }
        handler.postDelayed(then, 460)
    }

    private fun startVibration() {
        val vibrator = getSystemService(Vibrator::class.java) ?: return
        val effect = VibrationEffect.createWaveform(longArrayOf(0, 600, 500), 0)
        if (Build.VERSION.SDK_INT >= 33) {
            vibrator.vibrate(effect, VibrationAttributes.createForUsage(VibrationAttributes.USAGE_ALARM))
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(effect, alarmAudio)
        }
    }

    private fun requestFocus() {
        val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
            .setAudioAttributes(alarmAudio)
            .build()
        focus = request
        getSystemService(AudioManager::class.java).requestAudioFocus(request)
    }

    private fun settle(outcome: Outcome) {
        val current = ringing.value ?: return
        handler.removeCallbacksAndMessages(null)
        fade?.cancel()
        runCatching { tone?.release() }
        runCatching { speech?.release() }
        tone = null
        speech = null
        getSystemService(Vibrator::class.java)?.cancel()
        focus?.let { getSystemService(AudioManager::class.java).abandonAudioFocusRequest(it) }
        focus = null
        wakeLock?.takeIf { it.isHeld }?.release()
        wakeLock = null

        if (current.kind == RingKind.ALARM) {
            val updated = AlarmStore.update(current.alarm.id) {
                if (outcome == Outcome.SNOOZE) {
                    it.copy(snoozeUntil = System.currentTimeMillis() + Settings.snoozeMinutes * 60_000L)
                } else {
                    val oneShot = it.repeat == Repeat.ONCE || it.repeat == Repeat.DATE
                    it.copy(snoozeUntil = 0, enabled = it.enabled && !oneShot)
                }
            }
            updated?.let { AlarmScheduler.sync(this, it) }
            if (outcome == Outcome.MISSED) {
                val time = "%02d:%02d".format(current.alarm.hour, current.alarm.minute)
                Notifications.missed(this, current.alarm.label, time)
            }
        }
        ringing.value = null
    }

    private fun finish(outcome: Outcome) {
        settle(outcome)
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        settle(Outcome.DISMISS)
        super.onDestroy()
    }

    private enum class Outcome { DISMISS, SNOOZE, MISSED }

    companion object {
        const val ACTION_RING = "com.ansh.memyclock.RING"
        const val ACTION_PREVIEW = "com.ansh.memyclock.PREVIEW"
        const val ACTION_TIMER = "com.ansh.memyclock.TIMER"
        const val ACTION_SNOOZE = "com.ansh.memyclock.SNOOZE"
        const val ACTION_DISMISS = "com.ansh.memyclock.DISMISS"
        const val EXTRA_ID = "id"
        const val EXTRA_ALARM = "alarm"
        private const val TIMEOUT = 10 * 60_000L
        private const val DUCKED = 0.12f

        val ringing = MutableStateFlow<Ringing?>(null)

        fun preview(context: Context, alarm: Alarm) {
            val intent = Intent(context, AlarmService::class.java)
                .setAction(ACTION_PREVIEW)
                .putExtra(EXTRA_ALARM, alarm.toJson().toString())
            androidx.core.content.ContextCompat.startForegroundService(context, intent)
        }

        fun send(context: Context, action: String) {
            runCatching { context.startService(Intent(context, AlarmService::class.java).setAction(action)) }
        }
    }
}
