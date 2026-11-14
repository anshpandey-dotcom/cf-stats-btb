package com.fleetmc.kuchlikh.export

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.pdf.PdfDocument
import android.os.Build
import android.text.Layout
import android.text.SpannableString
import android.text.Spanned
import android.text.StaticLayout
import android.text.TextPaint
import android.text.TextUtils
import android.text.style.LineHeightSpan
import com.fleetmc.kuchlikh.data.Book
import com.fleetmc.kuchlikh.data.BookStore
import com.fleetmc.kuchlikh.data.Page
import com.fleetmc.kuchlikh.data.PageSpec
import com.fleetmc.kuchlikh.data.chapters
import com.fleetmc.kuchlikh.editor.Flow
import com.fleetmc.kuchlikh.editor.PageText
import com.fleetmc.kuchlikh.ui.CoverPainter
import com.fleetmc.kuchlikh.ui.Fonts
import com.fleetmc.kuchlikh.ui.PageStyles
import java.io.File
import java.io.OutputStream
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.math.ceil

private class FixedLine(private val height: Int) : LineHeightSpan {
    override fun chooseHeight(
        text: CharSequence?,
        start: Int,
        end: Int,
        spanstartv: Int,
        lineHeight: Int,
        fm: Paint.FontMetricsInt,
    ) {
        val current = fm.descent - fm.ascent
        if (current <= 0) return
        val extra = height - current
        fm.descent += ceil(extra * 0.5f).toInt()
        fm.ascent = fm.descent - height
        fm.top = fm.ascent
        fm.bottom = fm.descent
    }
}

class PagePainter(private val context: Context, private val store: BookStore, private val book: Book) {

    private val size = PageSpec.size(book.textSize)
    private val canon = PageSpec.CANON

