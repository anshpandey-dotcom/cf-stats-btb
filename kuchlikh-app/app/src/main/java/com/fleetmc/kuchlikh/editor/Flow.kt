package com.fleetmc.kuchlikh.editor

import com.fleetmc.kuchlikh.data.Page
import com.fleetmc.kuchlikh.data.newId

data class Caret(val pageId: String, val offset: Int)

class Reflowed(val pages: List<Page>, val caret: Caret?)

object Flow {

    private const val WINDOW = 4000

    fun starts(pages: List<Page>, index: Int): Boolean =
        index == 0 || pages[index].flowStart || pages[index].chapter != null

    fun firstOf(pages: List<Page>, index: Int): Int {
        var i = index
        while (!starts(pages, i)) i--
        return i
    }

    fun reflow(
        pages: List<Page>,
        from: Int,
        caretOffset: Int?,
        dirtyUntil: Int,
        fit: (Page, String) -> Int,
    ): Reflowed {
        var end = from
        while (end + 1 < pages.size && !starts(pages, end + 1)) end++

        val stream = StringBuilder()
        val starts = IntArray(end - from + 1)
        for (j in from..end) {
            starts[j - from] = stream.length
            stream.append(pages[j].text)
            if (pages[j].para && j < end) stream.append('\n')
        }
        val total = stream.length

        val out = ArrayList<Page>(pages.size + 2)
        out.addAll(pages.subList(0, from))
        var caret: Caret? = null
        var pos = 0
        var j = from

        while (true) {
            val existing = j <= end
            val page = if (existing) pages[j] else Page(id = newId())
            val window = stream.substring(pos, minOf(total, pos + WINDOW))
            var keep = fit(page, window)

            if (keep < 0 && pos + window.length >= total) {
                out += page.copy(text = window, para = false)
                if (caret == null && caretOffset != null) {
                    caret = Caret(page.id, (caretOffset - pos).coerceIn(0, window.length))
                }
                j++
                while (j <= end) {
                    if (pages[j].image != null) out += pages[j].copy(text = "", para = false)
                    j++
                }
                break
            }

            if (keep < 0) keep = window.length
            if (keep == 0 && !existing) keep = minOf(window.length, 200)

            var text = window.substring(0, keep)
            var para = false
            if (text.endsWith("\n")) {
                text = text.dropLast(1)
                para = true
            }
            out += page.copy(text = text, para = para)
            if (caret == null && caretOffset != null && caretOffset < pos + keep) {
                caret = Caret(page.id, (caretOffset - pos).coerceIn(0, text.length))
            }
            pos += keep
            j++

            val aligned = j <= end && j > dirtyUntil && pos == starts[j - from]
            if (aligned && (caretOffset == null || caret != null || caretOffset == pos)) {
                if (caret == null && caretOffset != null) caret = Caret(pages[j].id, 0)
                for (m in j..end) out += pages[m]
                break
            }
        }

        out.addAll(pages.subList(end + 1, pages.size))
        return Reflowed(out, caret)
    }

    fun reflowAll(pages: List<Page>, fit: (Page, String) -> Int): List<Page> {
        var current = pages
        var i = 0
        while (i < current.size) {
            current = reflow(current, i, null, Int.MAX_VALUE, fit).pages
            i++
            while (i < current.size && !starts(current, i)) i++
        }
        return current
    }
}
