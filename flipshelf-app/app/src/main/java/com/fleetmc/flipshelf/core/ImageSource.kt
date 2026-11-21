package com.fleetmc.flipshelf.core

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import java.io.File
import java.io.InputStream
import java.util.zip.ZipFile
import kotlin.math.min

class ImageSource private constructor(
    private val count: Int,
    private val opener: (Int) -> InputStream,
    private val closer: () -> Unit
) : BookSource {

    override val pageCount: Int = count
    override val aspect: Float
    override val reflowable = false

    private val paint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)

    init {
        val b = bounds(0)
        aspect = if (b.second > 0) b.first.toFloat() / b.second.toFloat() else 0.7f
    }

    private fun bounds(index: Int): Pair<Int, Int> {
        val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        opener(index).use { BitmapFactory.decodeStream(it, null, o) }
        return Pair(o.outWidth, o.outHeight)
    }

    override fun prepare(layoutWidth: Int, layoutHeight: Int, textScale: Float) = Unit

    override fun render(index: Int, width: Int, height: Int): Bitmap {
        val out = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        out.eraseColor(Color.WHITE)
        val (bw, bh) = bounds(index)
        if (bw <= 0 || bh <= 0) return out
        var sample = 1
        while (bw / (sample * 2) >= width && bh / (sample * 2) >= height) sample *= 2
        val o = BitmapFactory.Options().apply { inSampleSize = sample }
        val src = opener(index).use { BitmapFactory.decodeStream(it, null, o) } ?: return out
        val scale = min(width / src.width.toFloat(), height / src.height.toFloat())
        val w = src.width * scale
        val h = src.height * scale
        val dst = RectF((width - w) / 2f, (height - h) / 2f, (width + w) / 2f, (height + h) / 2f)
        Canvas(out).drawBitmap(src, null, dst, paint)
        src.recycle()
        return out
    }

    override fun close() = closer()

    companion object {
        private val extensions = setOf("jpg", "jpeg", "png", "webp", "gif", "bmp")

        fun isImageName(name: String): Boolean {
            val n = name.substringAfterLast('/')
            if (n.startsWith(".")) return false
            return n.substringAfterLast('.', "").lowercase() in extensions
        }

        private val chunk = Regex("(\\d+)|(\\D+)")

        fun naturalCompare(a: String, b: String): Int {
            val pa = chunk.findAll(a.lowercase()).map { it.value }.toList()
            val pb = chunk.findAll(b.lowercase()).map { it.value }.toList()
            for (i in 0 until min(pa.size, pb.size)) {
                val x = pa[i]
                val y = pb[i]
                val c = if (x[0].isDigit() && y[0].isDigit()) {
                    val nx = x.trimStart('0')
                    val ny = y.trimStart('0')
                    if (nx.length != ny.length) nx.length.compareTo(ny.length) else nx.compareTo(ny)
                } else x.compareTo(y)
                if (c != 0) return c
            }
            return pa.size.compareTo(pb.size)
        }

        fun fromZip(file: File): ImageSource {
            val zip = ZipFile(file)
            val names = zip.entries().asSequence()
                .filter { !it.isDirectory && !it.name.startsWith("__MACOSX") && isImageName(it.name) }
                .map { it.name }
                .sortedWith { a, b -> naturalCompare(a, b) }
                .toList()
            if (names.isEmpty()) {
                zip.close()
                throw IllegalArgumentException("No images found in archive")
            }
            return ImageSource(
                names.size,
                { i -> zip.getInputStream(zip.getEntry(names[i])) },
                { zip.close() }
            )
        }

        fun fromFolder(dir: File): ImageSource {
            val files = (dir.listFiles() ?: emptyArray())
                .filter { it.isFile && isImageName(it.name) }
                .sortedWith { a, b -> naturalCompare(a.name, b.name) }
            if (files.isEmpty()) throw IllegalArgumentException("No images found")
            return ImageSource(files.size, { i -> files[i].inputStream() }, { })
        }
    }
}
