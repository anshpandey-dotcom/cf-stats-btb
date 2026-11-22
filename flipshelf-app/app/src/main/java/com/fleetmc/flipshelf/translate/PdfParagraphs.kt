package com.fleetmc.flipshelf.translate

import com.fleetmc.flipshelf.core.Headings

object PdfParagraphs {

    private const val PAGE_END = "\u0000"
    private val pageNumber = Regex("^[\\s\\-–—]*\\d{1,4}[\\s\\-–—]*$")
    private val sentenceEnd = Regex("[.!?:।\"'”’)\\]]$")
    private const val CHAPTER_LIMIT = 14000

    fun build(pages: List<String>): List<Chapter> {
        val pageLines = pages.map { page ->
            page.replace("\r\n", "\n").replace('\r', '\n').split('\n').map { it.trim() }
        }
        val repeated = repeatedLines(pageLines)
        val lines = ArrayList<String>()
        for (page in pageLines) {
            for (line in page) {
                if (line.isNotEmpty() && (pageNumber.matches(line) || line in repeated)) continue
                lines.add(line)
            }
            lines.add(PAGE_END)
        }
        val lengths = lines.filter { it.isNotEmpty() && it != PAGE_END }.map { it.length }.sorted()
        val reference = if (lengths.isEmpty()) 80 else lengths[(lengths.size * 0.9).toInt().coerceAtMost(lengths.size - 1)]
        val paragraphs = ArrayList<String>()
        val current = StringBuilder()
        var last = ""
        var afterHeading = false
        fun flush() {
            if (current.isNotBlank()) paragraphs.add(current.toString().trim())
            current.setLength(0)
        }
        for (line in lines) {
            if (line == PAGE_END) continue
            if (line.isEmpty()) {
                flush()
                last = ""
                continue
            }
            if (afterHeading) {
                afterHeading = false
                if (Headings.isTitleLike(line) && !Headings.isStrong(line)) {
                    paragraphs.add(line)
                    last = ""
                    continue
                }
            }
            val headingLine = Headings.isStrong(line) || (Headings.isAllCaps(line) && line.length <= 50)
            if (headingLine) {
                flush()
                paragraphs.add(line)
                last = ""
                afterHeading = true
                continue
            }
            if (current.isNotEmpty()) {
                val shortLast = last.length < reference * 0.6f && sentenceEnd.containsMatchIn(last)
                if (shortLast) {
                    flush()
                } else if (current.endsWith("-") && line.first().isLowerCase()) {
                    current.setLength(current.length - 1)
                } else {
                    current.append(' ')
                }
            }
            current.append(line)
            last = line
        }
        flush()
        return assemble(paragraphs)
    }

    private fun assemble(paragraphs: List<String>): List<Chapter> {
        val chapters = ArrayList<Chapter>()
        var heading: String? = null
        var blocks = ArrayList<Block>()
        var size = 0
        fun close() {
            if (blocks.isNotEmpty() || heading != null) chapters.add(Chapter(heading, blocks))
            blocks = ArrayList()
            size = 0
        }
        var i = 0
        while (i < paragraphs.size) {
            val p = paragraphs[i]
            if (Headings.isStrong(p)) {
                close()
                val (title, merged) = Headings.mergeChapterTitle(p, paragraphs.getOrNull(i + 1))
                heading = title
                i += if (merged) 2 else 1
                continue
            }
            if (Headings.isAllCaps(p) && p.length <= 50) {
                blocks.add(Block(p, 2))
            } else {
                blocks.add(Block(p, 0))
                size += p.length
                if (size >= CHAPTER_LIMIT && heading == null) {
                    close()
                }
            }
            i++
        }
        close()
        return chapters
    }

    private fun repeatedLines(pageLines: List<List<String>>): Set<String> {
        if (pageLines.size < 6) return emptySet()
        val counts = HashMap<String, Int>()
        for (page in pageLines) {
            val edge = page.filter { it.isNotEmpty() }.let { it.take(2) + it.takeLast(2) }.toSet()
            for (line in edge) if (line.length in 3..80) counts[line] = (counts[line] ?: 0) + 1
        }
        val limit = pageLines.size * 0.3
        return counts.filter { it.value > limit }.keys
    }
}
