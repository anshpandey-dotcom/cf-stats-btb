package com.fleetmc.flipshelf.translate

import android.content.Context
import android.os.ParcelFileDescriptor
import android.graphics.Typeface
import android.text.Html
import android.text.Spanned
import android.text.style.RelativeSizeSpan
import android.text.style.StyleSpan
import com.fleetmc.flipshelf.core.ReflowSource
import com.fleetmc.flipshelf.core.Book
import com.fleetmc.flipshelf.core.EpubParser
import com.fleetmc.flipshelf.core.Format
import com.fleetmc.flipshelf.core.Library
import io.legere.pdfiumandroid.PdfiumCore
import java.io.File
import java.util.zip.ZipFile

object TextExtractor {

    fun extract(context: Context, library: Library, book: Book, isCancelled: () -> Boolean): List<Chapter> {
        val file = library.fileOf(book)
        val chapters = when (book.format) {
            Format.EPUB -> fromEpub(file)
            Format.TEXT -> fromText(file)
            Format.PDF -> fromPdf(context, file, isCancelled)
            else -> throw NoTextException("This book is made of pictures, there is no text to translate")
        }
        val total = chapters.sumOf { c -> c.blocks.sumOf { it.text.length } }
        if (total < 200) throw NoTextException("No selectable text found. Scanned books cannot be translated")
        return chapters
    }

    fun fromEpub(file: File): List<Chapter> {
        val parser = EpubParser(file)
        val out = ArrayList<Chapter>()
        ZipFile(file).use { zip ->
            for (path in parser.spine) {
                val raw = parser.readChapter(zip, path) ?: continue
                val spanned = Html.fromHtml(EpubParser.bodyOf(raw), Html.FROM_HTML_MODE_LEGACY)
                var blocks = blocksOf(spanned)
                if (blocks.isEmpty()) continue
                val tocTitle = parser.titleFor(path)
                val first = blocks.first()
                var heading: String? = null
                if (first.level in 1..2 && first.text.length <= 140) {
                    heading = first.text
                    blocks = blocks.drop(1)
                } else if (tocTitle != null) {
                    heading = tocTitle
                } else if (blocks.size > 1 && first.text.length <= 90 && !Regex("[.!?,;:।]$").containsMatchIn(first.text)) {
                    heading = first.text
                    blocks = blocks.drop(1)
                }
                if (heading == null && blocks.sumOf { it.text.length } < 20) continue
                out.add(Chapter(heading, blocks))
            }
        }
        return out
    }

    fun blocksOf(spanned: Spanned): List<Block> {
        val text = spanned.toString().replace('\uFFFC', ' ')
        val blocks = ArrayList<Block>()
        var pos = 0
        for (rawLine in text.split('\n')) {
            val start = pos
            val end = pos + rawLine.length
            pos = end + 1
            val line = rawLine.trim()
            if (line.isEmpty()) continue
            blocks.add(Block(line, headingLevel(spanned, start, end, line)))
        }
        return blocks
    }

    private fun headingLevel(spanned: Spanned, start: Int, end: Int, line: String): Int {
        if (end <= start) return 0
        val len = (end - start).toFloat()
        var level = 0
        for (span in spanned.getSpans(start, end, RelativeSizeSpan::class.java)) {
            val overlap = (minOf(end, spanned.getSpanEnd(span)) - maxOf(start, spanned.getSpanStart(span))) / len
            if (overlap < 0.5f) continue
            val l = when {
                span.sizeChange >= 1.4f -> 1
                span.sizeChange >= 1.25f -> 2
                span.sizeChange >= 1.1f -> 3
                else -> 0
            }
            if (l != 0 && (level == 0 || l < level)) level = l
        }
        if (level != 0) return level
        if (line.length <= 100) {
            for (span in spanned.getSpans(start, end, StyleSpan::class.java)) {
                if (span.style != Typeface.BOLD) continue
                val overlap = (minOf(end, spanned.getSpanEnd(span)) - maxOf(start, spanned.getSpanStart(span))) / len
                if (overlap >= 0.9f) return 3
            }
        }
        return 0
    }

    fun fromText(file: File): List<Chapter> {
        val bytes = file.readBytes()
        val utf8 = String(bytes, Charsets.UTF_8)
        val text = if (utf8.count { it == '\uFFFD' } > bytes.size / 200) String(bytes, Charsets.ISO_8859_1) else utf8
        val chapters = ArrayList<Chapter>()
        for ((heading, body) in ReflowSource.splitText(text.replace("\r\n", "\n").replace('\r', '\n'))) {
            val trimmed = if (heading != null && body.startsWith(heading)) body.substring(heading.length) else body
            val paragraphs = trimmed.split(Regex("\n\\s*\n"))
                .map { it.replace('\n', ' ').trim() }
                .filter { it.isNotEmpty() }
            var bucket = ArrayList<Block>()
            var size = 0
            var title = heading
            for (p in paragraphs) {
                bucket.add(Block(p))
                size += p.length
                if (size >= 14000) {
                    chapters.add(Chapter(title, bucket))
                    title = null
                    bucket = ArrayList()
                    size = 0
                }
            }
            if (bucket.isNotEmpty() || title != null) chapters.add(Chapter(title, bucket))
        }
        return chapters
    }

    private fun fromPdf(context: Context, file: File, isCancelled: () -> Boolean): List<Chapter> {
        val descriptor = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
        val core = PdfiumCore(context.applicationContext)
        val document = core.newDocument(descriptor)
        val pages = ArrayList<String>()
        try {
            for (i in 0 until document.getPageCount()) {
                if (isCancelled()) break
                val page = document.openPage(i)
                try {
                    val textPage = page.openTextPage()
                    try {
                        val count = textPage.textPageCountChars()
                        pages.add(if (count > 0) textPage.textPageGetText(0, count) ?: "" else "")
                    } finally {
                        textPage.close()
                    }
                } finally {
                    page.close()
                }
            }
        } finally {
            document.close()
            descriptor.close()
        }
        return PdfParagraphs.build(pages)
    }
}
