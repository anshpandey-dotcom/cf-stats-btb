package com.lagradost.cloudstream3.ui.collections

import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.CloudStreamApp.Companion.getKey
import com.lagradost.cloudstream3.CloudStreamApp.Companion.setKey
import com.lagradost.cloudstream3.LoadResponse
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.ui.foryou.TasteCard
import com.lagradost.cloudstream3.utils.DataStoreHelper

data class CollectionItem(
    @JsonProperty("name") val name: String,
    @JsonProperty("url") val url: String,
    @JsonProperty("apiName") val apiName: String,
    @JsonProperty("poster") val poster: String?,
    @JsonProperty("type") val type: String?,
    @JsonProperty("added") val added: Long,
)

data class UserCollection(
    @JsonProperty("name") val name: String,
    @JsonProperty("items") val items: List<CollectionItem>,
)

object CollectionStore {
    private const val FOLDER = "ansh_collections"
    private val lock = Any()
    val defaultNames = listOf("Watch Later", "Favourites", "Weekend")

    private fun key() = DataStoreHelper.selectedKeyIndex.toString()

    fun all(): List<UserCollection> {
        val stored = getKey<Array<UserCollection>>(FOLDER, key())?.toList()
        return stored ?: defaultNames.map { UserCollection(it, emptyList()) }
    }

    private fun save(list: List<UserCollection>) = setKey(FOLDER, key(), list.toTypedArray())

    fun create(name: String) = synchronized(lock) {
        val clean = name.trim()
        if (clean.isBlank()) return@synchronized
        val current = all()
        if (current.any { it.name.equals(clean, true) }) return@synchronized
        save(current + UserCollection(clean, emptyList()))
    }

    fun delete(name: String) = synchronized(lock) {
        save(all().filterNot { it.name == name })
    }

    fun namesContaining(url: String): Set<String> =
        all().filter { col -> col.items.any { it.url == url } }.map { it.name }.toSet()

    fun setMembership(response: LoadResponse, wanted: Set<String>) = synchronized(lock) {
        val item = CollectionItem(
            name = response.name,
            url = response.url,
            apiName = response.apiName,
            poster = response.posterUrl,
            type = response.type.name,
            added = System.currentTimeMillis()
        )
        save(all().map { col ->
            val has = col.items.any { it.url == item.url }
            when {
                col.name in wanted && !has -> col.copy(items = listOf(item) + col.items)
                col.name !in wanted && has -> col.copy(items = col.items.filterNot { it.url == item.url })
                else -> col
            }
        })
    }

    fun cards(name: String): List<SearchResponse> =
        all().firstOrNull { it.name == name }?.items.orEmpty().map { item ->
            TasteCard(
                name = item.name,
                url = item.url,
                apiName = item.apiName,
                type = item.type?.let { runCatching { TvType.valueOf(it) }.getOrNull() },
                posterUrl = item.poster
            )
        }
}
