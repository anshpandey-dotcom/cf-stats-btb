package com.ansh.recalm.importer

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import android.graphics.Typeface
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import com.ansh.recalm.data.Block
import com.ansh.recalm.data.BlockKind
import com.ansh.recalm.data.Book
import com.ansh.recalm.data.Library
import com.ansh.recalm.data.Page
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.devanagari.DevanagariTextRecognizerOptions
import com.google.android.gms.tasks.Tasks
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.io.MemoryUsageSetting
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import org.jsoup.parser.Parser
import java.io.File
import java.nio.charset.Charset
import java.util.UUID
import java.util.zip.ZipFile

class PendingImport(
    val id: String,
    val format: String,
    val file: File,
    val suggestedTitle: String,
    val covers: List<Bitmap>
)

class Importer(private val context: Context, private val library: Library) {

    init {
        PDFBoxResourceLoader.init(context.applicationContext)
    }

    private val recognizer by lazy { TextRecognition.getClient(DevanagariTextRecognizerOptions.Builder().build()) }

    fun prepare(uri: Uri): PendingImport {
        val name = displayName(uri)
        val ext = name.substringAfterLast('.', "").lowercase()
        val mime = context.contentResolver.getType(uri) ?: ""
        val format = when {
            ext == "pdf" || mime.contains("pdf") -> "pdf"
            ext == "epub" || mime.contains("epub") -> "epub"
            else -> "txt"
        }
        val id = UUID.randomUUID().toString()
        val dir = library.dir(id).apply { mkdirs() }
        val file = File(dir, "source.$format")
        context.contentResolver.openInputStream(uri)!!.use { input -> file.outputStream().use { input.copyTo(it) } }
        val title = name.substringBeforeLast('.').replace('_', ' ').replace(Regex("\\s+"), " ").trim().ifBlank { "Untitled" }
        val covers = when (format) {
            "pdf" -> pdfCovers(file) + generatedCovers(title).take(1)
            "epub" -> listOfNotNull(epubCover(file)) + generatedCovers(title)
            else -> generatedCovers(title)
        }
        return PendingImport(id, format, file, title, covers)
    }

    fun preparePasted(text: String, title: String): PendingImport {
        val id = UUID.randomUUID().toString()
        val dir = library.dir(id).apply { mkdirs() }
        val file = File(dir, "source.txt")
        file.writeText(text)
        val name = title.ifBlank { text.trim().lineSequence().firstOrNull()?.take(40) ?: "My text" }
        return PendingImport(id, "txt", file, name, generatedCovers(name))
    }

    fun discard(pending: PendingImport) {
        library.dir(pending.id).deleteRecursively()
    }

    fun complete(pending: PendingImport, title: String, cover: Bitmap?, onProgress: (Float, String) -> Unit): Book {
        val pages = when (pending.format) {
            "pdf" -> readPdf(pending.file, onProgress)
            "epub" -> readEpub(pending.file, onProgress)
            else -> readText(pending.file)
        }.ifEmpty { listOf(Page(listOf(Block("This file has no readable text.", BlockKind.BODY)))) }
        val finalTitle = title.ifBlank { pending.suggestedTitle }
        if (cover != null) library.saveCover(pending.id, cover)
        val book = Book(
            id = pending.id,
            title = finalTitle,
            format = pending.format,
            pageCount = pages.size,
            createdAt = System.currentTimeMillis(),
            lastOpened = System.currentTimeMillis(),
            hasCover = cover != null
        )
        library.add(book, pages)
        return book
    }

