package com.fleetmc.flipshelf.translate

import android.content.Context
import android.os.ParcelFileDescriptor
import android.text.Html
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
        val total = chapters.sumOf { c -> c.paragraphs.sumOf { it.length } }
        if (total < 200) throw NoTextException("No selectable text found. Scanned books cannot be translated")
        return chapters
    }

    fun fromEpub(file: File): List<Chapter> {
        val parser = EpubParser(file)
        val out = ArrayList<Chapter>()
        ZipFile(file).use { zip ->
            for (path in parser.spine) {
                val raw = parser.readChapter(zip, path) ?: continue
                val text = Html.fromHtml(EpubParser.bodyOf(raw), Html.FROM_HTML_MODE_LEGACY).toString().replace('￼', ' ')
                val lines = text.split('\n').map { it.trim() }.filter { it.isNotEmpty() }
                if (lines.isEmpty()) continue
                val heading = if (lines.size > 1 && lines[0].length <= 80 && !lines[0].endsWith(".")) lines[0] else null
                out.add(Chapter(heading, if (heading != null) lines.drop(1) else lines))
            }
        }
        return out
    }

    fun fromText(file: File): List<Chapter> {
        val bytes = file.readBytes()
        val utf8 = String(bytes, Charsets.UTF_8)
        val text = if (utf8.count { it == '�' } > bytes.size / 200) String(bytes, Charsets.ISO_8859_1) else utf8
        val paragraphs = text.replace("\r\n", "\n").split(Regex("\n\\s*\n"))
            .map { it.replace('\n', ' ').trim() }
            .filter { it.isNotEmpty() }
        val chapters = ArrayList<Chapter>()
        var bucket = ArrayList<String>()
        var size = 0
        for (p in paragraphs) {
            bucket.add(p)
            size += p.length
            if (size >= 9000) {
                chapters.add(Chapter(null, bucket))
                bucket = ArrayList()
                size = 0
            }
        }
        if (bucket.isNotEmpty()) chapters.add(Chapter(null, bucket))
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
