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
