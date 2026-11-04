package com.ansh.recalm.importer

import com.ansh.recalm.data.Block
import com.ansh.recalm.data.BlockKind
import com.ansh.recalm.data.Page

object TextCleaner {

    private const val CONSONANT = "[\\u0915-\\u0939\\u0958-\\u095F]\\u093C?"
    private val visualIMatra = Regex("\\u093F((?:$CONSONANT\\u094D)*$CONSONANT)")
    private val danglingReph = Regex("((?:$CONSONANT\\u094D)*$CONSONANT[\\u093E-\\u094C\\u0901\\u0902]*)\\u0930\\u094D(?![\\u0915-\\u0939\\u0958-\\u095F])")
    private val iMatraAfterNonConsonant = Regex("(^|[^\\u0915-\\u0939\\u0958-\\u095F\\u093C])\\u093F", RegexOption.MULTILINE)

    private val pageNumber = Regex("^(page\\s*)?[\\divxlcdmIVXLCDM०-९]{1,6}(\\s*/\\s*\\d+)?$|^[-–—]\\s*\\d+\\s*[-–—]$|^पृष्ठ\\s*[\\d०-९]+$", RegexOption.IGNORE_CASE)
    private val numbered = Regex("^(\\d+(\\.\\d+){0,3}|[A-Z]\\.|[IVX]+\\.)\\s+\\S")
    private val chapterStart = Regex("^(chapter|unit|lesson|part|अध्याय|पाठ|इकाई)\\b", RegexOption.IGNORE_CASE)
    private val exerciseHeading = Regex(
        "^(exercises?|questions?|intext questions|in-text questions|review questions|textbook questions|" +
            "test yourself|check your progress|let us (recall|practise|practice)|think and answer|answer the following|" +
            "what you have learnt|अभ्यास|प्रश्न[\\s-]*अभ्यास|प्रश्नावली|पाठ्यपुस्तक से|प्रश्न|आइए विचार करें|सोचिए और बताइए)\\b.*",
        RegexOption.IGNORE_CASE
    )
    private val activityHeading = Regex("^(activity|क्रियाकलाप|गतिविधि|do this|try this|project)\\s*[\\d०-९.]*.*", RegexOption.IGNORE_CASE)
    private val caption = Regex("^(fig\\.?|figure|चित्र|table|तालिका|सारणी)\\s*[\\d०-९]+([.:]\\d+)*", RegexOption.IGNORE_CASE)
    private val question = Regex("^(q\\.?\\s*\\d+|प्रश्न\\s*[\\d०-९]+|\\(?[\\d०-९]{1,2}[.)]|\\(?[a-z][.)]|\\(?[ivx]{1,4}[.)])\\s*", RegexOption.IGNORE_CASE)
    private val boilerplate = Regex(
        "©|\\(c\\)\\s*\\d{4}|copyright|all rights reserved|isbn|issn|printed (in|by|at)|published by|publisher|first (published|edition)|" +
            "reprint(ed)?\\s+\\d{4}|no part of this (publication|book)|without (the )?prior (written )?permission|www\\.|https?://|@[a-z0-9-]+\\.|" +
            "phone\\s*:|fax\\s*:|tel\\s*\\.?:|e-?mail\\s*:|cataloging|library of congress|headquarters|suite \\d+|" +
            "सर्वाधिकार|प्रकाशक|प्रकाशन विभाग|मुद्रक|मुद्रित|प्रथम संस्करण|पुनर्मुद्रण|मूल्य\\s*[:₹]|₹\\s*\\d",
        RegexOption.IGNORE_CASE
    )
    private val terminal = charArrayOf('.', '?', '!', '।', '॥', ':', '"', '”')

