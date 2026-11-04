package com.ansh.recalm.tts

import com.k2fsa.sherpa.onnx.GenerationConfig
import com.k2fsa.sherpa.onnx.OfflineTts
import com.k2fsa.sherpa.onnx.OfflineTtsConfig
import com.k2fsa.sherpa.onnx.OfflineTtsKokoroModelConfig
import com.k2fsa.sherpa.onnx.OfflineTtsModelConfig
import com.k2fsa.sherpa.onnx.OfflineTtsVitsModelConfig
import java.io.File

class WordMark(val start: Int, val end: Int, val sample: Int)

class Utterance(val samples: FloatArray, val sampleRate: Int, val marks: List<WordMark>)

interface Synthesizer {
    val sampleRate: Int
    fun speak(text: String, speed: Float): Utterance
    fun release()
}

private fun threads() = Runtime.getRuntime().availableProcessors().coerceIn(2, 4)

private fun readVocabulary(tokens: File): Set<String> = tokens.readLines(Charsets.UTF_8).mapNotNull { line ->
    val cut = line.lastIndexOf(' ')
    if (cut < 0) null else line.substring(0, cut).ifEmpty { " " }
}.toSet()

private val kokoroToEspeak = listOf(
    "A" to "eɪ", "I" to "aɪ", "O" to "oʊ", "W" to "aʊ", "Y" to "ɔɪ", "Q" to "əʊ",
    "ʤ" to "dʒ", "ʧ" to "tʃ", "ᵊ" to "ə", "ᵻ" to "ɪ"
)

abstract class PhonemeSynthesizer(english: EnglishG2p, vocabulary: Set<String>, convertEnglish: Boolean) : Synthesizer {

    protected abstract val tts: OfflineTts

    private val phonemizer = Phonemizer(
        english,
        vocabulary,
        if (convertEnglish) { phones -> kokoroToEspeak.fold(phones) { acc, (from, to) -> acc.replace(from, to) } } else { phones -> phones }
    )

    override val sampleRate: Int get() = tts.sampleRate()

    protected abstract fun generate(tokens: Array<String>, speed: Float): FloatArray

    override fun speak(text: String, speed: Float): Utterance {
        val chunks = phonemizer.phonemize(text)
        val parts = mutableListOf<FloatArray>()
        val marks = mutableListOf<WordMark>()
        var offset = 0
        for (chunk in chunks) {
            val samples = generate(chunk.tokens, speed)
            val weights = chunk.tokens.map { t ->
                when (t) {
                    " " -> 0.5f
                    ".", "!", "?", "…" -> 5f
                    ",", ";", ":", "—" -> 3f
                    "ˈ", "ˌ", "ː", "̃" -> 0.2f
                    else -> 1f
                }
            }
            val total = weights.sum().coerceAtLeast(1f)
            val cumulative = FloatArray(weights.size + 1)
            for (i in weights.indices) cumulative[i + 1] = cumulative[i] + weights[i]
            for (w in chunk.words) {
                val at = (samples.size * (cumulative[w.tokenStart] / total)).toInt()
                marks += WordMark(w.start, w.end, offset + at)
            }
            parts += samples
            offset += samples.size
        }
        return Utterance(concat(parts, offset), sampleRate, marks)
    }

    override fun release() = tts.release()
}

class KokoroSynthesizer(dir: File, private val speakerId: Int, english: EnglishG2p) :
    PhonemeSynthesizer(english, readVocabulary(File(dir, "tokens.txt")), convertEnglish = false) {

    override val tts: OfflineTts = run {
        val model = dir.listFiles()!!.first { it.name.endsWith(".onnx") }
        val dict = File(dir, "dict")
        OfflineTts(
            null,
            OfflineTtsConfig(
                model = OfflineTtsModelConfig(
                    kokoro = OfflineTtsKokoroModelConfig(
                        model = model.absolutePath,
                        voices = File(dir, "voices.bin").absolutePath,
                        tokens = File(dir, "tokens.txt").absolutePath,
                        dataDir = File(dir, "espeak-ng-data").takeIf { it.exists() }?.absolutePath ?: "",
                        lexicon = File(dir, "lexicon-us-en.txt").takeIf { it.exists() }?.absolutePath ?: "",
                        dictDir = if (dict.exists()) dict.absolutePath else ""
                    ),
                    numThreads = threads(),
                    debug = false,
                    provider = "cpu"
                )
            )
        )
    }

    override fun generate(tokens: Array<String>, speed: Float): FloatArray =
        tts.generateWithConfig("", GenerationConfig(sid = speakerId, speed = speed, tokens = arrayOf(tokens))).samples
}

class PiperSynthesizer(dir: File, english: EnglishG2p) :
    PhonemeSynthesizer(english, readVocabulary(modelHome(dir).resolve("tokens.txt")), convertEnglish = true) {

    override val tts: OfflineTts = run {
        val home = modelHome(dir)
        val model = home.listFiles()!!.first { it.name.endsWith(".onnx") }
        val espeak = File(home, "espeak-ng-data")
        OfflineTts(
            null,
            OfflineTtsConfig(
                model = OfflineTtsModelConfig(
                    vits = OfflineTtsVitsModelConfig(
                        model = model.absolutePath,
                        tokens = File(home, "tokens.txt").absolutePath,
                        dataDir = if (espeak.exists()) espeak.absolutePath else ""
                    ),
                    numThreads = threads(),
                    debug = false,
                    provider = "cpu"
                )
            )
        )
    }

    override fun generate(tokens: Array<String>, speed: Float): FloatArray {
        val codepoints = tokens.joinToString("").codePoints().toArray()
        return tts.generateWithConfig("", GenerationConfig(sid = 0, speed = speed, phonemeCodepoints = arrayOf(codepoints))).samples
    }

    companion object {
        fun modelHome(dir: File): File = dir.walkTopDown().first { it.name.endsWith(".onnx") }.parentFile!!
    }
}

private fun concat(parts: List<FloatArray>, size: Int): FloatArray {
    if (parts.size == 1) return parts[0]
    val out = FloatArray(size)
    var at = 0
    for (p in parts) {
        p.copyInto(out, at)
        at += p.size
    }
    return out
}
