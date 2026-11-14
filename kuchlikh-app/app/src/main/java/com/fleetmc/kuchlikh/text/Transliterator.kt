package com.fleetmc.kuchlikh.text

import android.content.res.AssetManager

object Transliterator {

    @Volatile
    private var lexicon: Map<String, String> = emptyMap()

    fun load(assets: AssetManager) {
        if (lexicon.isNotEmpty()) return
        val map = HashMap<String, String>(60000)
        runCatching {
            assets.open("hinglish.tsv").bufferedReader().useLines { lines ->
                lines.forEach { line ->
                    val tab = line.indexOf('\t')
                    if (tab > 0) map[line.substring(0, tab)] = line.substring(tab + 1)
                }
            }
        }
        lexicon = map
    }

    fun setLexicon(map: Map<String, String>) {
        lexicon = map
    }

    fun word(roman: String): String {
        if (roman.isEmpty()) return roman
        if (roman.length > 1 && roman.all { it.isUpperCase() }) return roman
        val key = roman.lowercase()
        HinglishWords.common[key]?.let { return it }
        lexicon[key]?.let { return it }
        verb(key)?.let { return it }
        return rules(key)
    }

    private val latinWord = Regex("[A-Za-z]+")
    private val stop = Regex("(?<=[\\u0900-\\u097F])\\.(?![.\\d])")
    private val bar = Regex("(?<=[\\u0900-\\u097F])\\s*\\|")

    fun text(input: String): String {
        val converted = latinWord.replace(input) { word(it.value) }
        return bar.replace(stop.replace(converted, "।"), "।")
    }

    fun isDevanagari(c: Char): Boolean = c in 'ऀ'..'ॿ'

    private fun endsOpen(deva: String): Boolean {
        val last = deva.last()
        return last in "ािीुूेैोौ" || last in "आइईउऊएऐओऔ"
    }

    private val closedEndings = listOf(
        "enge" to "ेंगे", "engi" to "ेंगी", "unga" to "ूँगा", "ungi" to "ूँगी", "oonga" to "ूँगा", "oongi" to "ूँगी",
        "ega" to "ेगा", "egi" to "ेगी", "oge" to "ोगे", "ogi" to "ोगी", "iye" to "िए", "ie" to "िए",
        "kar" to "कर", "ta" to "ता", "te" to "ते", "ti" to "ती", "na" to "ना", "ne" to "ने", "ni" to "नी",
        "ke" to "के", "a" to "ा", "e" to "े", "i" to "ी", "o" to "ो", "en" to "ें", "un" to "ूँ", "oon" to "ूँ",
    )

    private val openEndings = listOf(
        "enge" to "एँगे", "engi" to "एँगी", "unga" to "ऊँगा", "ungi" to "ऊँगी", "oonga" to "ऊँगा", "oongi" to "ऊँगी",
        "ega" to "एगा", "egi" to "एगी", "oge" to "ओगे", "ogi" to "ओगी", "iye" to "इए", "ie" to "इए",
        "kar" to "कर", "ta" to "ता", "te" to "ते", "ti" to "ती", "na" to "ना", "ne" to "ने", "ni" to "नी",
        "ke" to "के", "ya" to "या", "ye" to "ए", "yi" to "ई", "e" to "ए", "o" to "ओ", "en" to "एँ", "un" to "ऊँ", "oon" to "ऊँ",
    )

    private fun verb(key: String): String? {
        for (cut in key.length - 1 downTo 1) {
            val root = HinglishWords.roots[key.substring(0, cut)] ?: continue
            val ending = key.substring(cut)
            val table = if (endsOpen(root)) openEndings else closedEndings
            val suffix = table.firstOrNull { it.first == ending } ?: continue
            return root + suffix.second
        }
        return null
    }

    private class Tok(val roman: String, val deva: String, val matra: String, val vowel: Boolean)

    private val consonants = listOf(
        "ksh" to "क्ष", "chh" to "छ", "cch" to "च्छ", "shr" to "श्र", "kh" to "ख", "gh" to "घ", "ch" to "च", "jh" to "झ",
        "th" to "थ", "dh" to "ध", "ph" to "फ", "bh" to "भ", "sh" to "श", "jn" to "ज्ञ", "ck" to "क",
        "k" to "क", "g" to "ग", "c" to "क", "j" to "ज", "t" to "त", "d" to "द", "n" to "न", "p" to "प",
        "b" to "ब", "m" to "म", "y" to "य", "r" to "र", "l" to "ल", "v" to "व", "w" to "व", "s" to "स",
        "h" to "ह", "f" to "फ़", "z" to "ज़", "q" to "क़", "x" to "क्स",
    )

