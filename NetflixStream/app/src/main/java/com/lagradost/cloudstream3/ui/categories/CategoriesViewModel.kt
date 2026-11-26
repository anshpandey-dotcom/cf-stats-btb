package com.lagradost.cloudstream3.ui.categories

import com.lagradost.cloudstream3.ui.collections.CollectionStore
import android.content.Context
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.mvvm.Resource
import com.lagradost.cloudstream3.mvvm.launchSafe
import com.lagradost.cloudstream3.mvvm.logError
import com.lagradost.cloudstream3.ui.APIRepository
import com.lagradost.cloudstream3.ui.foryou.TasteEngine
import com.lagradost.cloudstream3.ui.home.FeedRows
import com.lagradost.cloudstream3.ui.home.HomeViewModel
import com.lagradost.cloudstream3.ui.imdb.Cinemeta
import com.lagradost.cloudstream3.ui.imdb.Tmdb
import com.lagradost.cloudstream3.ui.imdb.TitleResolver
import com.lagradost.cloudstream3.ui.profiles.ProfileFilter
import com.lagradost.cloudstream3.ui.search.CardLanguage
import com.lagradost.cloudstream3.utils.AppContextUtils.filterProviderByPreferredMedia
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

enum class TypeFilter { All, Movies, Series, Anime }

data class CategoryResult(
    val category: Category,
    val items: List<SearchResponse>,
    val languages: List<String>,
)

class CategoriesViewModel : ViewModel() {
    companion object {
        private val movieTypes = setOf(TvType.Movie, TvType.AnimeMovie, TvType.Documentary)
        private val seriesTypes = setOf(TvType.TvSeries, TvType.AsianDrama, TvType.Cartoon, TvType.Anime, TvType.OVA)
        private val animeTypes = setOf(TvType.Anime, TvType.AnimeMovie, TvType.OVA)
    }

    private val _covers = MutableLiveData<Map<String, String>>(emptyMap())
    val covers: LiveData<Map<String, String>> = _covers

    private val _result = MutableLiveData<Resource<CategoryResult>?>(null)
    val result: LiveData<Resource<CategoryResult>?> = _result

    var selected: Category? = null
        private set
    var typeFilter = TypeFilter.All
    var langFilter: String? = null
    private var unfiltered: List<SearchResponse> = emptyList()

    private suspend fun rows(context: Context): List<Pair<String, List<SearchResponse>>> =
        FeedRows.rows(context)

    private fun matchRows(
        rows: List<Pair<String, List<SearchResponse>>>,
        category: Category
    ): List<SearchResponse> = rows
        .filter { (name, _) -> category.rowKeywords.any { name.contains(it, ignoreCase = true) } }
        .flatMap { it.second }
        .distinctBy { it.url }

    fun loadCovers(context: Context) = viewModelScope.launchSafe {
        val rows = rows(context)
        val covers = HashMap<String, String>()
        val everything = rows.flatMap { it.second }
        CategoryCatalog.withCollections().forEach { category ->
            val pick = when (category.source) {
                CategorySource.Rows -> matchRows(rows, category)
                CategorySource.ForYou -> TasteEngine.rank(everything, limit = 3)
                CategorySource.TopWeek -> personalTop(7).take(1)
                CategorySource.TopMonth -> personalTop(30).take(1)
                CategorySource.Imdb -> imdbItems(category, 50).take(1)
                CategorySource.Collection -> CollectionStore.cards(category.label.orEmpty()).take(1)
                CategorySource.TopRated -> everything.sortedByDescending { it.score?.toDouble(10) ?: 0.0 }.take(1)
            }.firstNotNullOfOrNull { it.posterUrl }
            if (pick != null) covers[category.key] = pick
        }
        _covers.postValue(covers)
    }