    fun fixDevanagariOrder(text: String): String {
        if (text.none { it in 'ऀ'..'ॿ' }) return text
        val iCount = text.count { it == 'ि' }
        var fixed = text
        if (iCount > 0) {
            val misplaced = iMatraAfterNonConsonant.findAll(text).count()
            if (misplaced * 4 >= iCount) fixed = visualIMatra.replace(fixed) { it.groupValues[1] + "ि" }
        }
        return danglingReph.replace(fixed) { "र्" + it.groupValues[1] }
    }

    private fun normalizeLine(line: String) = line.trim().lowercase().replace(Regex("[\\d०-९]+"), "#").replace(Regex("\\s+"), " ")

    fun stripRunningLines(pages: List<List<String>>): List<List<String>> {
        if (pages.size < 4) return pages.map { lines -> lines.filterNot { pageNumber.matches(it.trim()) } }
        val counts = HashMap<String, Int>()
        pages.forEach { lines ->
            val edge = (lines.take(2) + lines.takeLast(2)).map(::normalizeLine).filter { it.isNotBlank() }.toSet()
            edge.forEach { counts[it] = (counts[it] ?: 0) + 1 }
        }
        val threshold = maxOf(3, pages.size / 4)
        val running = counts.filter { it.value >= threshold && it.key.length < 120 }.keys
        return pages.map { lines ->
            lines.filterIndexed { i, line ->
                val t = line.trim()
                val edge = i < 2 || i >= lines.size - 2
                !(pageNumber.matches(t) || (edge && normalizeLine(t) in running))
            }
        }
    }

    fun paragraphs(lines: List<String>): List<String> {
        val cleaned = lines.map { it.replace(' ', ' ').replace(Regex("[\\t ]+"), " ").trim() }
        val nonEmpty = cleaned.filter { it.isNotEmpty() }
        val typical = nonEmpty.map { it.length }.sorted().let { if (it.isEmpty()) 60 else it[it.size * 3 / 4] }
        val out = mutableListOf<String>()
        val current = StringBuilder()
        fun flush() {
            val text = current.toString().trim()
            if (text.isNotEmpty()) out += text
            current.clear()
        }
        for (i in cleaned.indices) {
            val line = cleaned[i]
            if (line.isEmpty()) {
                flush()
                continue
            }
            if (current.isNotEmpty()) {
                val prev = current.toString()
                val prevEnds = prev.trimEnd().lastOrNull()?.let { it in terminal } == true
                val prevShort = prev.substringAfterLast('\n').length < typical * 0.6 &&
                    cleaned[i - 1].length < typical * 0.7
                val headingLike = looksLikeHeading(line) || caption.containsMatchIn(line) ||
                    question.containsMatchIn(line) && prevEnds
                if ((prevEnds && prevShort) || headingLike || looksLikeHeading(cleaned[i - 1])) flush()
            }
            if (current.isEmpty()) current.append(line) else {
                val last = current.last()
                if (last == '-' && line.firstOrNull()?.isLowerCase() == true) {
                    current.setLength(current.length - 1)
                    current.append(line)
                } else {
                    current.append(' ').append(line)
                }
            }
        }
        flush()
        return out
    }

    fun looksLikeHeading(line: String): Boolean {
        val t = line.trim()
        if (t.isEmpty() || t.length > 90) return false
        if (t.last() in charArrayOf('.', ',', ';', '।', '?')) return false
        val words = t.split(' ')
        if (words.size > 12) return false
        if (numbered.containsMatchIn(t) && words.size <= 10) return true
        if (chapterStart.containsMatchIn(t)) return true
        if (exerciseHeading.matches(t) || activityHeading.matches(t)) return true
        val letters = t.filter { it.isLetter() }
        if (letters.length >= 4 && letters.all { !it.isLowerCase() } && letters.any { it in 'A'..'Z' }) return true
        return false
    }

