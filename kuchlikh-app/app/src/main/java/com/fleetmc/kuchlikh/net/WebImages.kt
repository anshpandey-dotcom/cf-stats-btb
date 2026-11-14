package com.fleetmc.kuchlikh.net

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.net.URLEncoder

enum class ImageSource(val label: String) {
    Commons("Wikimedia Commons"),
    Wikipedia("Wikipedia"),
}

enum class ImageKind(val label: String) {
    Any("All"),
    Photo("Photos"),
    Transparent("Transparent"),
    Drawing("Clip art"),
}

class WebImage(
    val title: String,
    val thumb: String,
    val full: String,
    val backup: String,
    val width: Int,
    val height: Int,
    val transparent: Boolean,
    val credit: String,
)

object WebImages {

    private val json = Json { ignoreUnknownKeys = true }
    private val tags = Regex("<[^>]*>")
    private val spaces = Regex("\\s+")

    private fun encode(value: String): String = URLEncoder.encode(value, "UTF-8")

    private fun plain(html: String?): String =
        html.orEmpty().replace(tags, "").replace("&amp;", "&").replace("&quot;", "\"").replace("&#039;", "'")
            .replace(spaces, " ").trim().take(110)

    private fun JsonElement?.obj(key: String): JsonObject? = (this as? JsonObject)?.get(key) as? JsonObject

    private fun JsonElement?.text(key: String): String? =
        ((this as? JsonObject)?.get(key) as? kotlinx.serialization.json.JsonPrimitive)?.contentOrNull

    private fun JsonElement?.number(key: String): Int =
        ((this as? JsonObject)?.get(key) as? kotlinx.serialization.json.JsonPrimitive)?.intOrNull ?: 0

    private fun larger(url: String, from: Int, to: Int): String = url.replace("/${from}px-", "/${to}px-")

    fun search(query: String, source: ImageSource, kind: ImageKind, large: Boolean): List<WebImage> {
        val clean = query.trim()
        if (clean.isEmpty()) return emptyList()
        return when (source) {
            ImageSource.Commons -> commons(clean, kind, large)
            ImageSource.Wikipedia -> wikipedia(clean, kind, large)
        }
    }

    private fun commons(query: String, kind: ImageKind, large: Boolean): List<WebImage> {
        val filter = when (kind) {
            ImageKind.Any -> "filetype:bitmap"
            ImageKind.Photo -> "filemime:jpeg"
            ImageKind.Transparent -> "filemime:png"
            ImageKind.Drawing -> "filetype:drawing"
        }
        val search = "$query $filter" + if (large) " filew:>1200" else ""
        val url = "https://commons.wikimedia.org/w/api.php?action=query&format=json&generator=search&gsrnamespace=6" +
            "&gsrlimit=30&gsrsearch=${encode(search)}&prop=imageinfo&iiprop=url|size|mime|extmetadata" +
            "&iiurlwidth=330&iiextmetadatafilter=Artist|LicenseShortName"
        val pages = json.parseToJsonElement(Net.text(url)).obj("query").obj("pages") ?: return emptyList()
        return pages.values.mapNotNull { page ->
            val info = (page as? JsonObject)?.get("imageinfo")?.jsonArray?.firstOrNull() ?: return@mapNotNull null
            val thumb = info.text("thumburl") ?: return@mapNotNull null
            val original = info.text("url") ?: return@mapNotNull null
            val mime = info.text("mime").orEmpty()
            val width = info.number("width")
            val height = info.number("height")
            val vector = mime == "image/svg+xml"
            val full = if (vector || width > 1280) larger(thumb, 330, 1280) else original
            val artist = plain(info.obj("extmetadata").obj("Artist").text("value"))
            val licence = plain(info.obj("extmetadata").obj("LicenseShortName").text("value"))
            val credit = listOf(artist, licence).filter { it.isNotBlank() }.joinToString(", ")
                .ifBlank { "Wikimedia Commons" } + " via Wikimedia Commons"
            val order = (page as JsonObject)["index"]?.jsonPrimitive?.intOrNull ?: 0
            order to WebImage(
                title = (page["title"]?.jsonPrimitive?.contentOrNull ?: "").removePrefix("File:").substringBeforeLast('.'),
                thumb = thumb,
                full = full,
                backup = thumb,
                width = width,
                height = height,
                transparent = mime == "image/png" || vector,
                credit = credit,
            )
        }.sortedBy { it.first }.map { it.second }
    }

    private fun wikipedia(query: String, kind: ImageKind, large: Boolean): List<WebImage> {
        val url = "https://en.wikipedia.org/w/api.php?action=query&format=json&generator=search&gsrlimit=30" +
            "&gsrsearch=${encode(query)}&prop=pageimages&piprop=thumbnail|original&pithumbsize=330&pilimit=30"
        val pages = json.parseToJsonElement(Net.text(url)).obj("query").obj("pages") ?: return emptyList()
        return pages.values.mapNotNull { page ->
            val thumb = page.obj("thumbnail").text("source") ?: return@mapNotNull null
            val original = page.obj("original")
            val source = original.text("source") ?: return@mapNotNull null
            val width = original.number("width")
            val height = original.number("height")
            val lower = source.lowercase()
            val transparent = lower.endsWith(".png") || lower.endsWith(".svg")
            val vector = lower.endsWith(".svg")
            when (kind) {
                ImageKind.Photo -> if (!(lower.endsWith(".jpg") || lower.endsWith(".jpeg"))) return@mapNotNull null
                ImageKind.Transparent -> if (!transparent) return@mapNotNull null
                ImageKind.Drawing -> if (!vector) return@mapNotNull null
                ImageKind.Any -> {}
            }
            if (large && width in 1..1199) return@mapNotNull null
            val title = page.text("title").orEmpty()
            val order = (page as JsonObject)["index"]?.jsonPrimitive?.intOrNull ?: 0
            order to WebImage(
                title = title,
                thumb = thumb,
                full = if (vector || width > 960) larger(thumb, 330, 960) else source,
                backup = thumb,
                width = width,
                height = height,
                transparent = transparent,
                credit = "Image from the Wikipedia article \"$title\" (Wikimedia licence)",
            )
        }.sortedBy { it.first }.map { it.second }
    }

    fun download(image: WebImage): ByteArray {
        for (candidate in listOf(image.full, image.backup).distinct()) {
            val bytes = runCatching { Net.get(candidate, 20_000_000) }.getOrNull()
            if (bytes != null) return bytes
        }
        throw java.io.IOException("Download failed")
    }
}
