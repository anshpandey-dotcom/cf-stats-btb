package com.fleetmc.kuchlikh.text

object Tidy {

    private val emDash = Regex("\\s*[\\u2014\\u2015]\\s*")
    private val spacedEnDash = Regex("\\s+\\u2013\\s+")
    private val danda = Regex("(?<=[\\u0900-\\u0963])\\s*\\|")
    private val fullStop = Regex("(?<=[\\u0900-\\u0963])\\.(?=\\s|$|[\"”’)])")
    private val markup = Regex("(\\*\\*|__|`)")
    private val spaces = Regex("[ \\t]{2,}")
    private val beforeMark = Regex("\\s+([,.!?;:\\u0964])")
    private val afterDanda = Regex("(\\u0964)(?=[\\u0900-\\u097F])")
    private val commas = Regex("([,;:])\\1+")
    private val bangs = Regex("([!?])\\1{1,}")
    private val dots = Regex("\\.{4,}")
    private val commaStop = Regex(",\\s*([.!?\\u0964])")

    fun clean(input: String): String {
        var text = input.trim()
        text = emDash.replace(text, ", ")
        text = spacedEnDash.replace(text, ", ")
        text = markup.replace(text, "")
        text = danda.replace(text, "\u0964")
        text = fullStop.replace(text, "\u0964")
        text = spaces.replace(text, " ")
        text = beforeMark.replace(text) { it.groupValues[1] }
        text = afterDanda.replace(text, "\u0964 ")
        text = commas.replace(text) { it.groupValues[1] }
        text = bangs.replace(text) { it.groupValues[1] }
        text = dots.replace(text, "...")
        text = commaStop.replace(text) { it.groupValues[1] }
        return text.trim()
    }
}
