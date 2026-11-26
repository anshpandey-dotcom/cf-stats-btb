package com.lagradost.cloudstream3.ui.search

object TitleCleaner {
    private val bracketed = Regex("[\\[({][^\\])}]*[\\])}]")
    private val noise = Regex(
        "\\b(" +
            "\\d{3,4}p|4k|uhd|fhd|hd|hdr|hdrip|hdtc|hdcam|web[-\\s.]?dl|web[-\\s.]?rip|webrip|bluray|blu[-\\s.]?ray|brrip|bdrip|dvdrip|dvdscr|camrip|pre[-\\s.]?dvd|" +
            "x264|x265|h\\.?264|h\\.?265|hevc|avc|10bit|8bit|aac|ac3|dd[+p]?\\s?\\d\\.\\d|ddp?\\d?\\.?\\d?|atmos|esubs?|msubs?|subbed|dubbed|org|" +
            "dual[-\\s.]?audio|multi[-\\s.]?audio|hindi|english|tamil|telugu|malayalam|kannada|bengali|punjabi|marathi|" +
            "full[-\\s.]?movie|watch[-\\s.]?online|download|free|leaked|proper|uncut|unrated|complete|amzn|nf|zee5|jiohotstar|hotstar|prime|netflix" +
            ")\\b",
        RegexOption.IGNORE_CASE
    )
    private val sizeToken = Regex("\\b\\d+(\\.\\d+)?\\s?(gb|mb)\\b", RegexOption.IGNORE_CASE)
    private val separators = Regex("[|_]+|\\s[-–]\\s*$|\\s{2,}")
    private val trailingYear = Regex("\\s+(19|20)\\d{2}\\s*$")

    fun clean(raw: String?): String {
        if (raw.isNullOrBlank()) return raw.orEmpty()
        var text = raw.replace(bracketed, " ")
        text = text.replace(sizeToken, " ")
        text = text.replace(noise, " ")
        text = text.replace(Regex("[&+,]\\s*(?=[&+,]|$)"), " ")
        text = text.replace(separators, " ").trim(' ', '-', '–', ':', '.', ',', '&', '+')
        text = text.replace(trailingYear, "").trim()
        return text.ifBlank { raw.trim() }
    }
}