    fun open(context: Context, category: Category) = viewModelScope.launchSafe {
        selected = category
        typeFilter = TypeFilter.All
        langFilter = null
        _result.postValue(Resource.Loading())
        val items = try {
            val rows = rows(context)
            val everything = rows.flatMap { it.second }.distinctBy { it.url }
            when (category.source) {
                CategorySource.ForYou -> TasteEngine.rank(everything, limit = 60)
                CategorySource.TopWeek -> personalTop(7)
                CategorySource.TopMonth -> personalTop(30)
                CategorySource.Imdb -> imdbItems(category, 100)
                CategorySource.Collection -> CollectionStore.cards(category.label.orEmpty())
                CategorySource.TopRated -> everything
                    .filter { (it.score?.toDouble(10) ?: 0.0) > 0.0 }
                    .sortedByDescending { it.score?.toDouble(10) ?: 0.0 }
                    .take(50)
                CategorySource.Rows -> {
                    val fromRows = matchRows(rows, category)
                    val withSearch = if (fromRows.size >= 24 || category.searchQuery == null) fromRows
                    else (fromRows + search(context, category.searchQuery)).distinctBy { it.url }
                    val external = tmdbItems(category.tmdb, 60) +
                        if (category.imdbGenre == null) emptyList() else imdbItems(category.copy(tmdb = null), 100)
                    external + withSearch
                }
            }
        } catch (e: Exception) {
            logError(e)
            emptyList()
        }
        unfiltered = items
        publish()
        TitleResolver.prefetch(context, items.take(12))
    }

    private suspend fun tmdbItems(key: String?, count: Int): List<SearchResponse> = when (key) {
        "trending_india" -> Tmdb.trendingIndia(count)
        "hollywood" -> Tmdb.hollywood(count)
        "south" -> Tmdb.southHits(count)
        "hindi_series" -> Tmdb.hindiSeries(count)
        "korean" -> Tmdb.koreanDrama(count)
        "new_hindi" -> Tmdb.newHindi(count)
        "trending_week" -> Tmdb.trendingWeek(count)
        "top_hindi" -> Tmdb.topHindi(count)
        else -> emptyList()
    }

    private suspend fun imdbItems(category: Category, count: Int): List<SearchResponse> {
        val fromImdb = category.imdbTypes.flatMap { type ->
            Cinemeta.list(category.imdbCatalog, type, category.imdbGenre, count)
        }.sortedByDescending { it.score?.toDouble(10) ?: 0.0 }
            .let { if (category.imdbCatalog == "top") it.shuffled() else it }
        return tmdbItems(category.tmdb, count) + fromImdb
    }

    private suspend fun personalTop(days: Int): List<SearchResponse> {
        val tracked = TasteEngine.topWatched(days, 100)
        val resumed = try {
            HomeViewModel.getResumeWatching().orEmpty()
        } catch (e: Exception) {
            logError(e)
            emptyList()
        }
        return (tracked + resumed).distinctBy { it.url }.take(100)
    }

    private suspend fun search(context: Context, query: String): List<SearchResponse> {
        val apis = context.filterProviderByPreferredMedia(hasHomePageIsRequired = false).take(8)
        return withContext(Dispatchers.IO) {
            coroutineScope {
                apis.map { api ->
                    async {
                        withTimeoutOrNull(12_000L) {
                            (APIRepository(api).search(query, 1) as? Resource.Success)?.value?.items
                        }.orEmpty()
                    }
                }.awaitAll().flatten()
            }
        }
    }

    fun publish() {
        val category = selected ?: return
        val languages = unfiltered
            .flatMap { CardLanguage.codes(it) }
            .groupingBy { it }.eachCount()
            .entries.sortedByDescending { it.value }
            .map { it.key }
        val filtered = unfiltered.filter { card ->
            val typeOk = when (typeFilter) {
                TypeFilter.All -> true
                TypeFilter.Movies -> card.type in movieTypes
                TypeFilter.Series -> card.type in seriesTypes
                TypeFilter.Anime -> card.type in animeTypes
            }
            val langOk = langFilter == null || CardLanguage.codes(card).contains(langFilter)
            typeOk && langOk
        }
        val safe = ProfileFilter.filter(category.key, filtered)
            .distinctBy { TitleResolver.normalize(it.name).ifBlank { it.url } }
        _result.postValue(Resource.Success(CategoryResult(category, safe, languages)))
    }

    fun close() {
        selected = null
        unfiltered = emptyList()
        _result.postValue(null)
    }
}
