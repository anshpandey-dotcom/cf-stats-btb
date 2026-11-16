package com.ansh.recalm.importer

import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import com.ansh.recalm.data.Block
import com.ansh.recalm.data.BlockKind
import com.ansh.recalm.data.Book
import com.ansh.recalm.data.Library
import com.ansh.recalm.data.Page
import com.ansh.recalm.data.RawWord
import com.tom_roush.pdfbox.io.MemoryUsageSetting
import com.tom_roush.pdfbox.pdmodel.PDDocument
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.util.concurrent.ConcurrentHashMap

@Serializable
private data class ScannedPage(val text: String = "", val words: List<RawWord> = emptyList(), val images: List<String>? = null)

data class ScanProgress(val done: Int, val total: Int)

class OcrWorker(private val library: Library, private val importer: () -> Importer) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val json = Json { ignoreUnknownKeys = true }
    private val wake = Channel<Unit>(Channel.CONFLATED)
    private val focus = ConcurrentHashMap<String, Int>()
    @Volatile private var focusBook: String? = null

    private val _progress = MutableStateFlow<Map<String, ScanProgress>>(emptyMap())
    val progress: StateFlow<Map<String, ScanProgress>> = _progress.asStateFlow()

    init {
        scope.launch { loop() }
        wake.trySend(Unit)
    }

    fun enqueue(bookId: String) {
        wake.trySend(Unit)
    }

    fun focus(bookId: String, page: Int) {
        focus[bookId] = page
        focusBook = bookId
        wake.trySend(Unit)
    }

    private fun needsWork(page: Page) = page.pending || page.scanImages

    private suspend fun loop() {
        while (true) {
            val books = library.books.value.filter { it.format == "pdf" && it.pending > 0 }
            val book = books.firstOrNull { it.id == focusBook } ?: books.maxByOrNull { it.lastOpened }
            if (book == null) {
                wake.receive()
                continue
            }
            runCatching { process(book) }.onFailure {
                library.update(book.id) { b -> b.copy(pending = 0) }
            }
        }
    }

    private fun store(book: Book) = File(library.dir(book.id), "ocr.json")

    private fun loadStore(book: Book): MutableMap<Int, ScannedPage> = runCatching {
        json.decodeFromString<Map<Int, ScannedPage>>(store(book).readText()).toMutableMap()
    }.getOrDefault(mutableMapOf())

    private fun process(book: Book) {
        val file = library.sourceFile(book)
        if (!file.exists()) {
            library.update(book.id) { it.copy(pending = 0) }
            return
        }
        val scanned = loadStore(book)
        var document: PDDocument? = null
        ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { fd ->
            PdfRenderer(fd).use { renderer ->
                try {
                    var unsaved = 0
                    while (true) {
                        val pages = library.pages(book.id)
                        val todo = pages.indices.filter { needsWork(pages[it]) && !scanned.containsKey(it) }
                        val total = pages.count { needsWork(it) } + scanned.size
                        _progress.update { it + (book.id to ScanProgress(scanned.size, total.coerceAtLeast(1))) }
                        if (todo.isEmpty()) break
                        if (focusBook != null && focusBook != book.id && library.book(focusBook!!)?.pending ?: 0 > 0) break
                        val start = focus[book.id] ?: book.page
                        val next = todo.firstOrNull { it >= start } ?: todo.first()
                        val page = pages[next]
                        scanned[next] = if (page.pending) {
                            val index = if (page.source >= 0) page.source else next
                            val (text, words) = importer().ocrPage(renderer, index)
                            ScannedPage(text, words)
                        } else {
                            val doc = document ?: PDDocument.load(file, MemoryUsageSetting.setupTempFileOnly()).also { document = it }
                            val pageText = page.blocks.joinToString("\n") { it.text }
                            val index = if (page.source >= 0) page.source else next
                            val found = runCatching { importer().textInImages(doc.getPage(index).resources, pageText, 0) }.getOrDefault(emptyList())
                            ScannedPage(images = found)
                        }
                        unsaved++
                        val urgent = next == (focus[book.id] ?: -1)
                        if (urgent || unsaved >= 4) {
                            apply(book, scanned)
                            unsaved = 0
                        }
                    }
                    if (unsaved > 0 || scanned.isNotEmpty()) apply(book, scanned)
                } finally {
                    document?.close()
                }
            }
        }
        val left = library.pages(book.id).count { needsWork(it) }
        library.update(book.id) { it.copy(pending = left) }
        if (left == 0) {
            store(book).delete()
            _progress.update { it - book.id }
        }
    }

    private fun apply(book: Book, scanned: Map<Int, ScannedPage>) {
        store(book).writeText(json.encodeToString(kotlinx.serialization.builtins.MapSerializer(
            kotlinx.serialization.serializer<Int>(), ScannedPage.serializer()), scanned))
        val pages = library.pages(book.id).toMutableList()
        val textPages = scanned.filterValues { it.images == null }.keys.sorted().filter { it < pages.size }
        if (textPages.isNotEmpty()) {
            val built = TextCleaner.buildPages(textPages.map { scanned.getValue(it).text })
            textPages.forEachIndexed { n, i ->
                val page = built[n]
                val words = runCatching { TextCleaner.alignWords(page.blocks, scanned.getValue(i).words) }.getOrDefault(emptyList())
                pages[i] = page.copy(source = if (pages[i].source >= 0) pages[i].source else i, ocr = true, words = words)
            }
        }
        for ((i, result) in scanned) {
            val images = result.images ?: continue
            if (i >= pages.size || !pages[i].scanImages) continue
            val kept = pages[i].blocks.filter { it.kind != BlockKind.IMAGE_TEXT }
            pages[i] = pages[i].copy(blocks = kept + images.map { Block(it, BlockKind.IMAGE_TEXT) }, scanImages = false)
        }
        library.writePages(book.id, pages)
        library.update(book.id) { it.copy(pending = pages.count { p -> needsWork(p) }) }
    }
}
