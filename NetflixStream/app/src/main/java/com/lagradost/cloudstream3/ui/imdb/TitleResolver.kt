package com.lagradost.cloudstream3.ui.imdb

import android.app.Activity
import android.widget.Toast
import com.lagradost.cloudstream3.CommonActivity.showToast
import com.lagradost.cloudstream3.R
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.isMovieType
import com.lagradost.cloudstream3.mvvm.Resource
import com.lagradost.cloudstream3.ui.APIRepository
import com.lagradost.cloudstream3.ui.search.TitleCleaner
import com.lagradost.cloudstream3.utils.AppContextUtils.filterProviderByPreferredMedia
import com.lagradost.cloudstream3.utils.AppContextUtils.loadSearchResult
import com.lagradost.cloudstream3.utils.Coroutines.ioSafe
import com.lagradost.cloudstream3.utils.Coroutines.main
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withTimeoutOrNull

object TitleResolver {
    private val nonWord = Regex("[^a-z0-9 ]")
    private val spaces = Regex("\\s+")
    private val yearRegex = Regex("(19|20)\\d{2}")

    fun normalize(title: String): String =
        TitleCleaner.clean(title).lowercase()
            .replace("&", " and ")
            .replace(nonWord, " ")
            .replace(spaces, " ")
            .trim()
            .removePrefix("the ")

    private fun matchScore(target: ImdbCard, candidate: SearchResponse): Int {
        val want = normalize(target.name)
        val got = normalize(candidate.name)
        if (want.isBlank() || got.isBlank()) return 0
        var points = when {
            got == want -> 10
            got.startsWith("$want ") || want.startsWith("$got ") -> 6
            got.contains(want) -> 4
            else -> 0
        }
        if (points == 0) return 0
        val candidateYear = yearRegex.find(candidate.name)?.value?.toIntOrNull()
        if (target.year != null && candidateYear != null) {
            points += if (kotlin.math.abs(target.year - candidateYear) <= 1) 3 else -4
        }
        val wantMovie = target.type == TvType.Movie
        val gotMovie = candidate.type?.isMovieType()
        if (gotMovie != null) points += if (gotMovie == wantMovie) 2 else -3
        return points
    }

    suspend fun findMatches(activity: Activity, card: ImdbCard): List<SearchResponse> {
        val apis = activity.filterProviderByPreferredMedia(hasHomePageIsRequired = false)
        val query = card.name
        val found = coroutineScope {
            apis.map { api ->
                async {
                    withTimeoutOrNull(12_000L) {
                        (APIRepository(api).search(query, 1) as? Resource.Success)?.value?.items
                    }.orEmpty()
                }
            }.awaitAll().flatten()
        }
        return found
            .map { it to matchScore(card, it) }
            .filter { it.second >= 4 }
            .sortedByDescending { it.second }
            .map { it.first }
    }

    fun open(activity: Activity?, card: ImdbCard) {
        activity ?: return
        showToast(activity.getString(R.string.finding_streams, card.name), Toast.LENGTH_SHORT)
        ioSafe {
            val best = findMatches(activity, card).firstOrNull()
            main {
                if (best == null) {
                    showToast(activity.getString(R.string.not_on_extensions, card.name), Toast.LENGTH_LONG)
                } else {
                    activity.loadSearchResult(best)
                }
            }
        }
    }
}
