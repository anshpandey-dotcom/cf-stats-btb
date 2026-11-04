package com.ansh.recalm.tts

import java.text.Normalizer

object HindiG2p {

    private const val VIRAMA = '्'
    private const val NUKTA = '़'
    private const val ANUSVARA = 'ं'
    private const val CHANDRABINDU = 'ँ'
    private const val VISARGA = 'ः'
    private const val TILDE = "̃"

    private val consonants = mapOf(
        'क' to "k", 'ख' to "kʰ", 'ग' to "ɡ", 'घ' to "ɡʰ", 'ङ' to "ŋ",
        'च' to "c", 'छ' to "cʰ", 'ज' to "ɟ", 'झ' to "ɟʰ", 'ञ' to "ɲ",
        'ट' to "ʈ", 'ठ' to "ʈʰ", 'ड' to "ɖ", 'ढ' to "ɖʰ", 'ण' to "ɳ",
        'त' to "t", 'थ' to "tʰ", 'द' to "d", 'ध' to "dʰ", 'न' to "n",
        'प' to "p", 'फ' to "pʰ", 'ब' to "b", 'भ' to "bʰ", 'म' to "m",
        'य' to "j", 'र' to "ɾ", 'ल' to "l", 'ळ' to "l", 'व' to "ʋ",
        'श' to "ʃ", 'ष' to "ʂ", 'स' to "s", 'ह' to "h",
        '\u0958' to "q", '\u0959' to "x", '\u095A' to "ɣ", '\u095B' to "z", '\u095E' to "f",
        '\u095C' to "ɽ", '\u095D' to "ɽh", '\u095F' to "j"
    )

    private val nuktaForms = mapOf(
        'क' to "q", 'ख' to "x", 'ग' to "ɣ", 'ज' to "z", 'फ' to "f", 'ड' to "ɽ", 'ढ' to "ɽh"
    )

    private val independentVowels = mapOf(
        'अ' to "ə", 'आ' to "aː", 'इ' to "ɪ", 'ई' to "i", 'उ' to "ʊ", 'ऊ' to "u",
        'ऋ' to "ɾɪ", 'ए' to "eː", 'ऐ' to "ɛː", 'ओ' to "oː", 'औ' to "ɔː", 'ऑ' to "ɔ", 'ऍ' to "ɛ"
    )

    private val matras = mapOf(
        'ा' to "aː", 'ि' to "ɪ", 'ी' to "iː", 'ु' to "ʊ", 'ू' to "uː", 'ृ' to "ɾɪ",
        'े' to "eː", 'ै' to "ɛː", 'ो' to "oː", 'ौ' to "ɔː", 'ॉ' to "ɔ", 'ॅ' to "ɛ"
    )

    private val nasalized = mapOf(
        "ə" to "ə$TILDE", "aː" to "a$TILDE", "ɪ" to "i$TILDE", "iː" to "i$TILDE",
        "ʊ" to "u$TILDE", "uː" to "u$TILDE", "eː" to "e${TILDE}ː", "ɛː" to "ɛ$TILDE",
        "oː" to "o$TILDE", "ɔː" to "ɔ$TILDE", "ɔ" to "ɔ$TILDE", "ɛ" to "ɛ$TILDE"
    )

    private val unstressedWords = setOf(
        "अगर", "आप", "आपका", "आपकी", "आपके", "आपको", "इनका", "इन्हें", "इसका", "इस्से", "उनका",
        "उनकी", "उनके", "उन्हे", "उसका", "उसकी", "उसके", "उस्से", "और", "का", "की", "के", "को",
        "क्या", "तक", "तुझे", "तुम", "तुम्हारा", "तुम्हारी", "तुम्हारे", "तुम्हें", "तू", "तेरा", "था",
        "थी", "थीं", "थे", "बिना", "मुझे", "में", "मेरा", "मेरी", "मेरे", "मैं", "य", "यदि", "यह", "ये",
        "रहा", "रही", "रहे", "वह", "वे", "वो", "से", "हम", "हमारा", "हमारी", "हमारे", "हमे", "हूँ",
        "है", "हैं", "हो"
    )

    private class Unit(var consonant: String?, var vowel: String?, var nasal: Char? = null) {
        val isConsonant get() = consonant != null
    }

    private class Segment(val text: String, val vowel: Boolean)

