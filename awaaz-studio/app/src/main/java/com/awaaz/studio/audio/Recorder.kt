package com.awaaz.studio.audio

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import kotlin.coroutines.coroutineContext
import kotlin.math.sqrt

class Recorder(val rate: Int = 16000) {
    @Volatile var level = 0f
        private set
    @Volatile private var running = false

    fun stop() { running = false }

    @SuppressLint("MissingPermission")
    suspend fun record(): Pcm = withContext(Dispatchers.IO) {
        val min = AudioRecord.getMinBufferSize(rate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        val rec = AudioRecord(MediaRecorder.AudioSource.VOICE_RECOGNITION, rate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, min * 4)
        val out = FloatList()
        val buf = ShortArray(rate / 10)
        running = true
        rec.startRecording()
        try {
            while (running && coroutineContext.isActive) {
                val n = rec.read(buf, 0, buf.size)
                if (n <= 0) continue
                var acc = 0.0
                for (i in 0 until n) {
                    val v = buf[i] / 32768f
                    out.add(v)
                    acc += v * v
                }
                level = sqrt(acc / n).toFloat()
            }
        } finally {
            rec.stop()
            rec.release()
            level = 0f
        }
        Pcm(out.toArray(), rate)
    }
}