    private val bodyPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Fonts.typeface(context, book.font, false)
        textSize = size.font * canon
    }
    private val titlePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Fonts.typeface(context, book.font, true)
        textSize = size.font * 1.45f
    }
    private val smallPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Fonts.typeface(context, "sans", false)
    }
    private val bitmapPaint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)

    private fun bitmap(name: String, maxSide: Int = 1400): Bitmap? {
        val file = store.file(book.id, name)
        if (!file.exists()) return null
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.path, bounds)
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxSide) sample *= 2
        return BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply { inSampleSize = sample })
    }

    fun cover(canvas: Canvas) {
        val image = book.cover?.let { bitmap(it, 1800) }
        if (image != null) {
            CoverPainter.drawBitmap(canvas, image)
            image.recycle()
        } else {
            CoverPainter.drawGenerated(context, canvas, book.title, book.author, book.coverColor)
        }
    }

    fun contents(canvas: Canvas) {
        val style = PageStyles.get(book.pageStyle)
        PageStyles.paint(canvas, PageStyles.get(if (style.id == "night") "night" else "plain"), size.line)
        val ink = if (style.id == "night") style.ink else 0xFF1D1D1F.toInt()
        titlePaint.color = ink
        canvas.drawText("Contents", PageSpec.PAD_X.toFloat(), 78f, titlePaint)
        smallPaint.textSize = 11.5f
        smallPaint.letterSpacing = 0f
        smallPaint.color = ink
        var y = 118f
        val right = (PageSpec.W - PageSpec.PAD_X).toFloat()
        for (chapter in book.chapters()) {
            if (y > PageSpec.H - 50) break
            val label = "${chapter.number}.  " + chapter.title.ifBlank { "Chapter ${chapter.number}" }
            val number = (chapter.pageIndex + 1).toString()
            val room = right - PageSpec.PAD_X - smallPaint.measureText(number) - 14f
            canvas.drawText(TextUtils.ellipsize(label, smallPaint, room, TextUtils.TruncateAt.END).toString(), PageSpec.PAD_X.toFloat(), y, smallPaint)
            canvas.drawText(number, right - smallPaint.measureText(number), y, smallPaint)
            y += 24f
        }
    }

    fun page(canvas: Canvas, page: Page, number: Int, chapterNumber: Int) {
        val style = PageStyles.get(page.style ?: book.pageStyle)
        PageStyles.paint(canvas, style, size.line)
        val left = PageSpec.PAD_X.toFloat()
        var y = PageSpec.PAD_TOP.toFloat()
        val imageLines = PageSpec.imageLines(page, book.textSize)
        val textLines = PageSpec.textLines(page, book.textSize)
        val gap = PageSpec.IMAGE_GAP.toFloat()

        if (page.chapter != null) {
            heading(canvas, page.chapter, chapterNumber, y, style.ink, style.muted)
            y += PageSpec.HEADING_LINES * size.line
        }
        if (page.image != null && !page.imageBottom) {
            image(canvas, page.image, y, imageLines * size.line - gap)
            y += imageLines * size.line
        }
        if (textLines > 0) {
            if (page.text.isNotEmpty()) text(canvas, page.text, left, y, textLines, style.ink)
            y += textLines * size.line
        }
        if (page.image != null && page.imageBottom) {
            image(canvas, page.image, y + gap, imageLines * size.line - gap)
        }

        smallPaint.textSize = 10f
        smallPaint.letterSpacing = 0f
        smallPaint.color = style.muted
        val label = number.toString()
        canvas.drawText(label, (PageSpec.W - smallPaint.measureText(label)) / 2f, PageSpec.H - 18f, smallPaint)
    }

    private fun heading(canvas: Canvas, title: String, number: Int, top: Float, ink: Int, muted: Int) {
        val block = PageSpec.HEADING_LINES * size.line.toFloat()
        titlePaint.color = ink
        val shown = title.ifBlank { "Chapter $number" }
        val layout = StaticLayout.Builder.obtain(shown, 0, shown.length, titlePaint, PageSpec.CONTENT_W)
            .setLineSpacing(0f, 1.15f)
            .setMaxLines(2)
            .setEllipsize(TextUtils.TruncateAt.END)
            .setIncludePad(false)
            .build()
        val total = 16f + layout.height
        val start = top + (block - total) / 2f
        smallPaint.textSize = 9.5f
        smallPaint.letterSpacing = 0.2f
        smallPaint.color = muted
        canvas.drawText("CHAPTER $number", PageSpec.PAD_X.toFloat(), start + 9f, smallPaint)
        canvas.save()
        canvas.translate(PageSpec.PAD_X.toFloat(), start + 16f)
        layout.draw(canvas)
        canvas.restore()
    }

    private fun image(canvas: Canvas, name: String, top: Float, height: Float) {
        val picture = bitmap(name) ?: return
        val width = PageSpec.CONTENT_W.toFloat()
        val scale = minOf(width / picture.width, height / picture.height)
        val w = picture.width * scale
        val h = picture.height * scale
        val x = PageSpec.PAD_X + (width - w) / 2f
        val y = top + (height - h) / 2f
        canvas.drawBitmap(picture, null, RectF(x, y, x + w, y + h), bitmapPaint)
        picture.recycle()
    }

    private fun text(canvas: Canvas, text: String, left: Float, top: Float, lines: Int, ink: Int) {
        bodyPaint.color = ink
        val spanned = SpannableString(PageText.MARK_TEXT + text)
        spanned.setSpan(FixedLine(Math.round(size.line * canon)), 0, spanned.length, Spanned.SPAN_INCLUSIVE_INCLUSIVE)
        val builder = StaticLayout.Builder.obtain(spanned, 0, spanned.length, bodyPaint, Math.round(PageSpec.CONTENT_W * canon))
            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .setIncludePad(false)
            .setBreakStrategy(Layout.BREAK_STRATEGY_SIMPLE)
            .setHyphenationFrequency(Layout.HYPHENATION_FREQUENCY_NONE)
        if (book.justify && Build.VERSION.SDK_INT >= 26) {
            builder.setJustificationMode(Layout.JUSTIFICATION_MODE_INTER_WORD)
        }
        val layout = builder.build()
        val squeeze = if (layout.lineCount > lines) lines / layout.lineCount.toFloat() else 1f
        canvas.save()
        canvas.translate(left, top)
        canvas.scale(1f / canon, squeeze / canon)
        layout.draw(canvas)
        canvas.restore()
    }
}

object Exporter {

    private const val PDF_W = 420
    private const val PDF_H = 595

    fun fileName(book: Book, extension: String): String {
        val base = book.title.replace(Regex("[\\\\/:*?\"<>|\\n\\r]+"), " ").trim().ifBlank { "Book" }
        return "$base.$extension"
    }

    private fun target(context: Context, book: Book, extension: String): File {
        val dir = File(context.cacheDir, "exports").apply { mkdirs() }
        dir.listFiles().orEmpty().forEach { it.delete() }
        return File(dir, fileName(book, extension))
    }

