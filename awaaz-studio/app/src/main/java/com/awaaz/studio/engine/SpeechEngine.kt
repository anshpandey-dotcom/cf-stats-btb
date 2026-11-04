package com.awaaz.studio.engine

import com.awaaz.studio.audio.WavWriter
import com.k2fsa.sherpa.onnx.OfflineTts
import com.k2fsa.sherpa.onnx.OfflineTtsConfig
import com.k2fsa.sherpa.onnx.OfflineTtsKokoroModelConfig
import com.k2fsa.sherpa.onnx.OfflineTtsModelConfig
import com.k2fsa.sherpa.onnx.OfflineTtsVitsModelConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.coroutines.coroutineContext

sealed interface Piece {
    data class Say(val text: String) : Piece
    data class Pause(val seconds: Float) : Piece
}

class SpeechEngine(private val packs: Packs) {
    private var cached: Pair<String, OfflineTts>? = null
    private val threads = Runtime.getRuntime().availableProcessors().coerceIn(2, 4)

    private fun engine(voice: Voice): OfflineTts {
        val key = "${voice.pack.id}:${voice.accent}"
        cached?.let { if (it.first == key) return it.second }
        cached?.second?.release()
        cached = null
        val dir = packs.dir(voice.pack).absolutePath
        val espeak = packs.espeakDir().absolutePath
        val model = when (voice.kind) {
            EngineKind.Kokoro -> OfflineTtsModelConfig(
                kokoro = OfflineTtsKokoroModelConfig(
                    model = "$dir/model.int8.onnx",
                    voices = "$dir/voices.bin",
                    tokens = "$dir/tokens.txt",
                    dataDir = espeak,
                    lexicon = if (voice.accent == "en-gb") "$dir/lexicon-gb-en.txt" else "$dir/lexicon-us-en.txt",
                    lang = voice.accent
                ),
                numThreads = threads
            )
            EngineKind.Piper -> OfflineTtsModelConfig(
                vits = OfflineTtsVitsModelConfig(
                    model = "$dir/${voice.model}",
                    tokens = "$dir/tokens.txt",
                    dataDir = espeak,
                    noiseScale = 0.6f,
                    noiseScaleW = 0.75f
                ),
                numThreads = threads
            )
        }
        val tts = OfflineTts(config = OfflineTtsConfig(model = model, maxNumSentences = 1))
        cached = key to tts
        return tts
    }

    suspend fun speak(
        text: String,
        voice: Voice,
        style: Style,
        speed: Float,
        lang: Lang,
        dictionary: Map<String, String>,
        out: File,
        onProgress: (Float) -> Unit
    ) = withContext(Dispatchers.Default) {
        val tts = engine(voice)
        val pieces = Script.parse(Script.apply(text, dictionary), lang)
        val total = pieces.count { it is Piece.Say }.coerceAtLeast(1)
        var done = 0
        val rate = tts.sampleRate()
        val pace = speed * style.speed * if (lang == Lang.Sanskrit) 0.9f else 1f
        WavWriter(out, rate).use { w ->
            w.silence(0.15f)
            for (p in pieces) {
                coroutineContext.ensureActive()
                when (p) {
                    is Piece.Pause -> w.silence(p.seconds)
                    is Piece.Say -> {
                        val audio = tts.generate(p.text, voice.sid, pace)
                        w.write(audio.samples)
                        w.silence(style.pause * if (p.text.endsWith(",")) 0.5f else 1f)
                        done++
                        onProgress(done.toFloat() / total)
                    }
                }
            }
            w.silence(0.2f)
        }
        rate
    }

    fun release() {
        cached?.second?.release()
        cached = null
    }
}

object Script {
    private val pauseTag = Regex("""\[\s*pause\s*([0-9.]+)\s*(ms|s)?\s*]""", RegexOption.IGNORE_CASE)
    private val enders = Regex("""(?<=[।॥?!.;])\s+|\n+""")

    fun apply(text: String, dictionary: Map<String, String>): String {
        var t = text
        for ((k, v) in dictionary) if (k.isNotBlank()) t = Regex("(?i)(?<![\\p{L}\\p{M}])${Regex.escape(k)}(?![\\p{L}\\p{M}])").replace(t, Regex.escapeReplacement(v))
        return t
    }

    fun parse(text: String, lang: Lang): List<Piece> {
        val out = ArrayList<Piece>()
        var last = 0
        for (m in pauseTag.findAll(text)) {
            sentences(text.substring(last, m.range.first), lang).forEach { out += Piece.Say(it) }
            val v = m.groupValues[1].toFloatOrNull() ?: 1f
            out += Piece.Pause((if (m.groupValues[2].lowercase() == "ms") v / 1000f else v).coerceIn(0f, 10f))
            last = m.range.last + 1
        }
        sentences(text.substring(last), lang).forEach { out += Piece.Say(it) }
        return out
    }

    private fun sentences(block: String, lang: Lang): List<String> {
        val out = ArrayList<String>()
        for (raw in block.split(enders)) {
            var s = raw.trim().replace(Regex("\\s+"), " ")
            if (s.isEmpty()) continue
            if (lang == Lang.Sanskrit) s = s.replace("॥", "।")
            while (s.length > 260) {
                val cut = s.lastIndexOfAny(charArrayOf(',', '،', ' '), 240).takeIf { it > 80 } ?: 240
                out += s.substring(0, cut + 1).trim()
                s = s.substring(cut + 1).trim()
            }
            if (s.isNotEmpty()) out += s
        }
        return out
    }
}