    private fun displayName(uri: Uri): String {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) return c.getString(0) ?: "Book"
        }
        return uri.lastPathSegment?.substringAfterLast('/') ?: "Book"
    }

    private fun pdfCovers(file: File): List<Bitmap> = runCatching {
        ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { fd ->
            PdfRenderer(fd).use { renderer ->
                (0 until minOf(5, renderer.pageCount)).map { i ->
                    renderer.openPage(i).use { page -> renderPage(page, 480) }
                }
            }
        }
    }.getOrDefault(emptyList())

    private fun renderPage(page: PdfRenderer.Page, width: Int): Bitmap {
        val height = (width.toFloat() * page.height / page.width).toInt().coerceAtLeast(1)
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(Color.WHITE)
        page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
        return bitmap
    }

    private fun readPdf(file: File, onProgress: (Float, String) -> Unit): List<Page> {
        val raw = mutableListOf<String>()
        PDDocument.load(file, MemoryUsageSetting.setupTempFileOnly()).use { doc ->
            val stripper = PDFTextStripper().apply {
                sortByPosition = true
                addMoreFormatting = false
            }
            val total = doc.numberOfPages
            for (p in 1..total) {
                stripper.startPage = p
                stripper.endPage = p
                raw += runCatching { stripper.getText(doc) }.getOrDefault("")
                if (p % 5 == 0 || p == total) onProgress(p.toFloat() / total * 0.8f, "Reading page $p of $total")
            }
        }
        val needOcr = raw.indices.filter { raw[it].count { c -> c.isLetter() } < 25 }
        if (needOcr.isNotEmpty()) {
            ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { fd ->
                PdfRenderer(fd).use { renderer ->
                    needOcr.forEachIndexed { n, index ->
                        onProgress(0.8f + 0.2f * n / needOcr.size, "Scanning page ${index + 1} with OCR")
                        raw[index] = ocrPage(renderer, index)
                    }
                }
            }
        }
        val pages = TextCleaner.buildPages(raw)
        return pages.mapIndexed { i, page -> if (i in needOcr) page.copy(ocr = true) else page }
    }

    fun ocrPage(renderer: PdfRenderer, index: Int): String = runCatching {
        val bitmap = renderer.openPage(index).use { renderPage(it, 1600) }
        val result = Tasks.await(recognizer.process(InputImage.fromBitmap(bitmap, 0)))
        bitmap.recycle()
        result.textBlocks.joinToString("\n\n") { block -> block.lines.joinToString("\n") { it.text } }
    }.getOrDefault("")

    fun ocrWholeBook(book: Book, onProgress: (Float, String) -> Unit): List<Page> {
        val file = library.sourceFile(book)
        val raw = mutableListOf<String>()
        ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { fd ->
            PdfRenderer(fd).use { renderer ->
                for (i in 0 until renderer.pageCount) {
                    onProgress(i.toFloat() / renderer.pageCount, "Scanning page ${i + 1} of ${renderer.pageCount}")
                    raw += ocrPage(renderer, i)
                }
            }
        }
        val pages = TextCleaner.buildPages(raw).map { it.copy(ocr = true) }
        library.writePages(book.id, pages)
        library.update(book.id) { it.copy(pageCount = pages.size) }
        return pages
    }

    private fun readText(file: File): List<Page> {
        val bytes = file.readBytes()
        val text = decode(bytes)
        val paragraphs = text.replace("\r\n", "\n").split(Regex("\\n\\s*\\n"))
            .flatMap { TextCleaner.paragraphs(it.lines()) }
        val blocks = TextCleaner.classify(paragraphs, TextCleaner.ClassifyState())
        return TextCleaner.paginate(blocks)
    }

    private fun decode(bytes: ByteArray): String {
        val utf8 = String(bytes, Charsets.UTF_8)
        if (!utf8.contains('�')) return utf8.removePrefix("﻿")
        if (bytes.size >= 2 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xFE.toByte()) return String(bytes, Charsets.UTF_16LE)
        if (bytes.size >= 2 && bytes[0] == 0xFE.toByte() && bytes[1] == 0xFF.toByte()) return String(bytes, Charsets.UTF_16BE)
        return String(bytes, Charset.forName("windows-1252"))
    }

    private fun readEpub(file: File, onProgress: (Float, String) -> Unit): List<Page> = ZipFile(file).use { zip ->
        val opfPath = opfPath(zip)
        val opf = Jsoup.parse(zip.getInputStream(zip.getEntry(opfPath)).readBytes().toString(Charsets.UTF_8), "", Parser.xmlParser())
        val base = opfPath.substringBeforeLast('/', "")
        val manifest = opf.select("manifest > item").associate { it.attr("id") to it.attr("href") }
        val spine = opf.select("spine > itemref").mapNotNull { manifest[it.attr("idref")] }
        val pages = mutableListOf<Page>()
        val state = TextCleaner.ClassifyState()
        spine.forEachIndexed { i, href ->
            onProgress(i.toFloat() / spine.size, "Reading chapter ${i + 1} of ${spine.size}")
            val path = resolve(base, href)
            val entry = zip.getEntry(path) ?: return@forEachIndexed
            val html = zip.getInputStream(entry).readBytes().toString(Charsets.UTF_8)
            val blocks = htmlBlocks(html, state)
            if (blocks.isNotEmpty()) pages += TextCleaner.paginate(blocks)
        }
        pages
    }

    private fun opfPath(zip: ZipFile): String {
        val container = zip.getEntry("META-INF/container.xml")
            ?: return zip.entries().asSequence().first { it.name.endsWith(".opf") }.name
        val xml = Jsoup.parse(zip.getInputStream(container).readBytes().toString(Charsets.UTF_8), "", Parser.xmlParser())
        return xml.selectFirst("rootfile")?.attr("full-path") ?: zip.entries().asSequence().first { it.name.endsWith(".opf") }.name
    }

    private fun resolve(base: String, href: String): String {
        val clean = java.net.URLDecoder.decode(href.substringBefore('#'), "UTF-8")
        val parts = (if (base.isEmpty()) clean else "$base/$clean").split('/').toMutableList()
        val out = mutableListOf<String>()
        for (p in parts) {
            when (p) {
                ".." -> if (out.isNotEmpty()) out.removeAt(out.size - 1)
                ".", "" -> Unit
                else -> out += p
            }
        }
        return out.joinToString("/")
    }

    private fun htmlBlocks(html: String, state: TextCleaner.ClassifyState): List<Block> {
        val doc = Jsoup.parse(html)
        doc.select("script, style, img, svg, figure img, nav, aside[epub:type=footnote], sup").remove()
        val blocks = mutableListOf<Block>()
        val selector = "h1, h2, h3, h4, h5, h6, p, li, blockquote, figcaption, pre, td, dt, dd"
        val elements = doc.body()?.select(selector) ?: return blocks
        for (el in elements) {
            if (el.parents().any { it.`is`(selector) && it != el && !it.`is`("blockquote, td") }) continue
            val text = TextCleaner.fixDevanagariOrder(el.text().trim())
            if (text.isEmpty()) continue
            val tag = el.tagName()
            val classified = TextCleaner.classify(listOf(text), state).first()
            blocks += when {
                tag.matches(Regex("h[1-6]")) && classified.kind == com.ansh.recalm.data.BlockKind.BODY ->
                    Block(text, com.ansh.recalm.data.BlockKind.HEADING)
                tag == "figcaption" -> Block(text, com.ansh.recalm.data.BlockKind.CAPTION)
                else -> classified
            }
        }
        if (blocks.isEmpty()) {
            val text = doc.body()?.wholeText()?.trim().orEmpty()
            if (text.isNotEmpty()) blocks += TextCleaner.classify(TextCleaner.paragraphs(text.lines()), state)
        }
        return blocks
    }

    private fun epubCover(file: File): Bitmap? = runCatching {
        ZipFile(file).use { zip ->
            val opfPath = opfPath(zip)
            val opf = Jsoup.parse(zip.getInputStream(zip.getEntry(opfPath)).readBytes().toString(Charsets.UTF_8), "", Parser.xmlParser())
            val base = opfPath.substringBeforeLast('/', "")
            val items = opf.select("manifest > item")
            val coverId = opf.selectFirst("meta[name=cover]")?.attr("content")
            val item: Element? = items.firstOrNull { it.attr("properties").contains("cover-image") }
                ?: items.firstOrNull { it.attr("id") == coverId }
                ?: items.firstOrNull { it.attr("media-type").startsWith("image") && it.attr("href").contains("cover", true) }
            val entry = item?.let { zip.getEntry(resolve(base, it.attr("href"))) } ?: return@use null
            zip.getInputStream(entry).use { BitmapFactory.decodeStream(it) }
        }
    }.getOrNull()

    fun generatedCovers(title: String): List<Bitmap> {
        val palettes = listOf(
            intArrayOf(0xFF1B2A4A.toInt(), 0xFF3E4C7A.toInt(), 0xFF9DB4FF.toInt()),
            intArrayOf(0xFF12343B.toInt(), 0xFF2D6A6A.toInt(), 0xFFA8E6CF.toInt()),
            intArrayOf(0xFF2B1B3D.toInt(), 0xFF5B3A7A.toInt(), 0xFFE0B3FF.toInt()),
            intArrayOf(0xFF3A2618.toInt(), 0xFF7A5034.toInt(), 0xFFFFD6A5.toInt())
        )
        return palettes.map { drawCover(title, it) }
    }

    private fun drawCover(title: String, colors: IntArray): Bitmap {
        val w = 480
        val h = 720
        val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.shader = LinearGradient(0f, 0f, w.toFloat(), h.toFloat(), colors[0], colors[1], Shader.TileMode.CLAMP)
        canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), paint)
        paint.shader = RadialGradient(w * 0.72f, h * 0.24f, w * 0.5f, (colors[2] and 0x00FFFFFF) or 0x55000000, Color.TRANSPARENT, Shader.TileMode.CLAMP)
        canvas.drawCircle(w * 0.72f, h * 0.24f, w * 0.5f, paint)
        paint.shader = null
        paint.color = colors[2]
        canvas.drawCircle(w * 0.72f, h * 0.24f, 54f, paint)
        paint.color = colors[1]
        canvas.drawCircle(w * 0.72f + 26f, h * 0.24f - 16f, 50f, paint)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 3f
        paint.color = (colors[2] and 0x00FFFFFF) or 0x66000000
        for (k in 0 until 3) {
            val y = h * 0.86f + k * 18f
            val path = android.graphics.Path()
            path.moveTo(0f, y)
            var x = 0f
            while (x < w) {
                path.quadTo(x + 30f, y - 10f, x + 60f, y)
                path.quadTo(x + 90f, y + 10f, x + 120f, y)
                x += 120f
            }
            canvas.drawPath(path, paint)
        }
        val text = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 46f
            typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD)
        }
        val layout = StaticLayout.Builder.obtain(title, 0, title.length, text, w - 80)
            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .setMaxLines(5)
            .setEllipsize(android.text.TextUtils.TruncateAt.END)
            .build()
        canvas.save()
        canvas.translate(40f, h * 0.48f)
        layout.draw(canvas)
        canvas.restore()
        return bitmap
    }
}