    fun pdf(context: Context, store: BookStore, book: Book): File {
        val painter = PagePainter(context, store, book)
        val document = PdfDocument()
        var index = 1
        val scale = PDF_W / PageSpec.W.toFloat()

        fun sheet(draw: (Canvas) -> Unit) {
            val page = document.startPage(PdfDocument.PageInfo.Builder(PDF_W, PDF_H, index++).create())
            page.canvas.save()
            page.canvas.scale(scale, scale)
            draw(page.canvas)
            page.canvas.restore()
            document.finishPage(page)
        }

        sheet { painter.cover(it) }
        if (book.chapters().isNotEmpty()) sheet { painter.contents(it) }
        var chapter = 0
        book.pages.forEachIndexed { i, page ->
            if (page.chapter != null) chapter++
            val number = chapter
            sheet { painter.page(it, page, i + 1, number) }
        }
        val file = target(context, book, "pdf")
        file.outputStream().use { document.writeTo(it) }
        document.close()
        return file
    }

    private fun escape(text: String): String = text
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")

    private class Section(val title: String, val body: StringBuilder = StringBuilder())

    private fun sections(book: Book, images: MutableList<String>): List<Section> {
        val out = ArrayList<Section>()
        var current: Section? = null
        var chapter = 0
        val paragraph = StringBuilder()

        fun flushParagraph() {
            val target = current ?: return
            val text = paragraph.toString()
            paragraph.setLength(0)
            text.split("\n").forEach { line ->
                if (line.isNotBlank()) target.body.append("<p>").append(escape(line.trim())).append("</p>\n")
            }
        }

        book.pages.forEachIndexed { index, page ->
            if (page.chapter != null) {
                flushParagraph()
                chapter++
                current = Section(page.chapter.ifBlank { "Chapter $chapter" }).also { out += it }
            } else if (current == null) {
                current = Section(book.title).also { out += it }
            } else if (Flow.starts(book.pages, index)) {
                paragraph.append('\n')
            }
            val figure = page.image?.let { name ->
                images += name
                "<div class=\"figure\"><img src=\"images/$name\" alt=\"\"/></div>\n"
            }
            if (figure != null && !page.imageBottom) {
                flushParagraph()
                current!!.body.append(figure)
            }
            paragraph.append(page.text)
            if (page.para) paragraph.append('\n')
            if (figure != null && page.imageBottom) {
                flushParagraph()
                current!!.body.append(figure)
            }
        }
        flushParagraph()
        return out
    }

