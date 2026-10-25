package com.anshpandey.pingai

import java.util.Locale

object Classifier {

    private val fillers = setOf(
        "bhai", "bro", "yaar", "dost", "bhaiya", "ansh", "ji", "sir", "dear", "mate", "dude",
        "buddy", "there", "a", "lot", "so", "much", "very", "you", "u", "all", "everyone", "sab"
    )

    private val acks = setOf(
        "ok", "okay", "okk", "okey", "k", "kk", "ha", "haan", "han", "hmm", "hm", "hmmm", "nice",
        "cool", "great", "accha", "achha", "acha", "thik", "theek", "thik hai", "theek hai", "done",
        "got it", "sure", "yes", "yep", "yeah", "no", "nope", "nahi", "lol", "haha", "hahaha", "hehe",
        "welcome", "your welcome", "you are welcome", "youre welcome", "are welcome", "most welcome",
        "ji haan", "ok ok", "okay okay", "oh", "ohh", "ooh", "wow"
    )

    private val noiseKinds = Regex(
        "^(sticker|gif|contact|location|missed voice call|missed video call|video call|voice call|calling|ringing)( [0-9 ]+)?$"
    )
    private val photo = Regex("^(photo|image|picture)( [0-9 ]+)?$")
    private val voice = Regex("^(voice message|voice note|audio|voice)( [0-9 ]+)?$")
    private val video = Regex("^video( [0-9 ]+)?$")
    private val file = Regex("^(document|file)( [0-9 ]+)?$")

    private val hiLike = Regex("^h+i+$")
    private val heyLike = Regex("^he+y+$")
    private val helloLike = Regex("^he+l+o+w*$")
    private val byeLike = Regex("^b+y+e+$")
    private val thanks = setOf(
        "thanks", "thank", "thankyou", "thanku", "thnx", "thnks", "thx", "tnx", "ty", "tysm", "thanx",
        "thanxs", "shukriya", "dhanyavad", "dhanyawad", "thank yous", "thanks thanks"
    )
    private val otp = Regex("\\b\\d{4,8}\\b")

    private val hinglishMarkers = setOf(
        "hai", "h", "hu", "hoon", "ho", "kya", "kaha", "kahan", "kidhar", "kab", "kb", "kbtk", "kaise",
        "kese", "kyu", "kyun", "nahi", "nhi", "mai", "main", "mujhe", "mera", "meri", "tera", "tum", "tu",
        "aap", "ye", "yeh", "wo", "woh", "bhai", "yaar", "abhi", "baat", "krna", "karna", "karo", "kro",
        "kar", "kr", "rha", "raha", "rhi", "gaya", "gya", "ayega", "aayega", "aaoge", "hoga", "acha",
        "accha", "thik", "theek", "chal", "bol", "bata", "btao", "btana", "dena", "lena", "paise", "kuch",
        "aur", "toh", "ka", "ki", "ke", "ko", "se", "mein", "pe", "wala", "wali", "bhi", "mat", "bas"
    )

    fun normalize(text: String): String =
        text.lowercase(Locale.ROOT)
            .replace(Regex("[^\\p{L}\\p{N}\\s]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()

    private fun core(n: String): String =
        n.split(" ").filter { it.isNotEmpty() && it !in fillers }.joinToString(" ")

    fun mediaKind(text: String): String? {
        val n = normalize(text)
        return when {
            photo.matches(n) -> "photo"
            voice.matches(n) -> "voice message"
            video.matches(n) -> "video"
            file.matches(n) -> "file"
            else -> null
        }
    }

    fun isNoise(text: String): Boolean {
        val n = normalize(text)
        if (n.isEmpty()) return true
        if (mediaKind(text) != null) return false
        if (noiseKinds.matches(n)) return true
        if (n in acks || core(n) in acks) return true
        if (otp.containsMatchIn(n) && (n.contains("otp") || n.contains("code") || n.contains("verification"))) return true
        return false
    }

    fun quickReply(text: String): String? {
        val n = normalize(text)
        if (n.isEmpty()) return null
        val c = core(n)
        if (c.isEmpty()) return null
        val tokens = c.split(" ")
        return when {
            hiLike.matches(c) -> "hi"
            heyLike.matches(c) -> "hey"
            helloLike.matches(c) || c == "hlo" || c == "helo" || c == "hola" || c == "yo" || c == "oye" -> "hello"
            c == "namaste" || c == "namaskar" || c == "pranam" -> "namaste"
            c == "good morning" || c == "gm" || c == "suprabhat" -> "good morning"
            c == "good afternoon" -> "good afternoon"
            c == "good night" || c == "gn" || c == "shubh ratri" -> "good night"
            tokens.all { it in thanks } -> "welcome"
            tokens.all { byeLike.matches(it) || it == "goodbye" || it == "tata" || it == "alvida" || it == "cya" } -> "bye"
            c == "take care" || c == "tc" -> "take care"
            else -> null
        }
    }

    fun callRequest(text: String, phrases: String): Boolean {
        val n = " " + normalize(text) + " "
        return phrases.split(",")
            .map { normalize(it) }
            .filter { it.isNotEmpty() }
            .any { n.contains(" $it ") || n.contains(it) && it.contains(" ") }
    }

    fun hasBlocked(text: String, words: String): Boolean {
        val n = " " + normalize(text) + " "
        return words.split(",")
            .map { normalize(it) }
            .filter { it.isNotEmpty() }
            .any { n.contains(" $it ") }
    }

    fun looksHinglish(text: String): Boolean? {
        val tokens = normalize(text).split(" ").filter { it.isNotEmpty() }
        if (tokens.any { it in hinglishMarkers }) return true
        return if (tokens.size >= 2) false else null
    }
}
