package com.lagradost.cloudstream3.ui.profiles

import android.content.Context
import com.lagradost.cloudstream3.CloudStreamApp.Companion.getKey
import com.lagradost.cloudstream3.CloudStreamApp.Companion.setKey
import com.lagradost.cloudstream3.R
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.ui.imdb.ImdbCard
import com.lagradost.cloudstream3.utils.DataStoreHelper

enum class ProfileKind { Standard, Kids, Family }

object ProfileFilter {
    private const val KIND_FOLDER = "ansh_profile_kind"
    private const val SEEDED_KEY = "ansh_profiles_seeded_v1"

    private val adultWords = Regex(
        "\\b(18\\+|adult|erotic|hot|uncut|nsfw|xxx|sexy|bold|ullu|kooku|primeplay|hunters|rabbit|xprime|fliz|besharams|neonx|tadka|nuefliks|cineprime|hothit|moodx|digimovieplex|unrated)\\b",
        RegexOption.IGNORE_CASE
    )
    private val kidsWords = Regex(
        "\\b(kids?|cartoons?|toons?|animation|animated|family|disney|pixar|pokemon|doraemon|shinchan|chhota|motu)\\b",
        RegexOption.IGNORE_CASE
    )
    private val kidsTypes = setOf(TvType.Cartoon)
    private val kidsGenres = setOf("animation", "family")
    private val unsafeGenres = setOf("horror", "crime", "thriller", "war")
    private val familyBlockedGenres = setOf("horror")

    fun kindOf(keyIndex: Int): ProfileKind =
        getKey<String>(KIND_FOLDER, keyIndex.toString())
            ?.let { runCatching { ProfileKind.valueOf(it) }.getOrNull() }
            ?: ProfileKind.Standard

    fun setKind(keyIndex: Int, kind: ProfileKind) = setKey(KIND_FOLDER, keyIndex.toString(), kind.name)

    fun current(): ProfileKind = kindOf(DataStoreHelper.selectedKeyIndex)

    fun seedProfiles(context: Context) {
        if (getKey<Boolean>(SEEDED_KEY, false) == true) return
        val existing = DataStoreHelper.accounts.toMutableList()
        val nextIndex = (existing.maxOfOrNull { it.keyIndex } ?: 0) + 1
        if (existing.none { it.keyIndex == 0 }) {
            existing.add(
                DataStoreHelper.Account(
                    keyIndex = 0,
                    name = context.getString(R.string.default_account),
                    defaultImageIndex = 2
                )
            )
        }
        val kids = DataStoreHelper.Account(
            keyIndex = nextIndex,
            name = context.getString(R.string.profile_kids),
            defaultImageIndex = 6
        )
        val family = DataStoreHelper.Account(
            keyIndex = nextIndex + 1,
            name = context.getString(R.string.profile_family),
            defaultImageIndex = 1
        )
        DataStoreHelper.accounts = (existing + kids + family).toTypedArray()
        setKind(kids.keyIndex, ProfileKind.Kids)
        setKind(family.keyIndex, ProfileKind.Family)
        setKey(SEEDED_KEY, true)
    }

    private fun genresOf(card: SearchResponse): Set<String> =
        (card as? ImdbCard)?.genres?.map { it.lowercase() }?.toSet() ?: emptySet()

    fun isAllowed(card: SearchResponse, rowName: String, kind: ProfileKind = current()): Boolean {
        if (card.type == TvType.NSFW) return false
        if (kind == ProfileKind.Standard) return true
        if (adultWords.containsMatchIn(card.name) || adultWords.containsMatchIn(rowName)) return false
        val genres = genresOf(card)
        return when (kind) {
            ProfileKind.Kids -> {
                if (genres.any { it in unsafeGenres }) return false
                card.type in kidsTypes ||
                    genres.any { it in kidsGenres } ||
                    kidsWords.containsMatchIn(rowName) ||
                    kidsWords.containsMatchIn(card.apiName) ||
                    kidsWords.containsMatchIn(card.name)
            }
            ProfileKind.Family -> genres.none { it in familyBlockedGenres }
            ProfileKind.Standard -> true
        }
    }

    fun filter(rowName: String, list: List<SearchResponse>): List<SearchResponse> {
        val kind = current()
        if (kind == ProfileKind.Standard) return list.filter { it.type != TvType.NSFW }
        return list.filter { isAllowed(it, rowName, kind) }
    }
}
