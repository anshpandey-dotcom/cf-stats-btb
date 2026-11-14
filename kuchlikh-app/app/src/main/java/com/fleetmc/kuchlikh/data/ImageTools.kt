package com.fleetmc.kuchlikh.data

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Matrix
import android.graphics.Paint
import androidx.exifinterface.media.ExifInterface
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.segmentation.subject.SubjectSegmentation
import com.google.mlkit.vision.segmentation.subject.SubjectSegmenterOptions
import kotlinx.coroutines.tasks.await
import java.io.ByteArrayInputStream

object ImageTools {

    fun decode(bytes: ByteArray, maxSide: Int = 1600): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxSide) sample *= 2
        var bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })
            ?: return null
        val rotation = runCatching {
            when (ExifInterface(ByteArrayInputStream(bytes)).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                else -> 0f
            }
        }.getOrDefault(0f)
        val longest = maxOf(bitmap.width, bitmap.height)
        val scale = if (longest > maxSide) maxSide / longest.toFloat() else 1f
        if (rotation != 0f || scale != 1f) {
            val matrix = Matrix().apply {
                postScale(scale, scale)
                postRotate(rotation)
            }
            bitmap = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        }
        return bitmap
    }

    fun hasTransparency(bitmap: Bitmap): Boolean {
        if (!bitmap.hasAlpha()) return false
        val stepX = maxOf(1, bitmap.width / 80)
        val stepY = maxOf(1, bitmap.height / 80)
        var y = 0
        while (y < bitmap.height) {
            var x = 0
            while (x < bitmap.width) {
                if ((bitmap.getPixel(x, y) ushr 24) < 250) return true
                x += stepX
            }
            y += stepY
        }
        return false
    }

    fun blackAndWhite(source: Bitmap): Bitmap {
        val out = Bitmap.createBitmap(source.width, source.height, Bitmap.Config.ARGB_8888)
        val paint = Paint(Paint.FILTER_BITMAP_FLAG).apply {
            colorFilter = ColorMatrixColorFilter(ColorMatrix().apply { setSaturation(0f) })
        }
        Canvas(out).drawBitmap(source, 0f, 0f, paint)
        return out
    }

    suspend fun cutout(source: Bitmap): Bitmap? {
        val smart = runCatching { subject(source) }.getOrNull()
        return smart ?: floodCutout(source)
    }

    private suspend fun subject(source: Bitmap): Bitmap? {
        val segmenter = SubjectSegmentation.getClient(SubjectSegmenterOptions.Builder().enableForegroundBitmap().build())
        return try {
            val result = segmenter.process(InputImage.fromBitmap(source, 0)).await()
            result.foregroundBitmap?.let { trim(it) }
        } finally {
            segmenter.close()
        }
    }

    fun trim(bitmap: Bitmap): Bitmap {
        val w = bitmap.width
        val h = bitmap.height
        val pixels = IntArray(w * h)
        bitmap.getPixels(pixels, 0, w, 0, 0, w, h)
        var minX = w
        var minY = h
        var maxX = -1
        var maxY = -1
        for (y in 0 until h) {
            for (x in 0 until w) {
                if ((pixels[y * w + x] ushr 24) > 12) {
                    if (x < minX) minX = x
                    if (x > maxX) maxX = x
                    if (y < minY) minY = y
                    if (y > maxY) maxY = y
                }
            }
        }
        if (maxX < minX || maxY < minY) return bitmap
        return Bitmap.createBitmap(bitmap, minX, minY, maxX - minX + 1, maxY - minY + 1)
    }

    fun floodCutout(source: Bitmap, tolerance: Int = 46): Bitmap? {
        val w = source.width
        val h = source.height
        if (w < 8 || h < 8) return null
        val pixels = IntArray(w * h)
        source.getPixels(pixels, 0, w, 0, 0, w, h)

        var sr = 0L
        var sg = 0L
        var sb = 0L
        var count = 0
        fun border(visit: (Int) -> Unit) {
            for (x in 0 until w) {
                visit(x)
                visit((h - 1) * w + x)
            }
            for (y in 1 until h - 1) {
                visit(y * w)
                visit(y * w + w - 1)
            }
        }
        border { index ->
            val c = pixels[index]
            if ((c ushr 24) > 200) {
                sr += (c shr 16) and 255
                sg += (c shr 8) and 255
                sb += c and 255
                count++
            }
        }
        if (count < (w + h)) return null
        val mr = (sr / count).toInt()
        val mg = (sg / count).toInt()
        val mb = (sb / count).toInt()
        val limit = tolerance * tolerance

        fun near(c: Int): Boolean {
            if ((c ushr 24) < 40) return true
            val dr = ((c shr 16) and 255) - mr
            val dg = ((c shr 8) and 255) - mg
            val db = (c and 255) - mb
            return dr * dr + dg * dg + db * db <= limit
        }

        var matching = 0
        border { index -> if (near(pixels[index])) matching++ }
        if (matching < (w + h) * 2 * 0.8) return null

        val background = BooleanArray(w * h)
        val queue = IntArray(w * h)
        var head = 0
        var tail = 0
        border { index ->
            if (!background[index] && near(pixels[index])) {
                background[index] = true
                queue[tail++] = index
            }
        }
        while (head < tail) {
            val index = queue[head++]
            val x = index % w
            val y = index / w
            if (x > 0 && !background[index - 1] && near(pixels[index - 1])) { background[index - 1] = true; queue[tail++] = index - 1 }
            if (x < w - 1 && !background[index + 1] && near(pixels[index + 1])) { background[index + 1] = true; queue[tail++] = index + 1 }
            if (y > 0 && !background[index - w] && near(pixels[index - w])) { background[index - w] = true; queue[tail++] = index - w }
            if (y < h - 1 && !background[index + w] && near(pixels[index + w])) { background[index + w] = true; queue[tail++] = index + w }
        }
        val share = tail / (w * h).toFloat()
        if (share < 0.04f || share > 0.97f) return null

        val out = IntArray(w * h)
        for (y in 0 until h) {
            for (x in 0 until w) {
                val index = y * w + x
                if (background[index]) {
                    out[index] = 0
                    continue
                }
                val edge = (x > 0 && background[index - 1]) || (x < w - 1 && background[index + 1]) ||
                    (y > 0 && background[index - w]) || (y < h - 1 && background[index + w])
                val c = pixels[index]
                out[index] = if (edge) (((c ushr 24) * 0.55f).toInt() shl 24) or (c and 0xFFFFFF) else c
            }
        }
        val result = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        result.setPixels(out, 0, w, 0, 0, w, h)
        return trim(result)
    }
}
