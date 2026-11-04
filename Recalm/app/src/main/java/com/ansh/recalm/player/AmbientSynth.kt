package com.ansh.recalm.player

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import kotlin.math.PI
import kotlin.math.sin

data class Soundscape(val id: String, val title: String, val subtitle: String)

object Soundscapes {
    val all = listOf(
        Soundscape("none", "Off", "Voice only"),
        Soundscape("rain", "Soft Rain", "Gentle rain on a quiet night"),
        Soundscape("ocean", "Slow Ocean", "Waves breathing in and out"),
        Soundscape("brown", "Deep Brown Noise", "Warm, low and steady"),
        Soundscape("pad", "Night Pad", "Slow evolving ambient chords"),
        Soundscape("delta", "Delta Drift", "2.5 Hz binaural beat for deep sleep, use earphones"),
        Soundscape("theta", "Theta Calm", "6 Hz binaural beat for relaxation, use earphones"),
        Soundscape("space", "Starlight", "Soft shimmering drone")
    )
}

class AmbientSynth {

    private val rate = 44100
    @Volatile private var running = false
    @Volatile private var scene = "none"
    @Volatile private var targetVolume = 0.35f
    @Volatile private var fade = 1f
    private var thread: Thread? = null

    private var seed = 0x2545F491L
    private var pink = FloatArray(7)
    private var brownL = 0f
    private var brownR = 0f
    private var lowL = 0f
    private var lowR = 0f
    private var t = 0L
    private val phases = DoubleArray(16)
    private var drop = 0f
    private var dropFreq = 0.0
    private var dropPhase = 0.0

    val isPlaying get() = running && scene != "none"

    fun setVolume(volume: Float) {
        targetVolume = volume.coerceIn(0f, 1f)
    }

    fun setFade(value: Float) {
        fade = value.coerceIn(0f, 1f)
    }

    fun play(id: String) {
        scene = id
        if (id == "none") {
            stop()
            return
        }
        if (running) return
        running = true
        thread = Thread({ loop() }, "ambient").apply {
            priority = Thread.NORM_PRIORITY
            start()
        }
    }

    fun stop() {
        running = false
        thread?.join(500)
        thread = null
    }

    private fun white(): Float {
        seed = seed xor (seed shl 13)
        seed = seed xor (seed ushr 7)
        seed = seed xor (seed shl 17)
        return ((seed ushr 40).toInt() and 0xFFFFFF) / 8388608f - 1f
    }

    private fun pinkNoise(): Float {
        val w = white()
        pink[0] = 0.99886f * pink[0] + w * 0.0555179f
        pink[1] = 0.99332f * pink[1] + w * 0.0750759f
        pink[2] = 0.96900f * pink[2] + w * 0.1538520f
        pink[3] = 0.86650f * pink[3] + w * 0.3104856f
        pink[4] = 0.55000f * pink[4] + w * 0.5329522f
        pink[5] = -0.7616f * pink[5] - w * 0.0168980f
        val out = pink.sum() + pink[6] + w * 0.5362f
        pink[6] = w * 0.115926f
        return out * 0.11f
    }

    private fun tone(index: Int, freq: Double): Float {
        phases[index] += 2 * PI * freq / rate
        if (phases[index] > 2 * PI) phases[index] -= 2 * PI
        return sin(phases[index]).toFloat()
    }

    private val padChords = arrayOf(
        doubleArrayOf(110.0, 164.81, 220.0, 277.18),
        doubleArrayOf(98.0, 146.83, 196.0, 246.94),
        doubleArrayOf(87.31, 130.81, 174.61, 220.0),
        doubleArrayOf(103.83, 155.56, 207.65, 261.63)
    )

