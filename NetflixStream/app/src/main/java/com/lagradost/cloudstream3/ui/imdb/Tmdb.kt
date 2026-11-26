package com.lagradost.cloudstream3.ui.imdb

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.Score
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.mvvm.logError
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object Tmdb {
    private const val BASE = "https://api.themoviedb.org/3/"
    private const val KEY = "e6333b32409e02a4a6eba6fb7ff866bb"
    private const val IMAGE = "https://image.tmdb.org/t/p/"
    private const val CACHE_MS = 30 * 60 * 1000L

    private val genreNames = mapOf(
        28 to "Action", 12 to "Adventure", 16 to "Animation", 35 to "Comedy", 80 to "Crime",
        99 to "Documentary", 18 to "Drama", 10751 to "Family", 14 to "Fantasy", 36 to "History",
        27 to "Horror", 10402 to "Music", 9648 to "Mystery", 10749 to "Romance", 878 to "Sci-Fi",
        53 to "Thriller", 10752 to "War", 37 to "Western", 10759 to "Action", 10762 to "Kids",
        10765 to "Sci-Fi", 10768 to "War", 10764 to "Reality", 10766 to "Drama"
    )

    @JsonIgnoreProperties(ignoreUnknown = true)
    data class Item(
        @JsonProperty("id") val id: Int? = null,
        @JsonProperty("title") val title: String? = null,
        @JsonProperty("name") val name: String? = null,
        @JsonProperty("poster_path") val poster: String? = null,
        @JsonProperty("backdrop_path") val backdrop: String? = null,
        @JsonProperty("overview") val overview: String? = null,
        @JsonProperty("release_date") val releaseDate: String? = null,
        @JsonProperty("first_air_date") val firstAirDate: String? = null,
        @JsonProperty("vote_average") val vote: Double? = null,
        @JsonProperty("genre_ids") val genreIds: List<Int>? = null,
        @JsonProperty("media_type") val mediaType: String? = null,
        @JsonProperty("adult") val adult: Boolean? = null,
    )

    @JsonIgnoreProperties(ignoreUnknown = true)
    data class Page(@JsonProperty("results") val results: List<Item>? = null)

    private val cache = HashMap<String, Pair<Long, List<ImdbCard>>>()

    private fun Item.toCard(defaultType: String): ImdbCard? {
        if (adult == true) return null
        val tmdbId = id ?: return null
        val label = (title ?: name)?.takeIf { it.isNotBlank() } ?: return null
        val kind = mediaType ?: defaultType
        if (kind != "movie" && kind != "tv") return null
        return ImdbCard(
            name = label,
            url = "tmdb://$kind/$tmdbId",
            type = if (kind == "tv") TvType.TvSeries else TvType.Movie,
            posterUrl = poster?.let { IMAGE + "w342" + it },
            score = vote?.takeIf { it > 0 }?.let { Score.from(it, 10) },
            id = "tmdb$kind$tmdbId".hashCode(),
            imdbId = "tmdb:$kind:$tmdbId",
            year = (releaseDate ?: firstAirDate)?.take(4)?.toIntOrNull(),
            genres = genreIds.orEmpty().mapNotNull { genreNames[it] }.distinct(),
            background = backdrop?.let { IMAGE + "w1280" + it },
            logo = null,
            plot = overview,
        )
    }

    private suspend fun fetch(path: String, defaultType: String): List<ImdbCard> {
        synchronized(cache) {
            cache[path]?.let { (time, list) ->
                if (System.currentTimeMillis() - time < CACHE_MS) return list
            }
        }
        val glue = if (path.contains('?')) "&" else "?"
        val cards = try {
            app.get("$BASE$path${glue}api_key=$KEY&include_adult=false")
                .parsedSafe<Page>()?.results.orEmpty().mapNotNull { it.toCard(defaultType) }
        } catch (t: Throwable) {
            logError(t)
            emptyList()
        }
        if (cards.isNotEmpty()) synchronized(cache) { cache[path] = System.currentTimeMillis() to cards }
        return cards
    }

    private suspend fun pages(path: String, type: String, count: Int): List<ImdbCard> =
        withContext(Dispatchers.IO) {
            coroutineScope {
                (1..((count + 19) / 20)).map { page ->
                    val glue = if (path.contains('?')) "&" else "?"
                    async { fetch("$path${glue}page=$page", type) }
                }.awaitAll().flatten().distinctBy { it.url }.take(count)
            }
        }

    private fun today(): String = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())

    suspend fun trendingIndia(count: Int = 40) =
        pages("discover/movie?region=IN&with_origin_country=IN&sort_by=popularity.desc", "movie", count)

    suspend fun newHindi(count: Int = 40) =
        pages("discover/movie?with_original_language=hi&sort_by=primary_release_date.desc&primary_release_date.lte=${today()}&vote_count.gte=3", "movie", count)

    suspend fun hindiSeries(count: Int = 40) =
        pages("discover/tv?with_original_language=hi&sort_by=popularity.desc", "tv", count)

    suspend fun southHits(count: Int = 40) =
        pages("discover/movie?with_original_language=te|ta|ml|kn&sort_by=popularity.desc&region=IN", "movie", count)

    suspend fun topHindi(count: Int = 100) =
        pages("discover/movie?with_original_language=hi&sort_by=vote_average.desc&vote_count.gte=300", "movie", count)

    suspend fun hollywood(count: Int = 40) =
        pages("discover/movie?with_original_language=en&sort_by=popularity.desc", "movie", count)

    suspend fun koreanDrama(count: Int = 40) =
        pages("discover/tv?with_original_language=ko&sort_by=popularity.desc", "tv", count)

    suspend fun trendingWeek(count: Int = 40) = pages("trending/all/week", "movie", count)

    suspend fun search(query: String): List<ImdbCard> =
        fetch("search/multi?query=" + java.net.URLEncoder.encode(query.trim(), "UTF-8"), "movie")
}
