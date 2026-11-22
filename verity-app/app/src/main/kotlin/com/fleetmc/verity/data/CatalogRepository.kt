package com.fleetmc.verity.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class CatalogRepository(private val context: Context) {

    suspend fun load(): List<Addon> = withContext(Dispatchers.IO) {
        context.assets.open(CATALOG_FILE).bufferedReader().use { CatalogParser.parse(it.readText()) }
    }

    private companion object {
        const val CATALOG_FILE = "catalog.json"
    }
}