    private val cache = object : LinkedHashMap<String, String>(4096, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, String>?) = size > 20000
    }

    fun isDevanagari(c: Char) = c in 'ऀ'..'ॿ'

    fun phonemize(word: String): String {
        synchronized(cache) { cache[word]?.let { return it } }
        val result = Normalizer.normalize(stress(segments(word), word), Normalizer.Form.NFD)
        synchronized(cache) { cache[word] = result }
        return result
    }

    private fun parse(raw: String): MutableList<Unit> {
        val word = raw.replace("क्ष", "क्श").replace("ज्ञ", "ग्य")
        val units = mutableListOf<Unit>()
        var i = 0
        while (i < word.length) {
            val ch = word[i]
            val base = consonants[ch]
            when {
                base != null -> {
                    var phone = base
                    i++
                    if (i < word.length && word[i] == NUKTA) {
                        phone = nuktaForms[ch] ?: phone
                        i++
                    }
                    val unit = Unit(phone, "ə")
                    if (i < word.length && word[i] == VIRAMA) {
                        unit.vowel = null
                        i++
                    } else if (i < word.length && matras.containsKey(word[i])) {
                        unit.vowel = matras[word[i]]
                        i++
                    }
                    units += unit
                }
                independentVowels.containsKey(ch) -> {
                    units += Unit(null, independentVowels[ch])
                    i++
                }
                ch == ANUSVARA || ch == CHANDRABINDU -> {
                    val last = units.lastOrNull()
                    if (last?.vowel != null) last.nasal = ch
                    i++
                }
                ch == VISARGA -> {
                    units += Unit("h", null)
                    i++
                }
                else -> i++
            }
        }
        return units
    }

    private fun deleteSchwa(units: MutableList<Unit>) {
        if (units.isEmpty()) return
        val last = units.last()
        if (last.isConsonant && last.vowel == "ə" && last.nasal == null && units.size > 1) {
            val prev = units[units.size - 2]
            val cluster = prev.isConsonant && prev.vowel == null
            val keep = cluster && (last.consonant in setOf("j", "ɾ", "ɳ") ||
                (last.consonant in setOf("ʋ", "n", "m") && prev.consonant != "ɾ"))
            if (!keep) last.vowel = null
        }
        for (k in units.size - 2 downTo 1) {
            val unit = units[k]
            if (!unit.isConsonant || unit.vowel != "ə" || unit.nasal != null) continue
            val next = units[k + 1]
            if (!next.isConsonant || next.vowel == null || next.vowel == "ə") continue
            if (units[k - 1].vowel == null) continue
            if (unit.consonant == "h" || unit.consonant == "ɽh") continue
            unit.vowel = null
        }
    }

    private fun homorganic(phone: String): String? = when (phone.first()) {
        'k', 'ɡ' -> "ŋ"
        'ʈ', 'ɖ' -> "ɳ"
        'p', 'b' -> "m"
        't', 'd', 'c', 'ɟ' -> "n"
        else -> null
    }

    private val noGeminate = setOf("m", "n", "s", "ʃ", "l", "ɾ", "ɖ", "ʈ")

    private fun segments(word: String): List<Segment> {
        val units = parse(word)
        deleteSchwa(units)
        val out = mutableListOf<Segment>()
        for (k in units.indices) {
            val unit = units[k]
            val next = units.getOrNull(k + 1)
            val consonant = unit.consonant
            if (consonant != null && unit.vowel == null && next?.consonant != null && consonant != "h") {
                val nextConsonant = next.consonant!!
                val sameStop = nextConsonant.first() == consonant.first() &&
                    consonant.length <= nextConsonant.length &&
                    consonant !in noGeminate && !consonant.endsWith("ʰ") &&
                    !(nextConsonant.endsWith("ʰ") && consonant == "c")
                if (nextConsonant == "j" && (consonant == "t" || consonant == "k")) {
                    out += Segment(consonant + "ː", false)
                    continue
                }
                if (sameStop) {
                    next.consonant = nextConsonant + "ː"
                    continue
                }
            }
            if (consonant != null) out += Segment(consonant, false)
            var vowel = unit.vowel ?: continue
            val nasal = unit.nasal
            if (nasal != null) {
                val nextIsConsonant = next?.consonant != null
                val nasalStop = if (nextIsConsonant) homorganic(next!!.consonant!!) else null
                when {
                    nasal == ANUSVARA && vowel == "ə" && nextIsConsonant -> {
                        val n = if (next!!.consonant!!.first() in "cɟ") "ɲ" else nasalStop ?: "n"
                        out += Segment("ə", true)
                        out += Segment(n, false)
                    }
                    nasal == CHANDRABINDU && nextIsConsonant && nasalStop != null -> {
                        out += Segment(nasalized[vowel] ?: vowel, true)
                        out += Segment("n", false)
                    }
                    else -> out += Segment(nasalized[vowel] ?: vowel, true)
                }
            } else {
                if (k == units.size - 1 && vowel == "iː") vowel = "i"
                out += Segment(vowel, true)
            }
        }
        if (out.size >= 3 && out[out.size - 1].text == "j") {
            val v = out[out.size - 2]
            if (v.text == "ə") out[out.size - 2] = Segment("ɛ", true)
            if (v.text == "iː") out[out.size - 2] = Segment("i", true)
        }
        return out
    }

    private fun stress(segs: List<Segment>, word: String): String {
        val vowelIdx = segs.indices.filter { segs[it].vowel }
        if (vowelIdx.isEmpty() || word in unstressedWords) return segs.joinToString("") { it.text }
        val weights = vowelIdx.mapIndexed { n, idx ->
            val nucleus = segs[idx].text
            val long = nucleus.contains('ː') || nucleus.first() in "ɛɔ" ||
                (nucleus.contains(TILDE) && nucleus.first() != 'ə')
            val nextVowel = vowelIdx.getOrNull(n + 1) ?: segs.size
            val coda = nextVowel - idx - 1
            val final = n == vowelIdx.size - 1
            val closed = if (final) coda >= 1 else coda >= 2
            (if (long) 2 else 1) + (if (closed) 1 else 0)
        }
        val count = weights.size
        val best = if (count == 1) 0 else {
            val maxNonFinal = weights.dropLast(1).max()
            val endsWithEj = segs.last().text == "j" && segs[segs.size - 2].text == "ɛ"
            when {
                weights.last() == 3 && weights.last() > maxNonFinal && !endsWithEj -> count - 1
                maxNonFinal == 1 -> 0
                else -> (0 until count - 1).last { weights[it] == maxNonFinal }
            }
        }
        val target = vowelIdx[best]
        val sb = StringBuilder()
        segs.forEachIndexed { i, s ->
            if (i == target) {
                sb.append('ˈ')
                sb.append(if (s.text.startsWith("ə")) "ʌ" + s.text.substring(1) else s.text)
            } else sb.append(s.text)
        }
        return sb.toString()
    }
}
