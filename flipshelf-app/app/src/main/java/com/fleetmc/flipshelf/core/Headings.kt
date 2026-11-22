package com.fleetmc.flipshelf.core

object Headings {

    private const val STANDALONE = "introduction|preface|foreword|prologue|epilogue|contents|table of contents|afterword|conclusion|appendix|" +
        "acknowledg(?:e)?ments?|about the author|प्रस्तावना|भूमिका|उपसंहार|आभार|अनुक्रमणिका|परिचय|प्राक्कथन|अनुक्रम"
    private const val NUMBERED = "chapter|part|book|section|interlude|अध्याय|भाग|खंड|खण्ड|प्रकरण"
    private const val NUMBER = "[\\p{N}]{1,4}|[IVXLC]{1,8}|one|two|three|four|five|six|seven|eight|nine|ten|eleven|twelve|thirteen|" +
        "fourteen|fifteen|sixteen|seventeen|eighteen|nineteen|twenty|first|second|third|fourth|fifth|sixth|seventh|eighth|ninth|tenth"

    private val standalone = Regex("^\\s*(?:$STANDALONE)\\s*[:.\\-–—]?\\s*$", RegexOption.IGNORE_CASE)
    private val numbered = Regex(
        "^\\s*(?:$NUMBERED)\\s*(?:(?:$NUMBER)(?![\\p{L}])\\s*)?(?:[:.\\-–—]\\s*[^\\n]{1,70})?[.:]?\\s*$",
        RegexOption.IGNORE_CASE
    )
    private val numberedNeedsNumber = Regex("^\\s*(?:$NUMBERED)\\s*$", RegexOption.IGNORE_CASE)
    private val chapterOnly = Regex("^\\s*(?:chapter|part|book|अध्याय|भाग|खंड|खण्ड)\\b[\\s.:\\-–—]*(?:$NUMBER)?[.:]?\\s*$", RegexOption.IGNORE_CASE)
    private val sentenceEnd = Regex("[.!?।]$")

    fun isStrong(line: String): Boolean {
        val t = line.trim()
        if (t.length < 3 || t.length > 80) return false
        if (t.split(Regex("\\s+")).size > 12) return false
        if (standalone.matches(t)) return true
        if (numberedNeedsNumber.matches(t)) return false
        if (!numbered.matches(t)) return false
        return !Regex("[!?]$").containsMatchIn(t)
    }

    fun isAllCaps(line: String): Boolean {
        val t = line.trim()
        if (t.length < 4 || t.length > 60) return false
        val letters = t.filter { it.isLetter() }
        if (letters.length < 4) return false
        return letters.none { it.isLowerCase() } && letters.any { it.isUpperCase() } && !sentenceEnd.containsMatchIn(t)
    }

    fun isTitleLike(line: String): Boolean {
        val t = line.trim()
        return t.isNotEmpty() && t.length <= 70 && !sentenceEnd.containsMatchIn(t) && !t.endsWith(",") && t.split(Regex("\\s+")).size <= 9
    }

    fun mergeChapterTitle(heading: String, next: String?): Pair<String, Boolean> {
        if (next != null && chapterOnly.matches(heading) && isTitleLike(next)) {
            return Pair(heading.trim() + " — " + next.trim(), true)
        }
        return Pair(heading.trim(), false)
    }
}
