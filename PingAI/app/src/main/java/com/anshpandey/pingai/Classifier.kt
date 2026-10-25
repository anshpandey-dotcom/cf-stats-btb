package com.anshpandey.pingai

import java.util.Locale

object Classifier {

    private val acks = setOf(
        "ok", "okay", "okk", "okey", "k", "kk", "ha", "haan", "han", "hmm", "hm", "hmmm",
        "thanks", "thank you", "thankyou", "thanku", "thx", "ty", "thanks a lot", "welcome",
        "your welcome", "you are welcome", "youre welcome", "bye", "byee", "bye bye", "tc",
        "take care", "nice", "cool", "great", "accha", "achha", "acha", "thik", "theek",
        "thik hai", "theek hai", "done", "got it", "sure", "yes", "yep", "yeah", "no", "nope",
        "nahi", "lol", "haha", "hahaha", "hehe", "gn", "good night", "shukriya", "dhanyavad", "ji", "ji haan"
    )

    private val media = Regex(
        "^(photo|video|sticker|gif|audio|voice message|document|image|contact|location|missed voice call|missed video call)( [0-9 ]+)?$"
    )

    private val hiLike = Regex("^h+i+$")
    private val heyLike = Regex("^he+y+( there)?$")
    private val helloLike = Regex("^he+l+o+$")
    private val otp = Regex("\\b\\d{4,8}\\b")

    fun normalize(text: String): String =
        text.lowercase(Locale.ROOT)
            .replace(Regex("[^\\p{L}\\p{N}\\s]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()

    fun isNoise(text: String): Boolean {
        val n = normalize(text)
        if (n.isEmpty()) return true
        if (n in acks) return true
        if (media.matches(n)) return true
        if (otp.containsMatchIn(n) && (n.contains("otp") || n.contains("code") || n.contains("verification"))) return true
        return false
    }

    fun greeting(text: String): String? {
        val n = normalize(text)
        return when {
            hiLike.matches(n) -> "hi"
            heyLike.matches(n) -> "hey"
            helloLike.matches(n) || n == "hlo" || n == "helo" || n == "hola" || n == "yo" || n == "oye" -> "hello"
            n == "namaste" -> "namaste"
            else -> null
        }
    }

    fun urgent(text: String, words: String): Boolean {
        val n = normalize(text)
        return words.split(",")
            .map { normalize(it) }
            .filter { it.isNotEmpty() }
            .any { n.contains(it) }
    }
}
