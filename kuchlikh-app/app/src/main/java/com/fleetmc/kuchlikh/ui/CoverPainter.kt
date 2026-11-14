package com.fleetmc.kuchlikh.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.text.TextUtils
import com.fleetmc.kuchlikh.data.PageSpec

class CoverSpec(
    val title: String,
    val author: String,
    val color: Int,
    val style: String,
    val font: String,
    val image: Bitmap?,
)

object CoverPainter {

    val colors = listOf(
        0xFF23395B.toInt(), 0xFF7A2E2E.toInt(), 0xFF2F5D50.toInt(), 0xFF3B3B3B.toInt(),
        0xFF6B4E2E.toInt(), 0xFF4A3B6B.toInt(), 0xFFB5651D.toInt(), 0xFF1F6F8B.toInt(),
        0xFF111111.toInt(), 0xFFB23A48.toInt(), 0xFF3F6B2E.toInt(), 0xFFD9A441.toInt(),
    )

    val styles = listOf(
        "classic" to "Classic",
        "centered" to "Centered",
        "bottom" to "Bottom",
        "band" to "Band",
        "minimal" to "Minimal",
        "photo" to "Photo only",
    )

    private const val GOLD = 0xFFE0A03B.toInt()
    private const val INK = 0xFF1D1D1F.toInt()
    private const val PAPER = 0xFFF7F3EA.toInt()

    fun color(index: Int): Int = colors[index.coerceIn(0, colors.lastIndex)]

    private val W = PageSpec.W.toFloat()
    private val H = PageSpec.H.toFloat()

