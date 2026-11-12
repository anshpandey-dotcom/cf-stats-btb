package com.ansh.memyclock.voice

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import com.ansh.memyclock.data.Alarm
import com.k2fsa.sherpa.onnx.OfflineTts
import com.k2fsa.sherpa.onnx.OfflineTtsConfig
import com.k2fsa.sherpa.onnx.OfflineTtsModelConfig
import com.k2fsa.sherpa.onnx.OfflineTtsVitsModelConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest
import java.util.Locale
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.math.abs

object Speech {
    suspend fun build(context: Context, alarm: Alarm, status: (String) -> Unit): Alarm {
        if (alarm.voiceReady) return alarm
        val text = alarm.voiceText.trim()
        require(text.isNotEmpty()) { "Type a voice message first" }
        val voice = Voices.byId(alarm.voice)
        if (!Voices.installed(context, voice)) {
            Voices.download(context, voice) { status("Downloading ${voice.name} voice  ${(it * 100).toInt()}%") }
        }
        status("Preparing text")
        val spoken = if (alarm.toHindi) Hinglish.toHindi(text) else text
        status("Creating voice")
        val key = alarm.wantedVoiceKey
        val file = File(File(context.filesDir, "tts").apply { mkdirs() }, "alarm_${hash(key + spoken)}.wav")
        if (voice.neural) neural(context, voice, spoken, file) else device(context, spoken, file)
        if (!file.exists() || file.length() < 2048) error("The voice could not be created")
        return alarm.copy(spoken = spoken, voiceFile = file.path, voiceKey = key)
    }

    private suspend fun neural(context: Context, voice: Voice, text: String, file: File) = withContext(Dispatchers.Default) {
        val dir = Voices.dir(context, voice)
        val config = OfflineTtsConfig(
            model = OfflineTtsModelConfig(
                vits = OfflineTtsVitsModelConfig(
                    model = File(dir, voice.model).path,
                    tokens = File(dir, "tokens.txt").path,
                    dataDir = File(dir, "espeak-ng-data").path
                ),
                numThreads = 2
            )
        )
        val engine = OfflineTts(config = config)
        try {
            val audio = engine.generate(text = text, sid = 0, speed = 0.95f)
            if (audio.samples.isEmpty()) error("The voice could not be created")
            writeWav(file, audio.samples, audio.sampleRate)
        } finally {
            engine.release()
        }
    }

    private suspend fun device(context: Context, text: String, file: File) = withContext(Dispatchers.Main) {
        withTimeout(40_000) {
            suspendCancellableCoroutine { continuation ->
                var engine: TextToSpeech? = null
                engine = TextToSpeech(context.applicationContext) { state ->
                    val tts = engine
                    if (state != TextToSpeech.SUCCESS || tts == null) {
                        if (continuation.isActive) continuation.resumeWithException(IllegalStateException("No speech engine on this phone"))
                        return@TextToSpeech
                    }
                    val hindi = Locale("hi", "IN")
                    tts.language = if (tts.isLanguageAvailable(hindi) >= TextToSpeech.LANG_AVAILABLE) hindi else Locale("en", "IN")
                    tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                        override fun onStart(id: String?) = Unit

                        override fun onDone(id: String?) {
                            tts.shutdown()
                            if (continuation.isActive) continuation.resume(Unit)
                        }

                        @Deprecated("Deprecated in Java")
                        override fun onError(id: String?) {
                            tts.shutdown()
                            if (continuation.isActive) continuation.resumeWithException(IllegalStateException("The phone voice failed"))
                        }
                    })
                    tts.synthesizeToFile(text, null, file, "memy")
                }
                continuation.invokeOnCancellation { engine?.shutdown() }
            }
        }
    }

    private fun writeWav(file: File, samples: FloatArray, rate: Int) {
        val peak = samples.maxOf { abs(it) }.coerceAtLeast(0.01f)
        val gain = 0.97f / peak
        val data = ByteBuffer.allocate(44 + samples.size * 2).order(ByteOrder.LITTLE_ENDIAN)
        data.put("RIFF".toByteArray()).putInt(36 + samples.size * 2).put("WAVE".toByteArray())
        data.put("fmt ".toByteArray()).putInt(16).putShort(1).putShort(1)
        data.putInt(rate).putInt(rate * 2).putShort(2).putShort(16)
        data.put("data".toByteArray()).putInt(samples.size * 2)
        for (sample in samples) data.putShort((sample * gain * 32767f).toInt().coerceIn(-32768, 32767).toShort())
        file.writeBytes(data.array())
    }

    private fun hash(value: String) = MessageDigest.getInstance("SHA-1")
        .digest(value.toByteArray())
        .take(8)
        .joinToString("") { "%02x".format(it) }
}
