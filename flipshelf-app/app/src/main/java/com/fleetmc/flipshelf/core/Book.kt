package com.fleetmc.flipshelf.core

import org.json.JSONObject

enum class Format(val label: String) {
    PDF("PDF"),
    EPUB("EPUB"),
    COMIC("CBZ"),
    TEXT("TXT"),
    IMAGES("IMG")
}

data class Book(
    val id: String,
    var title: String,
    val format: Format,
    val file: String,
    var progress: Float = 0f,
    var pages: Int = 0,
    var addedAt: Long = System.currentTimeMillis(),
    var openedAt: Long = 0L
) {
    fun toJson(): JSONObject = JSONObject()
        .put("id", id)
        .put("title", title)
        .put("format", format.name)
        .put("file", file)
        .put("progress", progress.toDouble())
        .put("pages", pages)
        .put("addedAt", addedAt)
        .put("openedAt", openedAt)

    companion object {
        fun fromJson(o: JSONObject): Book = Book(
            id = o.getString("id"),
            title = o.getString("title"),
            format = Format.valueOf(o.getString("format")),
            file = o.getString("file"),
            progress = o.optDouble("progress", 0.0).toFloat(),
            pages = o.optInt("pages", 0),
            addedAt = o.optLong("addedAt", 0L),
            openedAt = o.optLong("openedAt", 0L)
        )
    }
}