    fun epub(context: Context, store: BookStore, book: Book): File {
        val images = ArrayList<String>()
        val parts = sections(book, images)
        val painter = PagePainter(context, store, book)
        val cover = Bitmap.createBitmap(PageSpec.W * 3, PageSpec.H * 3, Bitmap.Config.ARGB_8888)
        Canvas(cover).apply {
            scale(3f, 3f)
            painter.cover(this)
        }
        val title = escape(book.title.ifBlank { "Untitled" })
        val author = escape(book.author.ifBlank { "Unknown" })
        val file = target(context, book, "epub")

        ZipOutputStream(file.outputStream().buffered()).use { zip ->
            val mime = "application/epub+zip".toByteArray()
            val entry = ZipEntry("mimetype").apply {
                method = ZipEntry.STORED
                size = mime.size.toLong()
                compressedSize = mime.size.toLong()
                crc = CRC32().apply { update(mime) }.value
            }
            zip.putNextEntry(entry)
            zip.write(mime)
            zip.closeEntry()

            fun put(name: String, write: (OutputStream) -> Unit) {
                zip.putNextEntry(ZipEntry(name))
                write(zip)
                zip.closeEntry()
            }

            fun putText(name: String, text: String) = put(name) { it.write(text.toByteArray()) }

            putText(
                "META-INF/container.xml",
                """<?xml version="1.0" encoding="UTF-8"?>
<container version="1.0" xmlns="urn:oasis:names:tc:opendocument:xmlns:container">
  <rootfiles>
    <rootfile full-path="OEBPS/content.opf" media-type="application/oebps-package+xml"/>
  </rootfiles>
</container>
"""
            )
            putText(
                "OEBPS/style.css",
                """body { font-family: serif; line-height: 1.6; margin: 5%; }
h1 { font-size: 1.6em; margin: 2em 0 1em; }
p { margin: 0 0 0.9em; text-align: ${if (book.justify) "justify" else "left"}; }
.figure { text-align: center; margin: 1.2em 0; }
.figure img { max-width: 100%; }
.cover { text-align: center; margin: 0; }
.cover img { max-width: 100%; max-height: 100%; }
"""
            )
            put("OEBPS/images/cover.jpg") { cover.compress(Bitmap.CompressFormat.JPEG, 90, it) }
            images.distinct().forEach { name ->
                val source = store.file(book.id, name)
                if (source.exists()) put("OEBPS/images/$name") { out -> source.inputStream().use { it.copyTo(out) } }
            }

            val head = { heading: String ->
                """<?xml version="1.0" encoding="UTF-8"?>
<html xmlns="http://www.w3.org/1999/xhtml" xmlns:epub="http://www.idpf.org/2007/ops">
<head><title>$heading</title><link rel="stylesheet" type="text/css" href="style.css"/></head>
"""
            }
            putText(
                "OEBPS/cover.xhtml",
                head(title) + "<body><div class=\"cover\"><img src=\"images/cover.jpg\" alt=\"$title\"/></div></body>\n</html>\n"
            )
            parts.forEachIndexed { index, part ->
                val name = escape(part.title)
                putText("OEBPS/part${index + 1}.xhtml", head(name) + "<body>\n<h1>$name</h1>\n${part.body}</body>\n</html>\n")
            }
            putText(
                "OEBPS/nav.xhtml",
                head(title) + "<body>\n<nav epub:type=\"toc\"><h1>Contents</h1>\n<ol>\n" +
                    parts.mapIndexed { index, part -> "<li><a href=\"part${index + 1}.xhtml\">${escape(part.title)}</a></li>" }
                        .joinToString("\n") + "\n</ol>\n</nav>\n</body>\n</html>\n"
            )
            putText(
                "OEBPS/toc.ncx",
                """<?xml version="1.0" encoding="UTF-8"?>
<ncx xmlns="http://www.daisy.org/z3986/2005/ncx/" version="2005-1">
<head><meta name="dtb:uid" content="kuchlikh-${book.id}"/></head>
<docTitle><text>$title</text></docTitle>
<navMap>
""" + parts.mapIndexed { index, part ->
                    "<navPoint id=\"p${index + 1}\" playOrder=\"${index + 1}\"><navLabel><text>${escape(part.title)}</text></navLabel><content src=\"part${index + 1}.xhtml\"/></navPoint>"
                }.joinToString("\n") + "\n</navMap>\n</ncx>\n"
            )

            val manifest = StringBuilder()
            manifest.append("<item id=\"nav\" href=\"nav.xhtml\" media-type=\"application/xhtml+xml\" properties=\"nav\"/>\n")
            manifest.append("<item id=\"ncx\" href=\"toc.ncx\" media-type=\"application/x-dtbncx+xml\"/>\n")
            manifest.append("<item id=\"css\" href=\"style.css\" media-type=\"text/css\"/>\n")
            manifest.append("<item id=\"cover\" href=\"cover.xhtml\" media-type=\"application/xhtml+xml\"/>\n")
            manifest.append("<item id=\"cover-image\" href=\"images/cover.jpg\" media-type=\"image/jpeg\" properties=\"cover-image\"/>\n")
            images.distinct().forEachIndexed { index, name ->
                manifest.append("<item id=\"img$index\" href=\"images/$name\" media-type=\"image/jpeg\"/>\n")
            }
            val spine = StringBuilder("<itemref idref=\"cover\"/>\n")
            parts.indices.forEach { index ->
                manifest.append("<item id=\"part${index + 1}\" href=\"part${index + 1}.xhtml\" media-type=\"application/xhtml+xml\"/>\n")
                spine.append("<itemref idref=\"part${index + 1}\"/>\n")
            }
            val modified = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", java.util.Locale.US)
                .apply { timeZone = java.util.TimeZone.getTimeZone("UTC") }
                .format(java.util.Date())
            putText(
                "OEBPS/content.opf",
                """<?xml version="1.0" encoding="UTF-8"?>
<package xmlns="http://www.idpf.org/2007/opf" version="3.0" unique-identifier="uid">
<metadata xmlns:dc="http://purl.org/dc/elements/1.1/">
<dc:identifier id="uid">kuchlikh-${book.id}</dc:identifier>
<dc:title>$title</dc:title>
<dc:creator>$author</dc:creator>
<dc:language>en</dc:language>
<meta property="dcterms:modified">$modified</meta>
<meta name="cover" content="cover-image"/>
</metadata>
<manifest>
$manifest</manifest>
<spine toc="ncx">
$spine</spine>
</package>
"""
            )
        }
        cover.recycle()
        return file
    }
}
