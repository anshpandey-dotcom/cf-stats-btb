package com.awaaz.studio.engine

import com.awaaz.studio.audio.Audio
import com.awaaz.studio.audio.Pcm
import com.k2fsa.sherpa.onnx.OfflineModelConfig
import com.k2fsa.sherpa.onnx.OfflineRecognizer
import com.k2fsa.sherpa.onnx.OfflineRecognizerConfig
import com.k2fsa.sherpa.onnx.OfflineWhisperModelConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlin.coroutines.coroutineContext

class Transcriber(private val packs: Packs) {
    private var cached: Triple<String, String, OfflineRecognizer>? = null

    private fun recognizer(pack: Pack, language: String): OfflineRecognizer {
        cached?.let { if (it.first == pack.id && it.second == language) return it.third }
        cached?.third?.release()
        val dir = packs.dir(pack).absolutePath
        val prefix = pack.files.first().substringBefore('-')
        val config = OfflineRecognizerConfig(
            modelConfig = OfflineModelConfig(
                whisper = OfflineWhisperModelConfig(
                    encoder = "$dir/$prefix-encoder.int8.onnx",
                    decoder = "$dir/$prefix-decoder.int8.onnx",
                    language = language,
                    task = "transcribe"
                ),
                tokens = "$dir/$prefix-tokens.txt",
                numThreads = Runtime.getRuntime().availableProcessors().coerceIn(2, 4)
            )
        )
        return OfflineRecognizer(config = config).also { cached = Triple(pack.id, language, it) }
    }

    suspend fun transcribe(input: Pcm, pack: Pack, language: String, onProgress: (Float) -> Unit): String =
        withContext(Dispatchers.Default) {
            val pcm = Audio.resample(input, 16000)
            val rec = recognizer(pack, language)
            val chunks = split(pcm.samples, 16000)
            val sb = StringBuilder()
            chunks.forEachIndexed { i, c ->
                coroutineContext.ensureActive()
                val stream = rec.createStream()
                stream.acceptWaveform(c, 16000)
                rec.decode(stream)
                val text = rec.getResult(stream).text.trim()
                stream.release()
                if (text.isNotEmpty()) {
                    if (sb.isNotEmpty()) sb.append(' ')
                    sb.append(text)
                }
                onProgress((i + 1f) / chunks.size)
            }
            sb.toString()
        }

    private fun split(x: FloatArray, rate: Int): List<FloatArray> {
        val out = ArrayList<FloatArray>()
        val maxLen = 28 * rate
        val minLen = 18 * rate
        val win = rate / 5
        var start = 0
        while (x.size - start > maxLen) {
            var best = start + maxLen
            var bestE = Double.MAX_VALUE
            var p = start + minLen
            while (p + win < start + maxLen) {
                var e = 0.0
                for (i in p until p + win) e += x[i] * x[i]
                if (e < bestE) { bestE = e; best = p + win / 2 }
                p += win / 2
            }
            out += x.copyOfRange(start, best)
            start = best
        }
        if (x.size - start > rate / 4) out += x.copyOfRange(start, x.size)
        return out
    }

    fun release() {
        cached?.third?.release()
        cached = null
    }
}
