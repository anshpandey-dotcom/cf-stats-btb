package com.lagradost.cloudstream3.ui.home

import android.content.Context
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.mvvm.Resource
import com.lagradost.cloudstream3.ui.APIRepository
import com.lagradost.cloudstream3.utils.AppContextUtils.filterProviderByPreferredMedia
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

object FeedRows {
    private const val CACHE_MS = 15 * 60 * 1000L
    private var cached: List<Pair<String, List<SearchResponse>>> = emptyList()
    private var cachedAt = 0L
    private var cachedKey = ""
    private val mutex = Mutex()

    suspend fun rows(context: Context, force: Boolean = false): List<Pair<String, List<SearchResponse>>> =
        mutex.withLock {
            val apis = context.filterProviderByPreferredMedia(hasHomePageIsRequired = true)
            val key = apis.joinToString(",") { it.name }
            if (!force && key == cachedKey && cached.isNotEmpty() &&
                System.currentTimeMillis() - cachedAt < CACHE_MS
            ) return cached

            val loaded = withContext(Dispatchers.IO) {
                coroutineScope {
                    apis.map { api ->
                        async {
                            withTimeoutOrNull(15_000L) {
                                val page = APIRepository(api).getMainPage(1, null)
                                (page as? Resource.Success)?.value.orEmpty()
                                    .filterNotNull()
                                    .flatMap { it.items }
                                    .filter { it.list.isNotEmpty() }
                                    .map { it.name to it.list }
                            } ?: emptyList()
                        }
                    }.awaitAll().flatten()
                }
            }
            if (loaded.isNotEmpty()) {
                cached = loaded
                cachedAt = System.currentTimeMillis()
                cachedKey = key
            }
            loaded
        }
}
