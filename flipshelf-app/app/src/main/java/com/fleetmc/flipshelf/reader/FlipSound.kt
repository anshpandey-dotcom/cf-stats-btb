package com.fleetmc.flipshelf.reader

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

class FlipSound {

    private val rate = 22050
    private var track: AudioTrack? = null

    init {
        try {
            val data = synthesize()
            val t = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_GAME)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(rate)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build()
                )
                .setTransferMode(AudioTrack.MODE_STATIC)
                .setBufferSizeInBytes(data.size * 2)
                .build()
            t.write(data, 0, data.size)
            track = t
        } catch (_: Throwable) {
            track = null
        }
    }

    private fun synthesize(): ShortArray {
        val n = (rate * 0.34f).toInt()
        val out = ShortArray(n)
        val rnd = Random(11)
        var low = 0f
        var prev = 0f
        for (i in 0 until n) {
            val t = i / n.toFloat()
            val attack = min(1f, t * 14f)
            val body = exp(-4.2f * t)
            val flutter = 0.65f + 0.35f * sin(2f * PI.toFloat() * 19f * t)
            val noise = rnd.nextFloat() * 2f - 1f
            low += (noise - low) * 0.42f
            val band = low - prev * 0.55f
            prev = low
            out[i] = (band * attack * body * flutter * 9000f).toInt().coerceIn(-32000, 32000).toShort()
        }
        return out
    }

    fun play() {
        val t = track ?: return
        try {
            t.stop()
            t.reloadStaticData()
            t.play()
        } catch (_: Throwable) {
        }
    }

    fun release() {
        try {
            track?.release()
        } catch (_: Throwable) {
        }
        track = null
    }
}
