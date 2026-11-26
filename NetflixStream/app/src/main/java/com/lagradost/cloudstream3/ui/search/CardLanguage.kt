package com.lagradost.cloudstream3.ui.search

import com.lagradost.cloudstream3.APIHolder.getApiFromNameNull
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.utils.SubtitleHelper

object CardLanguage {
    private val titleKeywords = linkedMapOf(
        "hindi" to "hi",
        "tamil" to "ta",
        "telugu" to "te",
        "malayalam" to "ml",
        "kannada" to "kn",
        "bengali" to "bn",
        "bangla" to "bn",
        "punjabi" to "pa",
        "marathi" to "mr",
        "gujarati" to "gu",
        "urdu" to "ur",
        "english" to "en",
        "korean" to "ko",
        "japanese" to "ja",
        "chinese" to "zh",
        "spanish" to "es",
        "french" to "fr",
        "german" to "de",
        "russian" to "ru",
        "turkish" to "tr",
        "thai" to "th",
        "indonesian" to "id",
        "arabic" to "ar",
        "portuguese" to "pt"
    )

    private val keywordRegex = Regex(
        "\\b(${titleKeywords.keys.joinToString("|")})\\b",
        RegexOption.IGNORE_CASE
    )
    private val dualAudioRegex = Regex("dual[\\s._-]*audio|multi[\\s._-]*audio", RegexOption.IGNORE_CASE)
    private val sharedFlagLanguages = setOf("hi", "ta", "te", "ml", "kn", "bn", "pa", "mr", "gu", "ur")

    private val cache = HashMap<String, String?>()

    fun detect(card: SearchResponse): String? {
        val key = card.apiName + "|" + card.name
        synchronized(cache) {
            if (cache.containsKey(key)) return cache[key]
        }
        val result = compute(card)
        synchronized(cache) { cache[key] = result }
        return result
    }

    private fun compute(card: SearchResponse): String? {
        val found = keywordRegex.findAll(card.name)
            .mapNotNull { titleKeywords[it.value.lowercase()] }
            .distinct()
            .toMutableList()

        if (found.isEmpty() && dualAudioRegex.containsMatchIn(card.name)) {
            found.add("hi")
            found.add("en")
        }

        if (found.isEmpty()) {
            val providerLang = getApiFromNameNull(card.apiName)?.lang
            if (!providerLang.isNullOrBlank() && !providerLang.equals("en", true)) {
                found.add(providerLang.substringBefore('-').lowercase())
            }
        }

        val labels = found.take(2).mapNotNull { iso ->
            val flag = SubtitleHelper.getFlagFromIso(iso) ?: return@mapNotNull null
            if (iso in sharedFlagLanguages) "$flag ${iso.uppercase()}" else flag
        }
        return labels.joinToString("  ").ifBlank { null }
    }
}
