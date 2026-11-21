package com.fleetmc.flipshelf.translate

object PdfParagraphs {

    private const val PAGE_END = "\u0000"
    private val pageNumber = Regex("^[\\s\\-–—]*\\d{1,4}[\\s\\-–—]*$")
    private val sentenceEnd = Regex("[.!?:।\"'”’)\\]]$")

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
        return group(paragraphs)
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

    private fun group(paragraphs: List<String>): List<Chapter> {
        val chapters = ArrayList<Chapter>()
        var bucket = ArrayList<String>()
        var size = 0
        for (p in paragraphs) {
            bucket.add(p)
            size += p.length
            if (size >= 9000) {
                chapters.add(Chapter(null, bucket))
                bucket = ArrayList()
                size = 0
            }
        }
        if (bucket.isNotEmpty()) chapters.add(Chapter(null, bucket))
        return chapters
    }
}
