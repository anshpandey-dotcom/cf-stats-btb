package com.fleetmc.kuchlikh.text

class Typed(val text: String, val cursor: Int)

object TypingAssist {

    private const val BOUNDARIES = " \n\t.,!?;:)\"”"
    private const val TIGHT = ",.!?;:"

    private fun letter(c: Char) = (c in 'a'..'z') || (c in 'A'..'Z')

    fun apply(
        old: String,
        new: String,
        cursor: Int,
        hindi: Boolean,
        autocorrect: Boolean,
        startOfFlow: Boolean,
    ): Typed? {
        if (new.length != old.length + 1 || cursor < 1 || cursor > new.length) return null
        val at = cursor - 1
        if (!new.regionMatches(0, old, 0, at) || !new.regionMatches(cursor, old, at, old.length - at)) return null
        val typed = new[at]
        if (typed !in BOUNDARIES) return null

        var start = at
        while (start > 0 && letter(new[start - 1])) start--
        val word = new.substring(start, at)
        val attached = start > 0 && (new[start - 1] == '\'' || new[start - 1] == '’' || new[start - 1].isDigit())

        if (hindi) {
            if (word.isEmpty()) {
                if (typed == '.' && at > 0 && Transliterator.isDevanagari(new[at - 1])) {
                    return Typed(new.substring(0, at) + "।" + new.substring(cursor), cursor)
                }
                return null
            }
            if (attached) return null
            val converted = Transliterator.word(word)
            if (converted == word) return null
            val mark = if (typed == '.') "।" else typed.toString()
            val text = new.substring(0, start) + converted + mark + new.substring(cursor)
            return Typed(text, start + converted.length + mark.length)
        }

        if (!autocorrect) return null

        if (word.isEmpty()) {
            if (typed in TIGHT && at >= 2 && new[at - 1] == ' ' && new[at - 2].isLetterOrDigit()) {
                return Typed(new.removeRange(at - 1, at), cursor - 1)
            }
            return null
        }
        if (attached) return null

        val fixed = Autocorrect.word(word, Autocorrect.sentenceStart(new, start, startOfFlow))
        var head = new.substring(0, start)
        var changed = fixed != word

        if (start >= 2 && head.endsWith(" ")) {
            var from = start - 1
            while (from > 0 && letter(head[from - 1])) from--
            val previous = head.substring(from, start - 1)
            val standalone = from == 0 || !head[from - 1].isLetterOrDigit() && head[from - 1] != '\''
            if (standalone && (previous.equals("a", true) || previous.equals("an", true))) {
                val article = Autocorrect.article(previous, fixed)
                if (article != previous) {
                    head = head.substring(0, from) + article + " "
                    changed = true
                }
            }
        }
        if (!changed) return null
        val text = head + fixed + typed + new.substring(cursor)
        return Typed(text, head.length + fixed.length + 1)
    }
}
