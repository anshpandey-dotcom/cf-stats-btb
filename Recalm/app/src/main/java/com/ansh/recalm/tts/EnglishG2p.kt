package com.ansh.recalm.tts

import java.io.File

class EnglishG2p(lexiconFile: File) {

    private val lexicon = HashMap<String, String>(200_000)

    init {
        lexiconFile.forEachLine { line ->
            val space = line.indexOf(' ')
            if (space > 0) {
                val word = line.substring(0, space)
                if (!lexicon.containsKey(word)) lexicon[word] = line.substring(space + 1).replace(" ", "")
            }
        }
    }

    private val letterNames = mapOf(
        'a' to "ˈA", 'b' to "bˈi", 'c' to "sˈi", 'd' to "dˈi", 'e' to "ˈi", 'f' to "ˈɛf", 'g' to "ʤˈi",
        'h' to "ˈAʧ", 'i' to "ˈI", 'j' to "ʤˈA", 'k' to "kˈA", 'l' to "ˈɛl", 'm' to "ˈɛm", 'n' to "ˈɛn",
        'o' to "ˈO", 'p' to "pˈi", 'q' to "kjˈu", 'r' to "ˈɑɹ", 's' to "ˈɛs", 't' to "tˈi", 'u' to "jˈu",
        'v' to "vˈi", 'w' to "dˈʌbᵊlju", 'x' to "ˈɛks", 'y' to "wˈI", 'z' to "zˈi"
    )

    private val graphemes = listOf(
        "tion" to "ʃən", "sion" to "ʒən", "ture" to "ʧəɹ", "ough" to "O", "augh" to "ɔ",
        "igh" to "I", "tch" to "ʧ", "dge" to "ʤ", "sch" to "sk", "ear" to "ɪɹ", "air" to "ɛɹ",
        "sh" to "ʃ", "ch" to "ʧ", "th" to "θ", "ph" to "f", "wh" to "w", "ck" to "k", "ng" to "ŋ",
        "qu" to "kw", "kn" to "n", "wr" to "ɹ", "ee" to "i", "ea" to "i", "oo" to "u", "ou" to "W",
        "ow" to "O", "oi" to "Y", "oy" to "Y", "ai" to "A", "ay" to "A", "au" to "ɔ", "aw" to "ɔ",
        "ie" to "i", "ey" to "i", "ar" to "ɑɹ", "or" to "ɔɹ", "er" to "əɹ", "ir" to "ɜɹ", "ur" to "ɜɹ",
        "a" to "æ", "e" to "ɛ", "i" to "ɪ", "o" to "ɑ", "u" to "ʌ", "y" to "i",
        "b" to "b", "c" to "k", "d" to "d", "f" to "f", "g" to "ɡ", "h" to "h", "j" to "ʤ", "k" to "k",
        "l" to "l", "m" to "m", "n" to "n", "p" to "p", "q" to "k", "r" to "ɹ", "s" to "s", "t" to "t",
        "v" to "v", "w" to "w", "x" to "ks", "z" to "z"
    )

    fun phonemize(raw: String): String {
        val word = raw.lowercase().replace('’', '\'')
        lexicon[word]?.let { return it }
        if (raw.length in 2..5 && raw.all { it.isUpperCase() }) return spell(word)
        stripped(word)?.let { return it }
        split(word)?.let { return it }
        return rules(word)
    }

    fun spell(word: String) = word.filter { it.isLetter() }.map { letterNames[it] ?: "" }.joinToString("")

    private fun stripped(word: String): String? {
        if (word.endsWith("'s")) lexicon[word.dropLast(2)]?.let { return it + "z" }
        if (word.endsWith("s")) lexicon[word.dropLast(1)]?.let { return it + "z" }
        if (word.endsWith("es")) lexicon[word.dropLast(2)]?.let { return it + "ᵻz" }
        if (word.endsWith("ed")) lexicon[word.dropLast(2)]?.let { return it + "d" }
        if (word.endsWith("ing")) lexicon[word.dropLast(3)]?.let { return it + "ɪŋ" }
        if (word.endsWith("ly")) lexicon[word.dropLast(2)]?.let { return it + "li" }
        return null
    }

    private fun split(word: String): String? {
        if (word.length < 6) return null
        for (cut in word.length - 3 downTo 3) {
            val left = lexicon[word.substring(0, cut)] ?: continue
            val right = lexicon[word.substring(cut)] ?: continue
            return left + right.replace("ˈ", "ˌ")
        }
        return null
    }

    private fun rules(word: String): String {
        val letters = word.filter { it in 'a'..'z' }
        if (letters.isEmpty()) return ""
        val body = if (letters.length > 3 && letters.endsWith("e") && letters[letters.length - 2] !in "aeiou") letters.dropLast(1) else letters
        val sb = StringBuilder()
        var i = 0
        var stressed = false
        while (i < body.length) {
            val match = graphemes.firstOrNull { body.startsWith(it.first, i) }
            if (match == null) {
                i++
                continue
            }
            val phone = match.second
            if (!stressed && phone.first() in "æɛɪɑʌiuAIOWYɔəɜ") {
                sb.append('ˈ')
                stressed = true
            }
            sb.append(phone)
            i += match.first.length
        }
        return sb.toString()
    }
}