    private val vowels = listOf(
        Tok("aa", "आ", "ा", true), Tok("ai", "ऐ", "ै", true), Tok("au", "औ", "ौ", true), Tok("ou", "औ", "ौ", true),
        Tok("ee", "ई", "ी", true), Tok("ii", "ई", "ी", true), Tok("oo", "ऊ", "ू", true), Tok("uu", "ऊ", "ू", true),
        Tok("ei", "ए", "े", true), Tok("a", "अ", "", true), Tok("i", "इ", "ि", true), Tok("u", "उ", "ु", true),
        Tok("e", "ए", "े", true), Tok("o", "ओ", "ो", true),
    )

    private val longA = Tok("aa", "आ", "ा", true)
    private val longI = Tok("ee", "ई", "ी", true)
    private val longU = Tok("oo", "ऊ", "ू", true)

    private val nasalBefore = setOf("k", "kh", "g", "gh", "ch", "chh", "j", "jh", "t", "th", "d", "dh", "s", "sh", "z")
    private val labials = setOf("p", "ph", "b", "bh")
    private val aspirates = mapOf("k" to "kh", "g" to "gh", "ch" to "chh", "j" to "jh", "t" to "th", "d" to "dh", "p" to "ph", "b" to "bh")
    private val afterSh = setOf("t", "th", "k", "v", "w", "m", "n", "ch", "p")
    private val beforeR = setOf("t", "d", "sh", "th", "dh")
    private val afterInitialS = setOf("k", "p", "n", "m", "w", "v", "ph")

    private fun tokenize(key: String): List<Tok>? {
        val out = ArrayList<Tok>()
        var i = 0
        outer@ while (i < key.length) {
            for (v in vowels) {
                if (key.startsWith(v.roman, i)) {
                    out += v
                    i += v.roman.length
                    continue@outer
                }
            }
            for ((roman, deva) in consonants) {
                if (key.startsWith(roman, i)) {
                    out += Tok(roman, deva, "", false)
                    i += roman.length
                    continue@outer
                }
            }
            return null
        }
        return out
    }

    private fun joins(tokens: List<Tok>, index: Int): Boolean {
        val a = tokens[index - 1].roman
        val b = tokens[index].roman
        val atStart = index == 1
        val last = index == tokens.lastIndex
        val beforeFinalVowel = index == tokens.size - 2 && tokens.last().vowel
        if (a == b && a != "y" && a != "h") return true
        if (aspirates[a] == b) return true
        if (b == "y") return a != "y"
        if (b == "r") return atStart || a in beforeR
        if (a == "s") return b == "t" || b == "th" || (atStart && b in afterInitialS)
        if (a == "sh") return b in afterSh
        if ((a == "n" || a == "m") && b == "h") return true
        if (a == "r") return last || (beforeFinalVowel && b != "n")
        if (a == "l" && b == "k" && beforeFinalVowel) return true
        return false
    }

    private fun rules(key: String): String {
        val raw = tokenize(key) ?: return key
        if (raw.isEmpty()) return key
        val tokens = ArrayList(raw)
        if (tokens.size > 1) {
            val last = tokens.last()
            val sanskrit = key.length >= 5 && key.endsWith("tra")
            when (last.roman) {
                "a" -> if (!sanskrit) tokens[tokens.lastIndex] = longA
                "i" -> tokens[tokens.lastIndex] = longI
                "u" -> tokens[tokens.lastIndex] = longU
            }
        }
        for (i in 0 until tokens.size - 1) {
            val next = tokens[i + 1].roman
            if (tokens[i].roman == "a" && tokens[i + 1].vowel && (next == "o" || next == "e")) tokens[i] = longA
        }

        val out = StringBuilder()
        var i = 0
        while (i < tokens.size) {
            val tok = tokens[i]
            val prev = if (i > 0) tokens[i - 1] else null
            if (tok.vowel) {
                val glide = prev != null && prev.vowel && (prev.roman == "i" || prev.roman == "ee") &&
                    (tok.roman == "a" || tok.roman == "aa" || tok.roman == "o" || tok.roman == "u" || tok.roman == "oo")
                when {
                    glide -> out.append("य").append(tok.matra)
                    prev != null && !prev.vowel -> out.append(tok.matra)
                    else -> out.append(tok.deva)
                }
                i++
                continue
            }
            val next = if (i + 1 < tokens.size) tokens[i + 1] else null
            val afterVowel = prev != null && prev.vowel
            if (afterVowel && next != null && !next.vowel) {
                val nasal = (tok.roman == "n" && next.roman in nasalBefore) || (tok.roman == "m" && next.roman in labials)
                if (nasal) {
                    out.append("ं")
                    i++
                    out.append(next.deva)
                    i++
                    continue
                }
            }
            if (afterVowel && tok.roman == "n" && next == null && i >= 2) {
                val vowel = prev!!.roman
                if (vowel == "o" && tokens.size >= 4) {
                    out.append("ं")
                    i++
                    continue
                }
                if ((vowel == "e" || vowel == "ei") && tokens.size >= 4) {
                    out.append("ं")
                    i++
                    continue
                }
            }
            if (prev != null && !prev.vowel && joins(tokens, i)) out.append("्")
            out.append(tok.deva)
            i++
        }
        return out.toString()
    }
}
