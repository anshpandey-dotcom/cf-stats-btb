package com.fleetmc.flipshelf.translate

object TranslationPipeline {

    private val letters = Regex("\\p{L}")
    private val sentenceSplit = Regex("(?<=[.!?।])\\s+")
    private const val SEGMENT = 700

    class Cancelled : Exception()

    fun segments(text: String): List<String> {
        if (text.length <= SEGMENT + 100) return listOf(text)
        val out = ArrayList<String>()
        val current = StringBuilder()
        for (sentence in text.split(sentenceSplit)) {
            if (current.isNotEmpty() && current.length + sentence.length + 1 > SEGMENT) {
                out.add(current.toString())
                current.setLength(0)
            }
            if (sentence.length > SEGMENT) {
                var from = 0
                while (from < sentence.length) {
                    val to = minOf(sentence.length, from + SEGMENT)
                    out.add(sentence.substring(from, to))
                    from = to
                }
            } else {
                if (current.isNotEmpty()) current.append(' ')
                current.append(sentence)
            }
        }
        if (current.isNotEmpty()) out.add(current.toString())
        return out
    }

    fun run(
        chapters: List<Chapter>,
        translate: (String) -> String,
        onProgress: (Int) -> Unit,
        isCancelled: () -> Boolean
    ): List<Chapter> {
        val total = chapters.sumOf { c -> c.paragraphs.sumOf { it.length } + (c.heading?.length ?: 0) }.coerceAtLeast(1)
        var done = 0
        var lastReported = -1
        fun one(text: String): String {
            if (isCancelled()) throw Cancelled()
            val result = if (letters.containsMatchIn(text)) {
                segments(text).joinToString(" ") { translate(it) }
            } else text
            done += text.length
            val percent = (done * 100L / total).toInt().coerceIn(0, 100)
            if (percent != lastReported) {
                lastReported = percent
                onProgress(percent)
            }
            return result
        }
        return chapters.map { c ->
            Chapter(c.heading?.let { one(it) }, c.paragraphs.map { one(it) })
        }
    }
}