    fun cropped(canvas: Canvas, bitmap: Bitmap, target: RectF) {
        val scale = maxOf(target.width() / bitmap.width, target.height() / bitmap.height)
        val srcW = target.width() / scale
        val srcH = target.height() / scale
        val left = (bitmap.width - srcW) / 2f
        val top = (bitmap.height - srcH) / 2f
        val src = Rect(left.toInt(), top.toInt(), (left + srcW).toInt(), (top + srcH).toInt())
        canvas.drawBitmap(bitmap, src, target, Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG))
    }

    private fun backdrop(canvas: Canvas, spec: CoverSpec, scrim: Int) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        if (spec.image != null) {
            cropped(canvas, spec.image, RectF(0f, 0f, W, H))
            paint.color = scrim
            canvas.drawRect(0f, 0f, W, H, paint)
        } else {
            paint.color = color(spec.color)
            canvas.drawRect(0f, 0f, W, H, paint)
        }
    }

    private fun fade(canvas: Canvas, from: Float, to: Float, startAlpha: Int, endAlpha: Int) {
        val paint = Paint().apply {
            shader = LinearGradient(0f, from, 0f, to, startAlpha shl 24, endAlpha shl 24, Shader.TileMode.CLAMP)
        }
        canvas.drawRect(0f, minOf(from, to), W, maxOf(from, to), paint)
    }

    private fun title(
        context: Context,
        spec: CoverSpec,
        width: Int,
        maxHeight: Int,
        start: Float,
        color: Int,
        align: Layout.Alignment,
    ): StaticLayout {
        val text = spec.title.ifBlank { "Untitled" }
        val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color
            typeface = Fonts.typeface(context, spec.font, true)
        }
        var size = start
        while (true) {
            paint.textSize = size
            val layout = StaticLayout.Builder.obtain(text, 0, text.length, paint, width)
                .setAlignment(align)
                .setLineSpacing(0f, 1.06f)
                .setIncludePad(false)
                .build()
            if (layout.height <= maxHeight || size <= 20f) {
                return StaticLayout.Builder.obtain(text, 0, text.length, paint, width)
                    .setAlignment(align)
                    .setLineSpacing(0f, 1.06f)
                    .setIncludePad(false)
                    .setMaxLines(maxOf(1, maxHeight / (size * 1.1f).toInt()))
                    .setEllipsize(TextUtils.TruncateAt.END)
                    .build()
            }
            size -= 2f
        }
    }

    private fun author(context: Context, spec: CoverSpec, canvas: Canvas, x: Float, y: Float, color: Int, centered: Boolean, width: Float) {
        if (spec.author.isBlank()) return
        val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color
            typeface = Fonts.typeface(context, "sans", false)
            textSize = 12.5f
            letterSpacing = 0.14f
        }
        val line = TextUtils.ellipsize(spec.author.uppercase(), paint, width, TextUtils.TruncateAt.END).toString()
        val left = if (centered) x + (width - paint.measureText(line)) / 2f else x
        canvas.drawText(line, left, y, paint)
    }

    private fun stroke(canvas: Canvas, rect: RectF, width: Float, color: Int) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = width
            this.color = color
        }
        canvas.drawRect(rect, paint)
    }

    private fun fill(canvas: Canvas, rect: RectF, color: Int) {
        canvas.drawRect(rect, Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color })
    }

    fun draw(context: Context, canvas: Canvas, spec: CoverSpec) {
        val style = if (spec.style == "photo" && spec.image == null) "classic" else spec.style
        when (style) {
            "photo" -> cropped(canvas, spec.image!!, RectF(0f, 0f, W, H))
            "centered" -> centered(context, canvas, spec)
            "bottom" -> bottom(context, canvas, spec)
            "band" -> band(context, canvas, spec)
            "minimal" -> minimal(context, canvas, spec)
            else -> classic(context, canvas, spec)
        }
    }

    private fun classic(context: Context, canvas: Canvas, spec: CoverSpec) {
        backdrop(canvas, spec, 0x73000000)
        fill(canvas, RectF(0f, 0f, 14f, H), 0x2EFFFFFF)
        stroke(canvas, RectF(34f, 30f, W - 24f, H - 30f), 1f, 0xB3FFFFFF.toInt())
        fill(canvas, RectF(56f, 96f, 96f, 99f), GOLD)
        val layout = title(context, spec, 258, 260, 34f, 0xFFFFFFFF.toInt(), Layout.Alignment.ALIGN_NORMAL)
        canvas.save()
        canvas.translate(56f, 120f)
        layout.draw(canvas)
        canvas.restore()
        author(context, spec, canvas, 56f, H - 56f, 0xE6FFFFFF.toInt(), false, 258f)
    }

    private fun centered(context: Context, canvas: Canvas, spec: CoverSpec) {
        backdrop(canvas, spec, 0x80000000.toInt())
        stroke(canvas, RectF(22f, 22f, W - 22f, H - 22f), 1.4f, 0xE6FFFFFF.toInt())
        stroke(canvas, RectF(28f, 28f, W - 28f, H - 28f), 0.6f, 0xB3FFFFFF.toInt())
        val layout = title(context, spec, 260, 250, 36f, 0xFFFFFFFF.toInt(), Layout.Alignment.ALIGN_CENTER)
        val top = (H - layout.height) / 2f - 14f
        fill(canvas, RectF(W / 2f - 22f, top - 26f, W / 2f + 22f, top - 23f), GOLD)
        canvas.save()
        canvas.translate((W - 260f) / 2f, top)
        layout.draw(canvas)
        canvas.restore()
        fill(canvas, RectF(W / 2f - 22f, top + layout.height + 22f, W / 2f + 22f, top + layout.height + 25f), GOLD)
        author(context, spec, canvas, 40f, H - 62f, 0xE6FFFFFF.toInt(), true, W - 80f)
    }

    private fun bottom(context: Context, canvas: Canvas, spec: CoverSpec) {
        backdrop(canvas, spec, 0x26000000)
        fade(canvas, H * 0.3f, H, 0, 0xD9)
        val layout = title(context, spec, 296, 230, 36f, 0xFFFFFFFF.toInt(), Layout.Alignment.ALIGN_NORMAL)
        val top = H - 78f - layout.height
        fill(canvas, RectF(32f, top - 20f, 72f, top - 17f), GOLD)
        canvas.save()
        canvas.translate(32f, top)
        layout.draw(canvas)
        canvas.restore()
        author(context, spec, canvas, 32f, H - 44f, 0xE6FFFFFF.toInt(), false, 296f)
    }

    private fun band(context: Context, canvas: Canvas, spec: CoverSpec) {
        backdrop(canvas, spec, 0x40000000)
        val band = RectF(0f, 186f, W, 332f)
        fill(canvas, band, 0xEBFFFFFF.toInt())
        fill(canvas, RectF(0f, 186f, W, 189f), GOLD)
        fill(canvas, RectF(0f, 329f, W, 332f), GOLD)
        val layout = title(context, spec, 288, 110, 32f, INK, Layout.Alignment.ALIGN_CENTER)
        canvas.save()
        canvas.translate((W - 288f) / 2f, band.centerY() - layout.height / 2f)
        layout.draw(canvas)
        canvas.restore()
        author(context, spec, canvas, 32f, 372f, 0xFFFFFFFF.toInt(), true, W - 64f)
    }

    private fun minimal(context: Context, canvas: Canvas, spec: CoverSpec) {
        fill(canvas, RectF(0f, 0f, W, H), PAPER)
        val accent = color(spec.color)
        if (spec.image != null) {
            cropped(canvas, spec.image, RectF(0f, 0f, W, 296f))
            fill(canvas, RectF(32f, 322f, 76f, 325f), accent)
            val layout = title(context, spec, 296, 120, 30f, INK, Layout.Alignment.ALIGN_NORMAL)
            canvas.save()
            canvas.translate(32f, 340f)
            layout.draw(canvas)
            canvas.restore()
        } else {
            fill(canvas, RectF(32f, 110f, 76f, 114f), accent)
            val layout = title(context, spec, 296, 250, 40f, INK, Layout.Alignment.ALIGN_NORMAL)
            canvas.save()
            canvas.translate(32f, 134f)
            layout.draw(canvas)
            canvas.restore()
            fill(canvas, RectF(0f, H - 14f, W, H), accent)
        }
        author(context, spec, canvas, 32f, H - 40f, 0xFF6B6B70.toInt(), false, 296f)
    }
}
