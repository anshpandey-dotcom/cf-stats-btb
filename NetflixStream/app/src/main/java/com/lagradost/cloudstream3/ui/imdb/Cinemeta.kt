package com.lagradost.cloudstream3.ui.imdb

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.Score
import com.lagradost.cloudstream3.SearchQuality
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.mvvm.logError
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import java.net.URLEncoder

const val IMDB_API_NAME = "IMDb"

data class ImdbCard(
    override val name: String,
    override val url: String,
    override val apiName: String = IMDB_API_NAME,
    override var type: TvType?,
    override var posterUrl: String?,
    override var posterHeaders: Map<String, String>? = null,
    override var id: Int? = null,
    override var quality: SearchQuality? = null,
    override var score: Score? = null,
    val imdbId: String,
    val year: Int?,
    val genres: List<String>,
    val background: String?,
    val logo: String?,
    val plot: String?,
) : SearchResponse

object Cinemeta {
    private const val CATALOGS = "https://cinemeta-catalogs.strem.io"
    private const val SEARCH = "https://v3-cinemeta.strem.io"
    private const val CACHE_MS = 30 * 60 * 1000L

    @JsonIgnoreProperties(ignoreUnknown = true)
    data class Meta(
        @JsonProperty("id") val id: String? = null,
        @JsonProperty("type") val type: String? = null,
        @JsonProperty("name") val name: String? = null,
        @JsonProperty("poster") val poster: String? = null,
        @JsonProperty("background") val background: String? = null,
        @JsonProperty("logo") val logo: String? = null,
        @JsonProperty("description") val description: String? = null,
        @JsonProperty("releaseInfo") val releaseInfo: String? = null,
        @JsonProperty("year") val year: String? = null,
        @JsonProperty("imdbRating") val imdbRating: String? = null,
        @JsonProperty("genres") val genres: List<String>? = null,
    )

    @JsonIgnoreProperties(ignoreUnknown = true)
    data class Catalog(@JsonProperty("metas") val metas: List<Meta>? = null)

    private val cache = HashMap<String, Pair<Long, List<ImdbCard>>>()

    private fun Meta.toCard(): ImdbCard? {
        val imdb = id?.takeIf { it.startsWith("tt") } ?: return null
        val title = name?.takeIf { it.isNotBlank() } ?: return null
        val rating = imdbRating?.toDoubleOrNull()
        return ImdbCard(
            name = title,
            url = "imdb://$imdb",
            type = if (type == "series") TvType.TvSeries else TvType.Movie,
            posterUrl = poster?.replace("/poster/small/", "/poster/medium/"),
            score = Score.from(rating, 10),
            id = imdb.hashCode(),
            imdbId = imdb,
            year = (year ?: releaseInfo)?.take(4)?.toIntOrNull(),
            genres = genres.orEmpty(),
            background = background,
            logo = logo,
            plot = description,
        )
    }

    private suspend fun fetch(url: String): List<ImdbCard> {
        synchronized(cache) {
            cache[url]?.let { (time, list) ->
                if (System.currentTimeMillis() - time < CACHE_MS) return list
            }
        }
        val cards = try {
            app.get(url).parsedSafe<Catalog>()?.metas.orEmpty().mapNotNull { it.toCard() }
        } catch (t: Throwable) {
            logError(t)
            emptyList()
        }
        if (cards.isNotEmpty()) synchronized(cache) { cache[url] = System.currentTimeMillis() to cards }
        return cards
    }

    private fun catalogUrl(catalog: String, type: String, genre: String?, skip: Int): String {
        val extras = listOfNotNull(
            genre?.let { "genre=" + URLEncoder.encode(it, "UTF-8") },
            skip.takeIf { it > 0 }?.let { "skip=$it" }
        )
        val suffix = if (extras.isEmpty()) "" else "/" + extras.joinToString("&")
        return "$CATALOGS/$catalog/catalog/$type/$catalog$suffix.json"
    }

    suspend fun list(
        catalog: String,
        type: String,
        genre: String? = null,
        count: Int = 100
    ): List<ImdbCard> = withContext(Dispatchers.IO) {
        coroutineScope {
            (0 until count step 50).map { skip ->
                async { fetch(catalogUrl(catalog, type, genre, skip)) }
            }.awaitAll().flatten().distinctBy { it.imdbId }.take(count)
        }
    }

    suspend fun search(query: String): List<ImdbCard> = withContext(Dispatchers.IO) {
        val encoded = URLEncoder.encode(query.trim(), "UTF-8")
        coroutineScope {
            listOf("series", "movie").map { type ->
                async { fetch("$SEARCH/catalog/$type/top/search=$encoded.json") }
            }.awaitAll().flatten()
        }
    }
}
