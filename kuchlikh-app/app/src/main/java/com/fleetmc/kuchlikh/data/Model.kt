package com.fleetmc.kuchlikh.data

import kotlinx.serialization.Serializable
import java.util.UUID

fun newId(): String = UUID.randomUUID().toString().replace("-", "").take(12)

@Serializable
data class Page(
    val id: String,
    val text: String = "",
    val para: Boolean = false,
    val chapter: String? = null,
    val flowStart: Boolean = false,
    val image: String? = null,
    val imageAspect: Float = 1f,
    val imageSize: Int = 1,
    val imageBottom: Boolean = false,
    val imageSrc: String? = null,
    val imageFx: String = "",
    val imageCredit: String? = null,
    val style: String? = null,
)

@Serializable
data class Book(
    val id: String,
    val title: String,
    val author: String = "",
    val cover: String? = null,
    val coverColor: Int = 0,
    val coverStyle: String = "classic",
    val coverFont: String = "serif",
    val pageStyle: String = "plain",
    val font: String = "serif",
    val headFont: String? = null,
    val textSize: Int = 1,
    val justify: Boolean = false,
    val pages: List<Page> = listOf(Page(id = newId(), flowStart = true)),
    val createdAt: Long = 0,
    val updatedAt: Long = 0,
)

data class ChapterRef(val number: Int, val title: String, val pageIndex: Int)

fun Book.chapters(): List<ChapterRef> {
    val out = ArrayList<ChapterRef>()
    pages.forEachIndexed { index, page ->
        if (page.chapter != null) out += ChapterRef(out.size + 1, page.chapter, index)
    }
    return out
}

fun Book.wordCount(): Int = pages.sumOf { page ->
    Blocks.strip(page.text).split(Regex("\\s+")).count { it.isNotBlank() }
}

class TextSize(val font: Int, val line: Int)

object PageSpec {
    const val CANON = 3f
    const val W = 360
    const val H = 510
    const val PAD_X = 30
    const val PAD_TOP = 34
    const val CONTENT_W = 300
    const val CONTENT_H = 436
    const val HEADING_LINES = 4
    const val IMAGE_GAP = 8

    val sizes = listOf(TextSize(13, 21), TextSize(15, 24), TextSize(17, 27), TextSize(19, 30))
    val sizeLabels = listOf("Small", "Medium", "Large", "Extra large")

    fun size(index: Int): TextSize = sizes[index.coerceIn(0, sizes.lastIndex)]

    fun totalLines(sizeIndex: Int): Int = CONTENT_H / size(sizeIndex).line

    fun headingLines(page: Page): Int = if (page.chapter != null) HEADING_LINES else 0

    fun imageLines(page: Page, sizeIndex: Int): Int {
        if (page.image == null) return 0
        val line = size(sizeIndex).line
        val available = totalLines(sizeIndex) - headingLines(page)
        if (page.imageSize >= 3) return available
        val share = when (page.imageSize) {
            0 -> 0.3f
            1 -> 0.45f
            else -> 0.65f
        }
        val wanted = Math.round(available * share)
        val natural = Math.ceil(((CONTENT_W / page.imageAspect.coerceAtLeast(0.2f)) + IMAGE_GAP) / line.toDouble()).toInt()
        return minOf(wanted, natural).coerceIn(2, available)
    }

    fun textLines(page: Page, sizeIndex: Int): Int =
        totalLines(sizeIndex) - headingLines(page) - imageLines(page, sizeIndex)
}
