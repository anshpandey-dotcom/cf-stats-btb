package com.lagradost.cloudstream3.ui.imdb

import android.app.Activity
import android.content.Context
import android.widget.Toast
import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.CloudStreamApp.Companion.getKey
import com.lagradost.cloudstream3.CloudStreamApp.Companion.setKey
import com.lagradost.cloudstream3.CommonActivity.showToast
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.R
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.isMovieType
import com.lagradost.cloudstream3.mvvm.Resource
import com.lagradost.cloudstream3.mvvm.logError
import com.lagradost.cloudstream3.ui.APIRepository
import com.lagradost.cloudstream3.ui.foryou.TasteCard
import com.lagradost.cloudstream3.ui.search.TitleCleaner
import com.lagradost.cloudstream3.utils.AppContextUtils.filterProviderByPreferredMedia
import com.lagradost.cloudstream3.utils.AppContextUtils.loadSearchResult
import com.lagradost.cloudstream3.utils.Coroutines.ioSafe
import com.lagradost.cloudstream3.utils.Coroutines.main
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.ConcurrentHashMap

data class ResolvedTitle(
    @JsonProperty("name") val name: String,
    @JsonProperty("url") val url: String,
    @JsonProperty("apiName") val apiName: String,
    @JsonProperty("type") val type: String?,
    @JsonProperty("poster") val poster: String?,
)

object TitleResolver {
    private const val CACHE_FOLDER = "ansh_resolved"
    private const val WINS_FOLDER = "ansh_provider_wins"
    private const val PREFETCH_PROVIDERS = 16
    private val nonWord = Regex("[^a-z0-9 ]")
    private val spaces = Regex("\\s+")
    private val yearRegex = Regex("(19|20)\\d{2}")
    private val memory = ConcurrentHashMap<String, SearchResponse>()
    private val inFlight = ConcurrentHashMap.newKeySet<String>()
    private val prefetchGate = Semaphore(2)

    fun normalize(title: String): String =
        TitleCleaner.clean(title).lowercase()
            .replace("&", " and ")
            .replace(nonWord, " ")
            .replace(spaces, " ")
            .trim()
            .removePrefix("the ")

    private fun score(name: String, year: Int?, isMovie: Boolean?, candidate: SearchResponse): Int {
        val want = normalize(name)
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
        if (year != null && candidateYear != null) {
            points += if (kotlin.math.abs(year - candidateYear) <= 1) 3 else -4
        }
        val gotMovie = candidate.type?.isMovieType()
        if (isMovie != null && gotMovie != null) points += if (gotMovie == isMovie) 2 else -3
        return points
    }

    private fun winsOf(api: String): Int = getKey<Int>(WINS_FOLDER, api) ?: 0

    private fun recordWin(api: String) = setKey(WINS_FOLDER, api, winsOf(api) + 1)

    private fun rankedApis(context: Context, exclude: Set<String>): List<MainAPI> =
        context.filterProviderByPreferredMedia(hasHomePageIsRequired = false)
            .filter { it.name !in exclude }
            .sortedWith(
                compareByDescending<MainAPI> { winsOf(it.name) }
                    .thenByDescending { it.lang == "hi" }
            )

    private suspend fun search(
        apis: List<MainAPI>,
        name: String,
        year: Int?,
        isMovie: Boolean?,
    ): SearchResponse? = coroutineScope {
        val exact = CompletableDeferred<SearchResponse?>()
        val found = ArrayList<Pair<SearchResponse, Int>>()
        val jobs = apis.map { api ->
            launch(Dispatchers.IO) {
                val items = withTimeoutOrNull(12_000L) {
                    (APIRepository(api).search(name, 1) as? Resource.Success)?.value?.items
                }.orEmpty()
                val scored = items.map { it to score(name, year, isMovie, it) }.filter { it.second >= 4 }
                synchronized(found) { found.addAll(scored) }
                scored.firstOrNull { it.second >= 12 }?.let { exact.complete(it.first) }
            }
        }
        launch {
            jobs.forEach { it.join() }
            exact.complete(null)
        }
        val quick = exact.await()
        jobs.forEach { it.cancel() }
        quick ?: synchronized(found) { found.maxByOrNull { it.second }?.first }
    }

    private fun cacheKey(card: ImdbCard) = card.url

    private fun remember(card: ImdbCard, match: SearchResponse) {
        memory[cacheKey(card)] = match
        setKey(
            CACHE_FOLDER,
            cacheKey(card).hashCode().toString(),
            ResolvedTitle(match.name, match.url, match.apiName, match.type?.name, match.posterUrl)
        )
        recordWin(match.apiName)
    }

    private fun cached(card: ImdbCard): SearchResponse? {
        memory[cacheKey(card)]?.let { return it }
        val stored = getKey<ResolvedTitle>(CACHE_FOLDER, cacheKey(card).hashCode().toString()) ?: return null
        val restored = TasteCard(
            name = stored.name,
            url = stored.url,
            apiName = stored.apiName,
            type = stored.type?.let { runCatching { TvType.valueOf(it) }.getOrNull() },
            posterUrl = stored.poster
        )
        memory[cacheKey(card)] = restored
        return restored
    }

    suspend fun resolve(context: Context, card: ImdbCard, providerLimit: Int? = null): SearchResponse? {
        cached(card)?.let { return it }
        val apis = rankedApis(context, emptySet()).let { list ->
            if (providerLimit != null) list.take(providerLimit) else list
        }
        val match = search(apis, card.name, card.year, card.type == TvType.Movie) ?: return null
        remember(card, match)
        return match
    }

    fun prefetch(context: Context, cards: List<SearchResponse>) {
        val targets = cards.filterIsInstance<ImdbCard>()
            .filter { cached(it) == null && inFlight.add(cacheKey(it)) }
        if (targets.isEmpty()) return
        ioSafe {
            coroutineScope {
                targets.map { card ->
                    async {
                        prefetchGate.withPermit {
                            try {
                                resolve(context, card, PREFETCH_PROVIDERS)
                            } catch (t: Throwable) {
                                logError(t)
                            } finally {
                                inFlight.remove(cacheKey(card))
                            }
                        }
                    }
                }.awaitAll()
            }
        }
    }

    fun open(activity: Activity?, card: ImdbCard) {
        activity ?: return
        cached(card)?.let {
            activity.loadSearchResult(it)
            return
        }
        ioSafe {
            val best = try {
                resolve(activity, card)
            } catch (t: Throwable) {
                logError(t)
                null
            }
            main {
                if (best == null) {
                    showToast(activity.getString(R.string.not_on_extensions, card.name), Toast.LENGTH_SHORT)
                } else {
                    activity.loadSearchResult(best)
                }
            }
        }
    }

    suspend fun alternative(
        context: Context,
        name: String,
        year: Int?,
        isMovie: Boolean,
        exclude: Set<String>,
    ): SearchResponse? = withContext(Dispatchers.IO) {
        search(rankedApis(context, exclude), TitleCleaner.clean(name), year, isMovie)
    }
}
