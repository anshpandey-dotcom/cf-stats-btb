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
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.StaticLayout
import android.text.TextPaint
import android.text.style.AlignmentSpan
import android.text.style.RelativeSizeSpan
import android.text.style.StyleSpan
import java.io.File
import java.util.zip.ZipFile
import kotlin.math.min

class ReflowSource private constructor(
    private val loader: ChapterLoader,
    private val closer: () -> Unit
) : BookSource {

    class OutlineRef(val title: String, val chapter: Int, val offset: Int = -1, val ratio: Float = -1f, val level: Int = 0)

    interface ChapterLoader {
        val chapterCount: Int
        fun load(index: Int, maxImageWidth: Int, maxImageHeight: Int): CharSequence
        fun outlineRefs(): List<OutlineRef> = emptyList()
    }

    private class PageSlice(val layout: StaticLayout, val top: Int, val bottom: Int)

    private val pages = ArrayList<PageSlice>()
    private val chapterFirst = HashMap<Int, Int>()
    private val chapterLast = HashMap<Int, Int>()
    private val chapterLayout = HashMap<Int, StaticLayout>()
    private val chapterTitle = HashMap<Int, String>()
    private var layoutWidth = 1
    private var layoutHeight = 1
    private var marginX = 0f
    private var marginY = 0f

    override val pageCount: Int get() = pages.size
    override val aspect = 0.68f
    override val reflowable = true

    @Synchronized
    override fun prepare(layoutWidth: Int, layoutHeight: Int, textScale: Float) {
        this.layoutWidth = layoutWidth
        this.layoutHeight = layoutHeight
        pages.clear()
        chapterFirst.clear()
        chapterLast.clear()
        chapterLayout.clear()
        chapterTitle.clear()
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
            val raw = try {
                loader.load(c, contentW, (contentH * 0.85f).toInt())
            } catch (_: Exception) {
                continue
            }
            if (raw.isBlank()) continue
            val text = HeadingStyler.style(raw)
            firstLineTitle(text)?.let { chapterTitle[c] = it }
            val builder = StaticLayout.Builder.obtain(text, 0, text.length, paint, contentW)
                .setIncludePad(false)
                .setLineSpacing(0f, 1.28f)
                .setBreakStrategy(Layout.BREAK_STRATEGY_HIGH_QUALITY)
                .setHyphenationFrequency(Layout.HYPHENATION_FREQUENCY_NORMAL)
            if (Build.VERSION.SDK_INT >= 26) builder.setJustificationMode(Layout.JUSTIFICATION_MODE_INTER_WORD)
            val layout = builder.build()
            chapterFirst[c] = pages.size
            chapterLayout[c] = layout
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
            chapterLast[c] = pages.size - 1
        }
        if (pages.isEmpty()) {
            val empty = StaticLayout.Builder.obtain("This book has no readable text.", 0, 31, paint, contentW).build()
            pages.add(PageSlice(empty, 0, empty.height))
        }
    }

    private fun firstLineTitle(text: CharSequence): String? {
        val end = text.indexOf('\n').let { if (it < 0) text.length else it }
        val line = text.subSequence(0, end).toString().replace('\uFFFC', ' ').trim()
        if (line.isEmpty() || line.length > 70) return null
        return line
    }

    @Synchronized
    override fun outline(): List<OutlineEntry> {
        if (chapterFirst.isEmpty()) return emptyList()
        val out = ArrayList<OutlineEntry>()
        for (ref in loader.outlineRefs()) {
            var c = ref.chapter
            while (c < loader.chapterCount && chapterFirst[c] == null) c++
            val first = chapterFirst[c] ?: continue
            val last = chapterLast[c] ?: first
            var page = first
            if (c == ref.chapter) {
                val layout = chapterLayout[c]
                if (ref.offset >= 0 && layout != null) {
                    val line = layout.getLineForOffset(ref.offset.coerceAtMost(layout.text.length))
                    val top = layout.getLineTop(line)
                    var p = first
                    while (p < last && pages[p].bottom <= top) p++
                    page = p
                } else if (ref.ratio >= 0f && last > first) {
                    page = first + (ref.ratio * (last - first + 1)).toInt().coerceIn(0, last - first)
                }
            }
            if (out.isNotEmpty() && out.last().page == page && out.last().title == ref.title) continue
            out.add(OutlineEntry(ref.title, page, ref.level))
        }
        if (out.size >= 2) return out
        val fallback = ArrayList<OutlineEntry>()
        for (c in 0 until loader.chapterCount) {
            val first = chapterFirst[c] ?: continue
            fallback.add(OutlineEntry(chapterTitle[c] ?: "Part ${fallback.size + 1}", first))
        }
        return if (fallback.size >= 2) fallback else out
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

    object HeadingStyler {
        fun style(source: CharSequence): CharSequence {
            val out = SpannableStringBuilder(source)
            val text = out.toString()
            if (source is Spanned) {
                for (span in source.getSpans(0, source.length, RelativeSizeSpan::class.java)) {
                    if (span.sizeChange < 1.15f) continue
                    val s = source.getSpanStart(span)
                    val e = source.getSpanEnd(span)
                    center(out, text, s, e)
                }
            }
            return out
        }

        fun center(out: SpannableStringBuilder, text: String, s: Int, e: Int) {
            val ps = text.lastIndexOf('\n', (s - 1).coerceAtLeast(0)).let { if (s == 0) 0 else it + 1 }
            var pe = text.indexOf('\n', e)
            if (pe < 0) pe = text.length
            if (pe - ps > 140 || pe <= ps) return
            val end = if (pe < text.length) pe + 1 else pe
            if (out.getSpans(ps, end, AlignmentSpan::class.java).isNotEmpty()) return
            out.setSpan(AlignmentSpan.Standard(Layout.Alignment.ALIGN_CENTER), ps, end, Spannable.SPAN_PARAGRAPH)
        }

        fun title(out: SpannableStringBuilder, s: Int, e: Int) {
            out.setSpan(StyleSpan(Typeface.BOLD), s, e, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
            out.setSpan(RelativeSizeSpan(1.4f), s, e, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
            center(out, out.toString(), s, e)
        }
    }

    private class EpubLoader(file: File, val parser: EpubParser) : ChapterLoader {
        private val zip = ZipFile(file)
        override val chapterCount = parser.spine.size

        override fun load(index: Int, maxImageWidth: Int, maxImageHeight: Int): CharSequence {
            val path = parser.spine[index]
            val raw = parser.readChapter(zip, path) ?: return ""
            val html = EpubParser.bodyOf(raw)
            val base = path.substringBeforeLast('/', "")
            val getter = Html.ImageGetter { src -> imageFor(base, src, maxImageWidth, maxImageHeight) }
            val spanned = Html.fromHtml(html, Html.FROM_HTML_MODE_LEGACY, getter, null)
            return styleFirstLine(trimTrailing(spanned))
        }

        private fun styleFirstLine(text: CharSequence): CharSequence {
            if (text.length < 300) return text
            val out = SpannableStringBuilder(text)
            val s = out.toString()
            val end = s.indexOf('\n').let { if (it < 0) s.length else it }
            val line = s.substring(0, end).trim()
            if (line.isEmpty() || line.length > 90 || line.contains('\uFFFC') || Regex("[.!?,;:।]$").containsMatchIn(line)) return text
            val covered = out.getSpans(0, end, RelativeSizeSpan::class.java).any { it.sizeChange >= 1.15f }
            if (!covered) HeadingStyler.title(out, 0, end)
            return out
        }

        override fun outlineRefs(): List<OutlineRef> {
            val result = ArrayList<OutlineRef>()
            for (e in parser.toc) {
                val chapter = parser.spine.indexOf(e.path)
                if (chapter < 0) continue
                var ratio = -1f
                if (e.fragment != null) {
                    val raw = parser.readChapter(zip, e.path)
                    if (raw != null && raw.isNotEmpty()) {
                        val at = Regex("(?:id|name)\\s*=\\s*[\"']" + Regex.escape(e.fragment) + "[\"']").find(raw)?.range?.first
                        if (at != null) ratio = at.toFloat() / raw.length
                    }
                }
                result.add(OutlineRef(e.title, chapter, -1, ratio, e.level))
            }
            return result
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

    private class TextLoader(private val chunks: List<Pair<String?, String>>) : ChapterLoader {
        override val chapterCount = chunks.size

        override fun load(index: Int, maxImageWidth: Int, maxImageHeight: Int): CharSequence {
            val (heading, body) = chunks[index]
            val out = SpannableStringBuilder(body)
            if (heading != null) {
                val end = body.indexOf('\n').let { if (it < 0) body.length else it }
                HeadingStyler.title(out, 0, end)
            }
            return out
        }

        override fun outlineRefs(): List<OutlineRef> =
            chunks.mapIndexedNotNull { i, c -> c.first?.let { OutlineRef(it, i) } }
    }

    companion object {
        fun fromEpub(file: File): ReflowSource {
            val loader = EpubLoader(file, EpubParser(file))
            return ReflowSource(loader) { loader.close() }
        }

        fun fromText(file: File): ReflowSource {
            val bytes = file.readBytes()
            val utf8 = String(bytes, Charsets.UTF_8)
            val text = if (utf8.count { it == '\uFFFD' } > bytes.size / 200) String(bytes, Charsets.ISO_8859_1) else utf8
            return ReflowSource(TextLoader(splitText(text.replace("\r\n", "\n").replace('\r', '\n')))) { }
        }

        fun splitText(text: String): List<Pair<String?, String>> {
            val lines = text.split('\n')
            val chunks = ArrayList<Pair<String?, String>>()
            var heading: String? = null
            val body = StringBuilder()
            fun flush() {
                if (body.isNotBlank()) chunks.add(Pair(heading, body.toString().trimEnd()))
                body.setLength(0)
            }
            var i = 0
            while (i < lines.size) {
                val line = lines[i]
                val t = line.trim()
                val blankBefore = i == 0 || lines[i - 1].isBlank()
                val blankAfter = i + 1 >= lines.size || lines[i + 1].isBlank()
                val isHeading = t.isNotEmpty() && (Headings.isStrong(t) || (blankBefore && blankAfter && Headings.isAllCaps(t)))
                if (isHeading) {
                    flush()
                    var title = t
                    var consumed = 0
                    val nextIdx = (i + 1 until lines.size).firstOrNull { lines[it].isNotBlank() }
                    if (nextIdx != null && nextIdx - i <= 2) {
                        val (merged, used) = Headings.mergeChapterTitle(title, lines[nextIdx].trim())
                        if (used) {
                            title = merged
                            consumed = nextIdx - i
                        }
                    }
                    heading = title
                    body.append(title).append("\n\n")
                    i += consumed + 1
                    continue
                }
                body.append(line).append('\n')
                i++
            }
            flush()
            if (chunks.isEmpty()) chunks.add(Pair(null, text))
            if (chunks.size == 1 && chunks[0].second.length > 400000) {
                val big = chunks[0].second
                chunks.clear()
                var from = 0
                while (from < big.length) {
                    var to = min(big.length, from + 200000)
                    if (to < big.length) {
                        val nl = big.indexOf('\n', to)
                        to = if (nl < 0) big.length else nl + 1
                    }
                    chunks.add(Pair(null, big.substring(from, to)))
                    from = to
                }
            }
            return chunks
        }
    }
}
