package com.ansh.recalm.tts

class WordSpan(val start: Int, val end: Int, val tokenStart: Int, val tokenEnd: Int)

class PhonemeChunk(val tokens: Array<String>, val words: List<WordSpan>)

class Phonemizer(
    private val english: EnglishG2p,
    private val vocabulary: Set<String>,
    private val englishStyle: (String) -> String = { it }
) {

    private val pattern = Regex(
        "[\\u0900-\\u0963\\u0970-\\u097F]+|[A-Za-z]+(?:['’][A-Za-z]+)*|[0-9\\u0966-\\u096F]+(?:[.,][0-9\\u0966-\\u096F]+)*|[.,!?;:—…\\u0964\\u0965-]"
    )

    private val punctuation = mapOf(
        "." to ".", "," to ",", "!" to "!", "?" to "?", ";" to ";", ":" to ":", "—" to "—",
        "…" to "…", "।" to ".", "॥" to ".", "-" to ","
    )

    fun phonemize(text: String, maxTokens: Int = 380): List<PhonemeChunk> {
        val devanagari = text.count { HindiG2p.isDevanagari(it) }
        val latin = text.count { it in 'A'..'Z' || it in 'a'..'z' }
        val hindiNumbers = devanagari >= latin
        val chunks = mutableListOf<PhonemeChunk>()
        var tokens = mutableListOf<String>()
        var words = mutableListOf<WordSpan>()
        fun flush() {
            while (tokens.isNotEmpty() && tokens.last() == " ") tokens.removeAt(tokens.size - 1)
            if (words.isNotEmpty()) chunks += PhonemeChunk(tokens.toTypedArray(), words)
            tokens = mutableListOf()
            words = mutableListOf()
        }
        for (match in pattern.findAll(text)) {
            val value = match.value
            val mark = punctuation[value]
            if (mark != null) {
                if (mark == "," && value == "-") continue
                while (tokens.isNotEmpty() && tokens.last() == " ") tokens.removeAt(tokens.size - 1)
                if (tokens.isNotEmpty() && mark in vocabulary) {
                    tokens += mark
                    tokens += " "
                }
                if (tokens.size > maxTokens * 0.6 && mark != "—") flush()
                continue
            }
            val phones = phonesFor(value, hindiNumbers)
            if (phones.isEmpty()) continue
            val symbols = phones.map { it.toString() }.filter { it in vocabulary }
            if (symbols.isEmpty()) continue
            if (tokens.size + symbols.size + 1 > maxTokens) flush()
            val begin = tokens.size
            tokens.addAll(symbols)
            words += WordSpan(match.range.first, match.range.last + 1, begin, tokens.size)
            tokens += " "
        }
        flush()
        return chunks
    }

    private fun phonesFor(value: String, hindiNumbers: Boolean): String {
        val first = value.first()
        return when {
            first.isDigit() || first in '०'..'९' -> {
                val spoken = NumberWords.spell(NumberWords.digitsHindi(value), hindiNumbers)
                spoken.split(' ').filter { it.isNotBlank() }.joinToString(" ") { w ->
                    if (HindiG2p.isDevanagari(w.first())) HindiG2p.phonemize(w) else englishStyle(english.phonemize(w))
                }
            }
            HindiG2p.isDevanagari(first) -> HindiG2p.phonemize(value)
            else -> englishStyle(english.phonemize(value))
        }
    }
}