    fun classify(paragraphs: List<String>, state: ClassifyState): List<Block> = paragraphs.map { text ->
        val t = text.trim()
        val heading = looksLikeHeading(t)
        when {
            isBoilerplate(t) -> Block(t, BlockKind.BOILERPLATE)
            heading && exerciseHeading.matches(t) -> {
                state.inExercise = true
                state.inActivity = false
                Block(t, BlockKind.EXERCISE)
            }
            heading && activityHeading.matches(t) -> {
                state.inActivity = true
                Block(t, BlockKind.ACTIVITY)
            }
            heading -> {
                if (chapterStart.containsMatchIn(t) || numbered.containsMatchIn(t)) state.inExercise = false
                state.inActivity = false
                Block(t, if (state.inExercise) BlockKind.EXERCISE else BlockKind.HEADING)
            }
            state.inExercise -> Block(t, BlockKind.EXERCISE)
            state.inActivity -> Block(t, BlockKind.ACTIVITY)
            caption.containsMatchIn(t) && t.length < 260 -> Block(t, BlockKind.CAPTION)
            isQuestion(t) -> Block(t, BlockKind.QUESTION)
            else -> Block(t, BlockKind.BODY)
        }
    }

    fun isBoilerplate(t: String): Boolean {
        val hits = boilerplate.findAll(t).count()
        if (hits == 0) return false
        val words = t.split(Regex("\\s+")).size
        return hits >= 3 || (hits >= 2 && hits * 50 >= words) || words <= 20
    }

    private fun isQuestion(t: String): Boolean {
        if (t.length > 420) return false
        val end = t.trimEnd().lastOrNull() ?: return false
        if (end == '?') return true
        return question.containsMatchIn(t) && t.contains('?')
    }

    class ClassifyState {
        var inExercise = false
        var inActivity = false
    }

    fun buildPages(rawPages: List<String>, sourceOffset: Int = 0): List<Page> {
        val lines = rawPages.map { fixDevanagariOrder(it).lines() }
        val stripped = stripRunningLines(lines)
        val state = ClassifyState()
        return markBoilerplatePages(stripped.mapIndexed { i, pageLines ->
            Page(classify(paragraphs(pageLines), state), source = sourceOffset + i)
        })
    }

    fun markBoilerplatePages(pages: List<Page>): List<Page> = pages.map { page ->
        val total = page.blocks.sumOf { it.text.length }.coerceAtLeast(1)
        val legal = page.blocks.filter { it.kind == BlockKind.BOILERPLATE }
        val legalChars = legal.sumOf { it.text.length }
        if (legal.size >= 3 && legalChars * 2 >= total || legal.size >= 2 && legalChars * 10 >= total * 7) {
            page.copy(blocks = page.blocks.map { if (it.kind == BlockKind.BODY || it.kind == BlockKind.HEADING) it.copy(kind = BlockKind.BOILERPLATE) else it })
        } else page
    }

    fun paginate(blocks: List<Block>, target: Int = 1600): List<Page> {
        val pages = mutableListOf<Page>()
        var current = mutableListOf<Block>()
        var size = 0
        for (block in blocks) {
            if (size > 0 && size + block.text.length > target) {
                pages += Page(current)
                current = mutableListOf()
                size = 0
            }
            if (block.text.length > target * 2) {
                val parts = splitLong(block.text, target)
                for (part in parts) {
                    if (size > 0) {
                        pages += Page(current)
                        current = mutableListOf()
                        size = 0
                    }
                    current += Block(part, block.kind)
                    size += part.length
                }
                continue
            }
            current += block
            size += block.text.length
        }
        if (current.isNotEmpty()) pages += Page(current)
        return pages
    }

    private fun splitLong(text: String, target: Int): List<String> {
        val out = mutableListOf<String>()
        val sb = StringBuilder()
        for (range in com.ansh.recalm.data.Sentences.split(text)) {
            val s = text.substring(range.first, range.last + 1)
            if (sb.length + s.length > target && sb.isNotEmpty()) {
                out += sb.toString().trim()
                sb.clear()
            }
            sb.append(s).append(' ')
        }
        if (sb.isNotBlank()) out += sb.toString().trim()
        return out
    }
}
