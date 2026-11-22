package com.fleetmc.verity.data

import org.json.JSONArray
import org.json.JSONObject

object CatalogParser {

    fun parse(json: String): List<Addon> {
        val array = JSONArray(json)
        return (0 until array.length()).mapNotNull { index -> toAddon(array.getJSONObject(index)) }
    }

    private fun toAddon(item: JSONObject): Addon? {
        val id = item.optString("id")
        val file = item.optString("file")
        if (id.isBlank() || file.isBlank()) return null
        val minVersion = item.optString("minVersion")
        return Addon(
            id = id,
            name = item.optString("name", id),
            description = item.optString("description"),
            category = item.optString("category", "Other"),
            minVersion = minVersion,
            maxVersion = item.optString("maxVersion", minVersion),
            file = file,
            sizeBytes = item.optLong("sizeBytes"),
            previews = item.optJSONArray("previews").toStringList(),
        )
    }

    private fun JSONArray?.toStringList(): List<String> {
        if (this == null) return emptyList()
        return (0 until length()).map { getString(it) }.filter { it.isNotBlank() }
    }
}
