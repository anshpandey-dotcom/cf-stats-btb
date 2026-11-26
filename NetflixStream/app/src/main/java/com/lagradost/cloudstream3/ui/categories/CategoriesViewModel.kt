package com.lagradost.cloudstream3.ui.categories

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
        private const val CACHE_MS = 15 * 60 * 1000L
        private var rowsCache: List<Pair<String, List<SearchResponse>>> = emptyList()
        private var rowsTime = 0L

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

    private suspend fun rows(context: Context): List<Pair<String, List<SearchResponse>>> {
        if (rowsCache.isNotEmpty() && System.currentTimeMillis() - rowsTime < CACHE_MS) return rowsCache
        val apis = context.filterProviderByPreferredMedia(hasHomePageIsRequired = true)
        val loaded = withContext(Dispatchers.IO) {
            coroutineScope {
                apis.map { api ->
                    async {
                        withTimeoutOrNull(15_000L) {
                            val page = APIRepository(api).getMainPage(1, null)
                            (page as? Resource.Success)?.value.orEmpty()
                                .filterNotNull()
                                .flatMap { it.items }
                                .map { it.name to it.list }
                        } ?: emptyList()
                    }
                }.awaitAll().flatten()
            }
        }
        if (loaded.isNotEmpty()) {
            rowsCache = loaded
            rowsTime = System.currentTimeMillis()
        }
        return loaded
    }

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
        CategoryCatalog.all.forEach { category ->
            val pick = when (category.source) {
                CategorySource.Rows -> matchRows(rows, category)
                CategorySource.ForYou -> TasteEngine.rank(everything, limit = 3)
                CategorySource.TopWeek -> TasteEngine.topWatched(7, 1)
                CategorySource.TopMonth -> TasteEngine.topWatched(30, 1)
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
                CategorySource.TopWeek -> TasteEngine.topWatched(7)
                CategorySource.TopMonth -> TasteEngine.topWatched(30)
                CategorySource.TopRated -> everything
                    .filter { (it.score?.toDouble(10) ?: 0.0) > 0.0 }
                    .sortedByDescending { it.score?.toDouble(10) ?: 0.0 }
                    .take(50)
                CategorySource.Rows -> {
                    val fromRows = matchRows(rows, category)
                    if (fromRows.size >= 24 || category.searchQuery == null) fromRows
                    else (fromRows + search(context, category.searchQuery)).distinctBy { it.url }
                }
            }
        } catch (e: Exception) {
            logError(e)
            emptyList()
        }
        unfiltered = items
        publish()
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
        _result.postValue(Resource.Success(CategoryResult(category, filtered, languages)))
    }

    fun close() {
        selected = null
        unfiltered = emptyList()
        _result.postValue(null)
    }
}
