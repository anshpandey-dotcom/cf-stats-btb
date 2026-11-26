package com.lagradost.cloudstream3.ui.home

import android.content.Context
import com.lagradost.cloudstream3.HomePageList
import com.lagradost.cloudstream3.LoadResponse
import com.lagradost.cloudstream3.R
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.mvvm.logError
import com.lagradost.cloudstream3.newMovieLoadResponse
import com.lagradost.cloudstream3.ui.APIRepository.Companion.noneApi
import com.lagradost.cloudstream3.ui.imdb.Cinemeta
import com.lagradost.cloudstream3.ui.imdb.IMDB_API_NAME
import com.lagradost.cloudstream3.ui.imdb.ImdbCard
import com.lagradost.cloudstream3.ui.imdb.TitleResolver
import com.lagradost.cloudstream3.ui.imdb.Tmdb
import com.lagradost.cloudstream3.ui.profiles.ProfileFilter
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

data class MixResult(
    val rows: List<HomePageList>,
    val hero: List<LoadResponse>,
)

object MixFeed {
    private val heroCards = HashMap<String, ImdbCard>()

    fun heroCard(url: String): ImdbCard? = synchronized(heroCards) { heroCards[url] }

    private fun mergeRows(rows: List<Pair<String, List<SearchResponse>>>): List<HomePageList> {
        val merged = LinkedHashMap<String, Pair<String, MutableList<SearchResponse>>>()
        rows.forEach { (name, list) ->
            val key = TitleResolver.normalize(name)
            if (key.isBlank()) return@forEach
            val slot = merged.getOrPut(key) { name to mutableListOf() }
            slot.second.addAll(list)
        }
        return merged.values.map { (name, list) ->
            val unique = list.distinctBy { TitleResolver.normalize(it.name).ifBlank { it.url } }
            HomePageList(name, unique.take(40), false)
        }.filter { it.list.size >= 3 }
    }

    private fun interleave(a: List<SearchResponse>, b: List<SearchResponse>): List<SearchResponse> {
        val out = ArrayList<SearchResponse>(a.size + b.size)
        val max = maxOf(a.size, b.size)
        for (i in 0 until max) {
            a.getOrNull(i)?.let { out.add(it) }
            b.getOrNull(i)?.let { out.add(it) }
        }
        return out
    }

    suspend fun build(context: Context): MixResult = coroutineScope {
        val providerRows = async { runCatching { FeedRows.rows(context) }.getOrElse { logError(it); emptyList() } }
        val trendingMovies = async { Cinemeta.list("top", "movie", count = 50) }
        val trendingSeries = async { Cinemeta.list("top", "series", count = 50) }
        val topMovies = async { Cinemeta.list("imdbRating", "movie", count = 50) }
        val topSeries = async { Cinemeta.list("imdbRating", "series", count = 50) }
        val india = async { Tmdb.trendingIndia() }
        val newHindi = async { Tmdb.newHindi() }
        val hindiSeries = async { Tmdb.hindiSeries() }
        val south = async { Tmdb.southHits() }

        val movies = trendingMovies.await()
        val series = trendingSeries.await()
        val trending = interleave(movies, series)

        val rows = ArrayList<HomePageList>()
        val indiaList = india.await()
        fun addRow(title: Int, list: List<SearchResponse>) {
            if (list.size >= 3) rows.add(HomePageList(context.getString(title), list.take(40), false))
        }
        addRow(R.string.mix_india, indiaList)
        addRow(R.string.mix_new_hindi, newHindi.await())
        addRow(R.string.mix_trending, trending)
        val extension = mergeRows(providerRows.await()).shuffled()
        rows.addAll(extension.take(3))
        addRow(R.string.mix_hindi_series, hindiSeries.await())
        addRow(R.string.mix_south, south.await())
        rows.addAll(extension.drop(3).take(2))
        topMovies.await().takeIf { it.isNotEmpty() }?.let {
            rows.add(HomePageList(context.getString(R.string.mix_imdb_movies), it.take(40), false))
        }
        rows.addAll(extension.drop(4).take(6))
        series.takeIf { it.isNotEmpty() }?.let {
            rows.add(HomePageList(context.getString(R.string.mix_series), it.take(40), false))
        }
        topSeries.await().takeIf { it.isNotEmpty() }?.let {
            rows.add(HomePageList(context.getString(R.string.mix_imdb_series), it.take(40), false))
        }
        rows.addAll(extension.drop(10).take(20))

        val filtered = rows.mapNotNull { row ->
            val safe = ProfileFilter.filter(row.name, row.list)
            if (safe.isEmpty()) null else row.copy(list = safe)
        }

        val heroPool = ProfileFilter.filter("", (indiaList.take(10) + trending.filterIsInstance<ImdbCard>()).shuffled())
            .filterIsInstance<ImdbCard>()
            .filter { it.background != null && !it.plot.isNullOrBlank() }
            .take(8)
            .shuffled()
            .take(5)
        val hero = heroPool.mapNotNull { card ->
            runCatching {
                synchronized(heroCards) { heroCards[card.url] = card }
                noneApi.newMovieLoadResponse(card.name, card.url, card.type ?: com.lagradost.cloudstream3.TvType.Movie, card.url) {
                    apiName = IMDB_API_NAME
                    posterUrl = card.background ?: card.posterUrl
                    logoUrl = card.logo
                    plot = card.plot
                    tags = card.genres
                    year = card.year
                    score = card.score
                }
            }.getOrNull()
        }
        MixResult(filtered, hero)
    }
}
