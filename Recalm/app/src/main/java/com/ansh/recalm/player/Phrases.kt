package com.ansh.recalm.player

object Phrases {

    fun split(text: String, start: Int, end: Int, firstLimit: Int): List<IntRange> {
        val out = mutableListOf<IntRange>()
        var s = start
        var limit = firstLimit
        while (end - s > limit + 25) {
            val windowEnd = (s + limit + 25).coerceAtMost(end)
            var cut = -1
            for (i in windowEnd - 1 downTo s + limit / 2) {
                if (text[i] in ",;:—" && i + 1 < end && text[i + 1].isWhitespace()) {
                    cut = i + 1
                    break
                }
            }
            if (cut < 0) {
                for (i in (s + limit).coerceAtMost(windowEnd - 1) downTo s + limit / 2) {
                    if (text[i].isWhitespace()) {
                        cut = i
                        break
                    }
                }
            }
            if (cut <= s) break
            out += s until cut
            s = cut
            while (s < end && text[s].isWhitespace()) s++
            limit = (limit + 40).coerceAtMost(150)
        }
        if (s < end) out += s until end
        return out
    }
}
