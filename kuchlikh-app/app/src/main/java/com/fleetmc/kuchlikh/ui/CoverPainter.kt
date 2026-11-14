package com.fleetmc.kuchlikh.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.text.TextUtils
import com.fleetmc.kuchlikh.data.PageSpec

object CoverPainter {

    val colors = listOf(
        0xFF23395B.toInt(), 0xFF7A2E2E.toInt(), 0xFF2F5D50.toInt(), 0xFF3B3B3B.toInt(),
        0xFF6B4E2E.toInt(), 0xFF4A3B6B.toInt(), 0xFFB5651D.toInt(), 0xFF1F6F8B.toInt(),
    )

    fun color(index: Int): Int = colors[index.coerceIn(0, colors.lastIndex)]

    fun drawBitmap(canvas: Canvas, bitmap: Bitmap) {
        val w = PageSpec.W.toFloat()
        val h = PageSpec.H.toFloat()
        val scale = maxOf(w / bitmap.width, h / bitmap.height)
        val srcW = w / scale
        val srcH = h / scale
        val left = (bitmap.width - srcW) / 2f
        val top = (bitmap.height - srcH) / 2f
        val src = Rect(left.toInt(), top.toInt(), (left + srcW).toInt(), (top + srcH).toInt())
        canvas.drawBitmap(bitmap, src, RectF(0f, 0f, w, h), Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG))
    }

    fun drawGenerated(context: Context, canvas: Canvas, title: String, author: String, colorIndex: Int) {
        val w = PageSpec.W.toFloat()
        val h = PageSpec.H.toFloat()
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.color = color(colorIndex)
        canvas.drawRect(0f, 0f, w, h, paint)

        paint.color = 0x1FFFFFFF
        canvas.drawRect(0f, 0f, 14f, h, paint)

        paint.color = 0xB3FFFFFF.toInt()
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 1f
        canvas.drawRect(34f, 30f, w - 24f, h - 30f, paint)
        paint.style = Paint.Style.FILL

        val titlePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFFFFFFFF.toInt()
            typeface = Fonts.typeface(context, "serif", true)
            textSize = 34f
        }
        val width = (w - 34f - 24f - 44f).toInt()
        val shown = title.ifBlank { "Untitled" }
        val layout = StaticLayout.Builder.obtain(shown, 0, shown.length, titlePaint, width)
            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .setLineSpacing(0f, 1.08f)
            .setMaxLines(5)
            .setEllipsize(TextUtils.TruncateAt.END)
            .build()
        canvas.save()
        canvas.translate(56f, 120f)
        layout.draw(canvas)
        canvas.restore()

        paint.color = 0xFFE0A03B.toInt()
        canvas.drawRect(56f, 96f, 96f, 99f, paint)

        if (author.isNotBlank()) {
            val authorPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                color = 0xE6FFFFFF.toInt()
                typeface = Fonts.typeface(context, "sans", false)
                textSize = 13f
                letterSpacing = 0.08f
            }
            val line = TextUtils.ellipsize(author.uppercase(), authorPaint, width.toFloat(), TextUtils.TruncateAt.END).toString()
            canvas.drawText(line, 56f, h - 56f, authorPaint)
        }
    }
}