    private fun frame(out: FloatArray, i: Int) {
        val seconds = t.toDouble() / rate
        var l: Float
        var r: Float
        when (scene) {
            "rain" -> {
                val n = pinkNoise()
                lowL += 0.18f * (n - lowL)
                lowR += 0.18f * (pinkNoise() - lowR)
                if (white() > 0.99965f) {
                    drop = 0.25f + 0.25f * white()
                    dropFreq = 2200.0 + 1600.0 * white()
                }
                dropPhase += 2 * PI * dropFreq / rate
                val d = (drop * sin(dropPhase)).toFloat()
                drop *= 0.9975f
                l = lowL * 1.6f + d * 0.25f
                r = lowR * 1.6f + d * 0.18f
            }
            "ocean" -> {
                brownL = (brownL + 0.02f * white()) / 1.02f
                brownR = (brownR + 0.02f * white()) / 1.02f
                val swell = (0.55 + 0.45 * sin(2 * PI * seconds / 9.5)).toFloat()
                val swell2 = (0.55 + 0.45 * sin(2 * PI * seconds / 11.3 + 1.3)).toFloat()
                val hiss = pinkNoise() * 0.35f
                l = (brownL * 3.2f + hiss) * swell
                r = (brownR * 3.2f + hiss) * swell2
            }
            "brown" -> {
                brownL = (brownL + 0.02f * white()) / 1.02f
                brownR = (brownR + 0.02f * white()) / 1.02f
                l = brownL * 3.5f
                r = brownR * 3.5f
            }
            "pad" -> {
                val chordLength = 24.0
                val pos = seconds / chordLength
                val a = (pos.toInt()) % padChords.size
                val b = (a + 1) % padChords.size
                val mix = ((pos - pos.toInt()).let { if (it < 0.8) 0.0 else (it - 0.8) / 0.2 }).toFloat()
                var sl = 0f
                var sr = 0f
                for (k in 0 until 4) {
                    val lfo = (0.6 + 0.4 * sin(2 * PI * seconds / (7.0 + k * 2.3) + k)).toFloat()
                    val va = tone(k, padChords[a][k]) * (1 - mix) + tone(k + 4, padChords[b][k]) * mix
                    val vb = tone(k + 8, padChords[a][k] * 1.003) * (1 - mix) + tone(k + 12, padChords[b][k] * 1.003) * mix
                    sl += va * lfo
                    sr += vb * lfo
                }
                val air = pinkNoise() * 0.08f
                l = sl * 0.11f + air
                r = sr * 0.11f + air
            }
            "delta" -> {
                val bed = pinkNoise() * 0.25f
                lowL += 0.05f * (bed - lowL)
                l = tone(0, 100.0) * 0.22f + lowL
                r = tone(1, 102.5) * 0.22f + lowL
            }
            "theta" -> {
                val bed = pinkNoise() * 0.25f
                lowL += 0.05f * (bed - lowL)
                l = tone(0, 140.0) * 0.2f + lowL
                r = tone(1, 146.0) * 0.2f + lowL
            }
            "space" -> {
                val shimmer = (0.5 + 0.5 * sin(2 * PI * seconds / 13.0)).toFloat()
                val base = tone(0, 108.0) * 0.16f + tone(1, 162.0) * 0.08f
                val high = tone(2, 432.0) * 0.03f * shimmer + tone(3, 648.0) * 0.015f * (1 - shimmer)
                val air = pinkNoise() * 0.06f
                l = base + high + air
                r = base + tone(4, 433.2) * 0.03f * shimmer + air
            }
            else -> {
                l = 0f
                r = 0f
            }
        }
        out[i] = l
        out[i + 1] = r
        t++
    }

    private fun loop() {
        val minBytes = AudioTrack.getMinBufferSize(rate, AudioFormat.CHANNEL_OUT_STEREO, AudioFormat.ENCODING_PCM_FLOAT)
        val track = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setSampleRate(rate)
                    .setEncoding(AudioFormat.ENCODING_PCM_FLOAT)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_STEREO)
                    .build()
            )
            .setBufferSizeInBytes(maxOf(minBytes, 8192 * 4))
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()
        val buffer = FloatArray(2048)
        var gain = 0f
        track.play()
        try {
            while (running) {
                val target = targetVolume * fade
                var i = 0
                while (i < buffer.size) {
                    gain += (target - gain) * 0.00005f
                    frame(buffer, i)
                    buffer[i] = (buffer[i] * gain).coerceIn(-1f, 1f)
                    buffer[i + 1] = (buffer[i + 1] * gain).coerceIn(-1f, 1f)
                    i += 2
                }
                track.write(buffer, 0, buffer.size, AudioTrack.WRITE_BLOCKING)
            }
            var i = 0
            while (i < buffer.size) {
                val k = 1f - i.toFloat() / buffer.size
                frame(buffer, i)
                buffer[i] *= gain * k
                buffer[i + 1] *= gain * k
                i += 2
            }
            track.write(buffer, 0, buffer.size, AudioTrack.WRITE_BLOCKING)
        } finally {
            track.stop()
            track.release()
        }
    }
}
