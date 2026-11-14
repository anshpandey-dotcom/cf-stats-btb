package com.fleetmc.kuchlikh.ui

import android.graphics.Canvas
import android.graphics.Paint
import com.fleetmc.kuchlikh.data.PageSpec

class PageStyle(
    val id: String,
    val name: String,
    val paper: Int,
    val ink: Int,
    val muted: Int,
    val line: Int = 0,
)

object PageStyles {

    val all = listOf(
        PageStyle("plain", "Plain", 0xFFFFFFFF.toInt(), 0xFF1D1D1F.toInt(), 0xFF8A8A8F.toInt()),
        PageStyle("cream", "Cream", 0xFFFBF5E6.toInt(), 0xFF2B2620.toInt(), 0xFF9A8F7C.toInt()),
        PageStyle("ruled", "Ruled", 0xFFFFFFFF.toInt(), 0xFF1D1D1F.toInt(), 0xFF8A8A8F.toInt(), 0xFFC7D3E3.toInt()),
        PageStyle("legal", "Legal pad", 0xFFFFF8CF.toInt(), 0xFF26241C.toInt(), 0xFF9C9670.toInt(), 0xFFB9C6D9.toInt()),
        PageStyle("grid", "Grid", 0xFFFFFFFF.toInt(), 0xFF1D1D1F.toInt(), 0xFF8A8A8F.toInt(), 0xFFDDE4EC.toInt()),
        PageStyle("dotted", "Dotted", 0xFFFDFDFB.toInt(), 0xFF1D1D1F.toInt(), 0xFF8A8A8F.toInt(), 0xFFB5BCC6.toInt()),
        PageStyle("parchment", "Parchment", 0xFFF1E4C6.toInt(), 0xFF3A2E1E.toInt(), 0xFF9C8660.toInt(), 0xFFD3BE92.toInt()),
        PageStyle("frame", "Classic frame", 0xFFFFFFFF.toInt(), 0xFF1D1D1F.toInt(), 0xFF8A8A8F.toInt(), 0xFF3A3A3C.toInt()),
        PageStyle("sky", "Sky", 0xFFEEF5FB.toInt(), 0xFF1B2A3A.toInt(), 0xFF7F93A8.toInt()),
        PageStyle("night", "Night", 0xFF1F2126.toInt(), 0xFFE7E3DA.toInt(), 0xFF8D9099.toInt()),
    )

    fun get(id: String?): PageStyle = all.firstOrNull { it.id == id } ?: all[0]

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)

    @Synchronized
    fun paint(canvas: Canvas, style: PageStyle, lineHeight: Int) {
        val w = PageSpec.W.toFloat()
        val h = PageSpec.H.toFloat()
        paint.style = Paint.Style.FILL
        paint.color = style.paper
        canvas.drawRect(0f, 0f, w, h, paint)

        val top = PageSpec.PAD_TOP.toFloat()
        val lines = PageSpec.CONTENT_H / lineHeight
        val left = PageSpec.PAD_X - 10f
        val right = w - PageSpec.PAD_X + 10f
        paint.color = style.line

        when (style.id) {
            "ruled", "legal" -> {
                paint.strokeWidth = 0.6f
                for (n in 1..lines) {
                    val y = top + n * lineHeight - lineHeight * 0.14f
                    canvas.drawLine(if (style.id == "legal") 0f else left, y, if (style.id == "legal") w else right, y, paint)
                }
                if (style.id == "legal") {
                    paint.color = 0xFFE58B8B.toInt()
                    paint.strokeWidth = 0.8f
                    canvas.drawLine(left - 2f, 0f, left - 2f, h, paint)
                }
            }
            "grid" -> {
                paint.strokeWidth = 0.5f
                val step = lineHeight.toFloat()
                var y = top % step
                while (y < h) {
                    canvas.drawLine(0f, y, w, y, paint)
                    y += step
                }
                var x = PageSpec.PAD_X % step
                while (x < w) {
                    canvas.drawLine(x, 0f, x, h, paint)
                    x += step
                }
            }
            "dotted" -> {
                val step = lineHeight.toFloat()
                var y = top % step
                while (y < h) {
                    var x = PageSpec.PAD_X % step
                    while (x < w) {
                        canvas.drawCircle(x, y, 0.9f, paint)
                        x += step
                    }
                    y += step
                }
            }
            "parchment" -> {
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = 1.2f
                canvas.drawRect(12f, 12f, w - 12f, h - 12f, paint)
                paint.strokeWidth = 0.5f
                canvas.drawRect(15.5f, 15.5f, w - 15.5f, h - 15.5f, paint)
            }
            "frame" -> {
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = 1.4f
                canvas.drawRect(13f, 13f, w - 13f, h - 13f, paint)
                paint.strokeWidth = 0.5f
                canvas.drawRect(17f, 17f, w - 17f, h - 17f, paint)
            }
        }
        paint.style = Paint.Style.FILL
    }
}
