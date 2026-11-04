package com.ansh.recalm.data

import kotlinx.serialization.Serializable

object BlockKind {
    const val BODY = 0
    const val HEADING = 1
    const val QUESTION = 2
    const val EXERCISE = 3
    const val CAPTION = 4
    const val ACTIVITY = 5
    const val BOILERPLATE = 6
    const val IMAGE_TEXT = 7
}

@Serializable
data class Block(val text: String, val kind: Int = BlockKind.BODY)

@Serializable
data class Page(val blocks: List<Block>, val source: Int = -1, val ocr: Boolean = false)

@Serializable
data class Book(
    val id: String,
    val title: String,
    val format: String,
    val pageCount: Int,
    val createdAt: Long,
    val lastOpened: Long = 0L,
    val page: Int = 0,
    val sentence: Int = 0,
    val hasCover: Boolean = false
) {
    val progress: Float get() = if (pageCount <= 1) (if (page > 0) 1f else 0f) else page.toFloat() / (pageCount - 1)
}

data class SentenceRef(val block: Int, val start: Int, val end: Int, val kind: Int)

object Sentences {

    private val abbreviations = setOf(
        "mr", "mrs", "ms", "dr", "prof", "sr", "jr", "st", "vs", "etc", "fig", "figs", "eq", "no", "vol",
        "pp", "p", "ch", "sec", "e.g", "i.e", "approx", "dept", "govt", "u.s", "a.m", "p.m"
    )

    fun split(text: String): List<IntRange> {
        val out = mutableListOf<IntRange>()
        var start = 0
        var i = 0
        while (i < text.length) {
            val c = text[i]
            val terminal = c == '.' || c == '?' || c == '!' || c == '।' || c == '॥' || c == '…'
            if (terminal) {
                var end = i + 1
                while (end < text.length && (text[end] in ".?!।॥\"'”’)…")) end++
                val atBoundary = end >= text.length || text[end].isWhitespace()
                if (atBoundary && !isAbbreviation(text, start, i) && !isNumberDot(text, i)) {
                    if (text.substring(start, end).isNotBlank()) out += trim(text, start, end)
                    start = end
                    i = end
                    continue
                }
            }
            i++
        }
        if (start < text.length && text.substring(start).isNotBlank()) out += trim(text, start, text.length)
        return mergeTiny(text, out)
    }

    private fun trim(text: String, start: Int, end: Int): IntRange {
        var s = start
        var e = end
        while (s < e && text[s].isWhitespace()) s++
        while (e > s && text[e - 1].isWhitespace()) e--
        return s until e
    }

    private fun isAbbreviation(text: String, start: Int, dot: Int): Boolean {
        if (text[dot] != '.') return false
        var s = dot - 1
        while (s >= start && (text[s].isLetter() || text[s] == '.')) s--
        val word = text.substring(s + 1, dot).lowercase()
        if (word.length == 1 && word[0].isLetter() && word[0] in 'a'..'z') return true
        return word in abbreviations
    }

    private fun isNumberDot(text: String, dot: Int): Boolean =
        text[dot] == '.' && dot > 0 && text[dot - 1].isDigit() && dot + 1 < text.length && text[dot + 1].isDigit()

    private fun mergeTiny(text: String, ranges: List<IntRange>): List<IntRange> {
        if (ranges.size < 2) return ranges
        val out = mutableListOf<IntRange>()
        for (r in ranges) {
            val last = out.lastOrNull()
            if (last != null && text.substring(last.first, last.last + 1).count { it.isLetter() } < 3) {
                out[out.size - 1] = last.first..r.last
            } else out += r
        }
        return out
    }

    fun forPage(page: Page): List<SentenceRef> = page.blocks.flatMapIndexed { index, block ->
        split(block.text).map { SentenceRef(index, it.first, it.last + 1, block.kind) }
    }
}
