package com.fleetmc.flipshelf.core

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.BitmapDrawable
import android.os.Build
import android.text.Html
import android.text.Layout
import android.text.Spanned
import android.text.StaticLayout
import android.text.TextPaint
import java.io.File
import java.util.zip.ZipFile
import kotlin.math.min

class ReflowSource private constructor(
    private val loader: ChapterLoader,
    private val closer: () -> Unit
) : BookSource {

    interface ChapterLoader {
        val chapterCount: Int
        fun load(index: Int, maxImageWidth: Int, maxImageHeight: Int): CharSequence
    }

    private class PageSlice(val layout: StaticLayout, val top: Int, val bottom: Int)

    private val pages = ArrayList<PageSlice>()
    private var layoutWidth = 1
    private var layoutHeight = 1
    private var scale = 1f
    private var marginX = 0f
    private var marginY = 0f

    override val pageCount: Int get() = pages.size
    override val aspect = 0.68f
    override val reflowable = true

    @Synchronized
    override fun prepare(layoutWidth: Int, layoutHeight: Int, textScale: Float) {
        this.layoutWidth = layoutWidth
        this.layoutHeight = layoutHeight
        this.scale = textScale
        pages.clear()
        val textSize = min(layoutWidth, layoutHeight) * 0.043f * textScale
        val contentW = min(layoutWidth * 0.85f, textSize * 26f).toInt().coerceAtLeast(1)
        marginX = (layoutWidth - contentW) / 2f
        marginY = layoutHeight * 0.06f
        val contentH = (layoutHeight - marginY * 2f).toInt().coerceAtLeast(1)
        val paint = TextPaint(TextPaint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(28, 28, 30)
            typeface = Typeface.SERIF
            this.textSize = textSize
        }
        for (c in 0 until loader.chapterCount) {
            val text = try {
                loader.load(c, contentW, (contentH * 0.85f).toInt())
            } catch (_: Exception) {
                continue
            }
            if (text.isBlank()) continue
            val builder = StaticLayout.Builder.obtain(text, 0, text.length, paint, contentW)
                .setIncludePad(false)
                .setLineSpacing(0f, 1.28f)
                .setBreakStrategy(Layout.BREAK_STRATEGY_HIGH_QUALITY)
                .setHyphenationFrequency(Layout.HYPHENATION_FREQUENCY_NORMAL)
            if (Build.VERSION.SDK_INT >= 26) builder.setJustificationMode(Layout.JUSTIFICATION_MODE_INTER_WORD)
            val layout = builder.build()
            var pageTop = 0
            var startLine = 0
            for (l in 0 until layout.lineCount) {
                if (layout.getLineBottom(l) - pageTop > contentH && l > startLine) {
                    pages.add(PageSlice(layout, pageTop, layout.getLineTop(l)))
                    pageTop = layout.getLineTop(l)
                    startLine = l
                }
            }
            pages.add(PageSlice(layout, pageTop, layout.height))
        }
        if (pages.isEmpty()) {
            val empty = StaticLayout.Builder.obtain("This book has no readable text.", 0, 31, paint, contentW).build()
            pages.add(PageSlice(empty, 0, empty.height))
        }
    }

    @Synchronized
    override fun render(index: Int, width: Int, height: Int): Bitmap {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(Color.WHITE)
        if (index !in pages.indices) return bitmap
        val slice = pages[index]
        val canvas = Canvas(bitmap)
        canvas.scale(width / layoutWidth.toFloat(), height / layoutHeight.toFloat())
        canvas.translate(marginX, marginY)
        canvas.clipRect(0f, 0f, layoutWidth - marginX * 2f, (slice.bottom - slice.top).toFloat())
        canvas.translate(0f, -slice.top.toFloat())
        slice.layout.draw(canvas)
        return bitmap
    }

    override fun close() = closer()

    private class EpubLoader(file: File, val parser: EpubParser) : ChapterLoader {
        private val zip = ZipFile(file)
        override val chapterCount = parser.spine.size

        override fun load(index: Int, maxImageWidth: Int, maxImageHeight: Int): CharSequence {
            val path = parser.spine[index]
            val raw = parser.readChapter(zip, path) ?: return ""
            val bodyStart = Regex("<body[^>]*>", RegexOption.IGNORE_CASE).find(raw)
            val bodyEnd = raw.lastIndexOf("</body>", ignoreCase = true)
            var html = if (bodyStart != null) {
                raw.substring(bodyStart.range.last + 1, if (bodyEnd > bodyStart.range.last) bodyEnd else raw.length)
            } else raw
            html = html.replace(Regex("<script[\\s\\S]*?</script>", RegexOption.IGNORE_CASE), "")
                .replace(Regex("<style[\\s\\S]*?</style>", RegexOption.IGNORE_CASE), "")
            val base = path.substringBeforeLast('/', "")
            val getter = Html.ImageGetter { src -> imageFor(base, src, maxImageWidth, maxImageHeight) }
            val spanned = Html.fromHtml(html, Html.FROM_HTML_MODE_LEGACY, getter, null)
            return trimTrailing(spanned)
        }

        private fun trimTrailing(text: CharSequence): CharSequence {
            var end = text.length
            while (end > 0 && text[end - 1].isWhitespace()) end--
            var start = 0
            while (start < end && text[start] == '\n') start++
            return text.subSequence(start, end)
        }

        private fun imageFor(base: String, src: String?, maxW: Int, maxH: Int): BitmapDrawable? {
            if (src == null) return null
            return try {
                val entry = zip.getEntry(EpubParser.resolve(base, src)) ?: return null
                val bytes = zip.getInputStream(entry).use { it.readBytes() }
                val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size, o)
                if (o.outWidth <= 0) return null
                var sample = 1
                while (o.outWidth / (sample * 2) >= maxW) sample *= 2
                val d = BitmapFactory.Options().apply { inSampleSize = sample }
                val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, d) ?: return null
                val k = min(maxW / bmp.width.toFloat(), maxH / bmp.height.toFloat())
                val drawable = BitmapDrawable(null, bmp)
                drawable.setBounds(0, 0, (bmp.width * k).toInt(), (bmp.height * k).toInt())
                drawable
            } catch (_: Exception) {
                null
            }
        }

        fun close() = zip.close()
    }

    private class TextLoader(private val text: String) : ChapterLoader {
        override val chapterCount = 1
        override fun load(index: Int, maxImageWidth: Int, maxImageHeight: Int): CharSequence = text
    }

    companion object {
        fun fromEpub(file: File): ReflowSource {
            val loader = EpubLoader(file, EpubParser(file))
            return ReflowSource(loader) { loader.close() }
        }

        fun fromText(file: File): ReflowSource {
            val bytes = file.readBytes()
            val utf8 = String(bytes, Charsets.UTF_8)
            val text = if (utf8.count { it == '�' } > bytes.size / 200) String(bytes, Charsets.ISO_8859_1) else utf8
            return ReflowSource(TextLoader(text.replace("\r\n", "\n").replace('\r', '\n'))) { }
        }
    }
}
