package com.lagradost.cloudstream3.ui.foryou

import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.CloudStreamApp.Companion.getKey
import com.lagradost.cloudstream3.CloudStreamApp.Companion.setKey
import com.lagradost.cloudstream3.LoadResponse
import com.lagradost.cloudstream3.Score
import com.lagradost.cloudstream3.SearchQuality
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.ui.search.CardLanguage
import com.lagradost.cloudstream3.utils.DataStoreHelper
import kotlin.math.exp
import kotlin.math.ln

data class TasteEvent(
    @JsonProperty("parentId") val parentId: Int,
    @JsonProperty("name") val name: String,
    @JsonProperty("url") val url: String,
    @JsonProperty("apiName") val apiName: String,
    @JsonProperty("poster") val poster: String?,
    @JsonProperty("type") val type: String?,
    @JsonProperty("tags") val tags: List<String>,
    @JsonProperty("languages") val languages: List<String>,
    @JsonProperty("time") val time: Long,
)

data class TasteCard(
    override val name: String,
    override val url: String,
    override val apiName: String,
    override var type: TvType?,
    override var posterUrl: String?,
    override var posterHeaders: Map<String, String>? = null,
    override var id: Int? = null,
    override var quality: SearchQuality? = null,
    override var score: Score? = null,
) : SearchResponse

object TasteEngine {
    private const val EVENTS_KEY = "ansh_taste_events"
    private const val MAX_EVENTS = 400
    private const val DAY_MS = 86_400_000L
    private const val HALF_LIFE_DAYS = 14.0

    private val lock = Any()

    private fun events(): List<TasteEvent> =
        getKey<Array<TasteEvent>>(EVENTS_KEY)?.toList() ?: emptyList()

    fun record(response: LoadResponse, parentId: Int?) {
        if (parentId == null || DataStoreHelper.isIncognito) return
        if (response.type == TvType.NSFW) return
        val probe = TasteCard(response.name, response.url, response.apiName, response.type, response.posterUrl)
        val event = TasteEvent(
            parentId = parentId,
            name = response.name,
            url = response.url,
            apiName = response.apiName,
            poster = response.posterUrl,
            type = response.type.name,
            tags = response.tags.orEmpty().map { it.trim().lowercase() }.filter { it.isNotBlank() }.take(8),
            languages = CardLanguage.codes(probe),
            time = System.currentTimeMillis()
        )
        synchronized(lock) {
            val updated = (events() + event).takeLast(MAX_EVENTS)
            setKey(EVENTS_KEY, updated.toTypedArray())
        }
    }

    fun clear() = synchronized(lock) { setKey(EVENTS_KEY, emptyArray<TasteEvent>()) }

    fun hasHistory(): Boolean = events().isNotEmpty()

    fun topWatched(windowDays: Int, limit: Int = 10): List<SearchResponse> {
        val since = System.currentTimeMillis() - windowDays * DAY_MS
        return events()
            .filter { it.time >= since }
            .groupBy { it.parentId }
            .map { (_, list) -> list.maxBy { it.time } to list.size }
            .sortedWith(compareByDescending<Pair<TasteEvent, Int>> { it.second }.thenByDescending { it.first.time })
            .take(limit)
            .map { (event, _) -> event.toCard() }
    }

    fun watchedUrls(): Set<String> = events().map { it.url }.toSet()

    private fun decay(time: Long, now: Long): Double {
        val days = (now - time).coerceAtLeast(0L) / DAY_MS.toDouble()
        return exp(-ln(2.0) * days / HALF_LIFE_DAYS)
    }

    private fun weights(select: (TasteEvent) -> List<String>): Map<String, Double> {
        val now = System.currentTimeMillis()
        val totals = HashMap<String, Double>()
        events().forEach { event ->
            val w = decay(event.time, now)
            select(event).forEach { key -> totals[key] = (totals[key] ?: 0.0) + w }
        }
        val max = totals.values.maxOrNull() ?: return emptyMap()
        return totals.mapValues { it.value / max }
    }

    fun favouriteGenres(limit: Int = 5): List<String> =
        weights { it.tags }.entries.sortedByDescending { it.value }.take(limit).map { it.key }

    fun rank(
        candidates: List<SearchResponse>,
        recommendedBy: Map<String, Double> = emptyMap(),
        limit: Int = 30
    ): List<SearchResponse> {
        if (candidates.isEmpty()) return emptyList()
        val typeWeights = weights { listOfNotNull(it.type) }
        val langWeights = weights { it.languages }
        val seen = watchedUrls()

        return candidates
            .asSequence()
            .filter { it.url !in seen && it.type != TvType.NSFW }
            .distinctBy { it.url }
            .map { card ->
                var points = (recommendedBy[card.url] ?: 0.0) * 3.0
                points += (card.type?.name?.let { typeWeights[it] } ?: 0.0) * 1.5
                points += CardLanguage.codes(card).maxOfOrNull { langWeights[it] ?: 0.0 } ?: 0.0
                points += (card.score?.toDouble(10) ?: 0.0) / 10.0
                card to points
            }
            .filter { it.second > 0.0 }
            .sortedByDescending { it.second }
            .take(limit)
            .map { it.first }
            .toList()
    }

    private fun TasteEvent.toCard(): SearchResponse = TasteCard(
        name = name,
        url = url,
        apiName = apiName,
        type = type?.let { runCatching { TvType.valueOf(it) }.getOrNull() },
        posterUrl = poster,
        id = parentId
    )
}
