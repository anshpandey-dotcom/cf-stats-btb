package com.fleetmc.flipshelf.core

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.os.ParcelFileDescriptor
import io.legere.pdfiumandroid.PdfDocument
import io.legere.pdfiumandroid.PdfiumCore
import java.io.File
import kotlin.math.max
import kotlin.math.min

class PdfSource(context: Context, file: File) : BookSource {

    private val descriptor = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
    private val core = PdfiumCore(context.applicationContext)
    private val document: PdfDocument = core.newDocument(descriptor)
    private val lock = Any()

    override val pageCount: Int = document.getPageCount()
    override val aspect: Float
    override val reflowable = false

    init {
        if (pageCount == 0) throw IllegalArgumentException("This PDF has no pages")
        val page = document.openPage(0)
        val w = max(1, page.getPageWidthPoint())
        val h = max(1, page.getPageHeightPoint())
        page.close()
        aspect = w.toFloat() / h.toFloat()
    }

    override fun prepare(layoutWidth: Int, layoutHeight: Int, textScale: Float) = Unit

    override fun render(index: Int, width: Int, height: Int): Bitmap {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(Color.WHITE)
        synchronized(lock) {
            val page = document.openPage(index)
            try {
                val pw = max(1, page.getPageWidthPoint()).toFloat()
                val ph = max(1, page.getPageHeightPoint()).toFloat()
                val scale = min(width / pw, height / ph)
                val drawW = max(1, (pw * scale).toInt())
                val drawH = max(1, (ph * scale).toInt())
                val startX = (width - drawW) / 2
                val startY = (height - drawH) / 2
                page.renderPageBitmap(bitmap, startX, startY, drawW, drawH, false, false, Color.WHITE, Color.WHITE)
            } finally {
                page.close()
            }
        }
        return bitmap
    }

    private fun pageText(index: Int): String = synchronized(lock) {
        val page = document.openPage(index)
        try {
            val textPage = page.openTextPage()
            try {
                val count = textPage.textPageCountChars()
                if (count > 0) textPage.textPageGetText(0, count) ?: "" else ""
            } finally {
                textPage.close()
            }
        } finally {
            page.close()
        }
    }

    override fun outline(): List<OutlineEntry> {
        val out = ArrayList<OutlineEntry>()
        fun walk(list: List<PdfDocument.Bookmark>, level: Int) {
            for (b in list) {
                val title = (b.title ?: "").trim()
                if (title.isNotEmpty()) out.add(OutlineEntry(title, b.pageIdx.toInt().coerceIn(0, pageCount - 1), level))
                walk(b.children, level + 1)
            }
        }
        try {
            walk(synchronized(lock) { document.getTableOfContents() }, 0)
        } catch (_: Throwable) {
            out.clear()
        }
        if (out.size >= 2) return out
        out.clear()
        for (i in 0 until min(pageCount, 3000)) {
            val lines = try {
                pageText(i).replace("\r\n", "\n").split('\n').map { it.trim() }.filter { it.isNotEmpty() }.take(8)
            } catch (_: Throwable) {
                continue
            }
            val at = lines.indexOfFirst { Headings.isStrong(it) }
            if (at < 0) continue
            val (title, _) = Headings.mergeChapterTitle(lines[at], lines.getOrNull(at + 1))
            if (out.isEmpty() || out.last().title != title) out.add(OutlineEntry(title, i))
        }
        return out
    }

    override fun close() {
        synchronized(lock) {
            try {
                document.close()
            } catch (_: Exception) {
            }
            try {
                descriptor.close()
            } catch (_: Exception) {
            }
        }
    }
}
