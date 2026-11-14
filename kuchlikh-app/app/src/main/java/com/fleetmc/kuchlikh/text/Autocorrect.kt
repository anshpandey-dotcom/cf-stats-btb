package com.fleetmc.kuchlikh.text

import android.content.res.AssetManager

class Fixed(val text: String, val changes: Int)

object Autocorrect {

    @Volatile
    private var misspellings: Map<String, String> = emptyMap()

    private val contractions = mapOf(
        "dont" to "don't", "cant" to "can't", "wont" to "won't", "isnt" to "isn't", "arent" to "aren't",
        "wasnt" to "wasn't", "werent" to "weren't", "doesnt" to "doesn't", "didnt" to "didn't", "hasnt" to "hasn't",
        "havent" to "haven't", "hadnt" to "hadn't", "couldnt" to "couldn't", "shouldnt" to "shouldn't",
        "wouldnt" to "wouldn't", "mustnt" to "mustn't", "im" to "I'm", "ive" to "I've", "youre" to "you're",
        "youve" to "you've", "youll" to "you'll", "theyre" to "they're", "theyve" to "they've", "theyll" to "they'll",
        "weve" to "we've", "thats" to "that's", "whats" to "what's", "wheres" to "where's", "heres" to "here's",
        "theres" to "there's", "shes" to "she's", "couldve" to "could've", "shouldve" to "should've",
        "wouldve" to "would've", "aint" to "ain't", "i" to "I", "teh" to "the", "adn" to "and", "hte" to "the",
        "taht" to "that", "thier" to "their", "alot" to "a lot", "recieve" to "receive", "definately" to "definitely",
        "seperate" to "separate", "untill" to "until", "wich" to "which", "becuase" to "because", "freind" to "friend",
        "tommorow" to "tomorrow", "tomorow" to "tomorrow", "beleive" to "believe", "writting" to "writing",
        "begining" to "beginning", "occured" to "occurred", "wierd" to "weird", "realy" to "really",
    )

    private val silentH = setOf("hour", "hours", "honest", "honestly", "honor", "honour", "heir", "honorable")
    private val hardU = listOf("uni", "use", "usu", "uti", "ure", "uro", "ubi", "eu", "one", "once", "ewe", "uk")

    fun load(assets: AssetManager) {
        if (misspellings.isNotEmpty()) return
        val map = HashMap<String, String>(6000)
        runCatching {
            assets.open("misspellings.txt").bufferedReader().useLines { lines ->
                lines.forEach { line ->
                    val cut = line.indexOf(' ')
                    if (cut > 0) map[line.substring(0, cut)] = line.substring(cut + 1)
                }
            }
        }
        misspellings = map
    }

    fun word(word: String, sentenceStart: Boolean): String {
        if (word.isEmpty()) return word
        if (word.drop(1).any { it.isUpperCase() }) return word
        val lower = word.lowercase()
        var result = contractions[lower] ?: misspellings[lower] ?: lower
        if (word[0].isUpperCase() || sentenceStart) result = result.replaceFirstChar { it.uppercaseChar() }
        return result
    }

    fun vowelSound(word: String): Boolean? {
        val lower = word.lowercase()
        if (lower.isEmpty() || !lower[0].isLetter() || lower[0] > 'z') return null
        if (word.length > 1 && word.all { it.isUpperCase() }) return null
        if (lower in silentH) return true
        if (hardU.any { lower.startsWith(it) }) return false
        return lower[0] in "aeiou"
    }

    fun article(article: String, next: String): String {
        val vowel = vowelSound(next) ?: return article
        val lower = article.lowercase()
        if (lower == "a" && vowel) return if (article[0].isUpperCase()) "An" else "an"
        if (lower == "an" && !vowel) return if (article[0].isUpperCase()) "A" else "a"
        return article
    }

    fun sentenceStart(text: String, index: Int, startOfFlow: Boolean): Boolean {
        var i = index - 1
        while (i >= 0 && (text[i] == ' ' || text[i] == '"' || text[i] == '\'' || text[i] == '(')) i--
        if (i < 0) return startOfFlow
        val c = text[i]
        return c == '.' || c == '!' || c == '?' || c == '\n' || c == '।'
    }

    private val spaces = Regex("[ \\t]{2,}")
    private val spaceBefore = Regex("(?<=\\S) +([,.!?;:।])")
    private val spaceAfter = Regex("([,;!?])(?=[A-Za-z\\u0900-\\u097F])")
    private val stopAfter = Regex("(?<=[a-z]{2})\\.(?!(?:com|org|net|in|io|co|edu|gov|app)\\b)(?=[A-Za-z]{2})")
    private val repeated = Regex("\\b([A-Za-z]{2,})( +)\\1\\b", RegexOption.IGNORE_CASE)
    private val keepRepeats = setOf("that", "had", "very", "so", "no", "bye", "ha", "ho")
    private val articlePair = Regex("\\b(a|an|A|An) +([A-Za-z]+)")
    private val plainWord = Regex("[A-Za-z]+")

    fun fixText(input: String, startOfFlow: Boolean): Fixed {
        var changes = 0
        var text = input

        fun pass(regex: Regex, transform: (MatchResult) -> String) {
            text = regex.replace(text) { match ->
                val replacement = transform(match)
                if (replacement != match.value) changes++
                replacement
            }
        }

        pass(spaces) { " " }
        pass(spaceBefore) { it.groupValues[1] }
        pass(spaceAfter) { it.groupValues[1] + " " }
        pass(stopAfter) { ". " }
        pass(repeated) { match ->
            if (match.groupValues[1].lowercase() in keepRepeats) match.value else match.groupValues[1]
        }

        val current = text
        val words = StringBuilder()
        var last = 0
        for (match in plainWord.findAll(current)) {
            words.append(current, last, match.range.first)
            val start = match.range.first
            val attached = start > 0 && (current[start - 1] == '\'' || current[start - 1] == '’')
            val fixed = if (attached) match.value else word(match.value, sentenceStart(current, start, startOfFlow))
            if (fixed != match.value) changes++
            words.append(fixed)
            last = match.range.last + 1
        }
        words.append(current, last, current.length)
        text = words.toString()

        pass(articlePair) { match ->
            article(match.groupValues[1], match.groupValues[2]) + match.value.substring(match.groupValues[1].length)
        }
        return Fixed(text, changes)
    }
}
