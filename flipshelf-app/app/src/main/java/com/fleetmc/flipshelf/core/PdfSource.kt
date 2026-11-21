package com.fleetmc.flipshelf.core

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import java.io.File
import kotlin.math.min

class PdfSource(file: File) : BookSource {

    private val descriptor = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
    private val renderer = PdfRenderer(descriptor)
    private val lock = Any()

    override val pageCount: Int = renderer.pageCount
    override val aspect: Float
    override val reflowable = false

    init {
        val page = renderer.openPage(0)
        aspect = page.width.toFloat() / page.height.toFloat()
        page.close()
    }

    override fun prepare(layoutWidth: Int, layoutHeight: Int, textScale: Float) = Unit

    override fun render(index: Int, width: Int, height: Int): Bitmap {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(Color.WHITE)
        synchronized(lock) {
            val page = renderer.openPage(index)
            try {
                val scale = min(width / page.width.toFloat(), height / page.height.toFloat())
                val m = Matrix()
                m.postScale(scale, scale)
                m.postTranslate((width - page.width * scale) / 2f, (height - page.height * scale) / 2f)
                page.render(bitmap, null, m, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
            } finally {
                page.close()
            }
        }
        return bitmap
    }

    override fun close() {
        synchronized(lock) {
            try {
                renderer.close()
            } catch (_: Exception) {
            }
            try {
                descriptor.close()
            } catch (_: Exception) {
            }
        }
    }
}
