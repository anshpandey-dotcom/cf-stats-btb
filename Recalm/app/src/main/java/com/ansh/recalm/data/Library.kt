package com.ansh.recalm.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.io.File

class Library(context: Context) {

    private val json = Json { ignoreUnknownKeys = true }
    private val root = File(context.filesDir, "books").apply { mkdirs() }
    private val index = File(root, "library.json")
    private val pageCache = object : LinkedHashMap<String, List<Page>>(4, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, List<Page>>?) = size > 2
    }

    private val _revisions = MutableStateFlow<Map<String, Int>>(emptyMap())
    val revisions: StateFlow<Map<String, Int>> = _revisions.asStateFlow()

    private val _books = MutableStateFlow(load())
    val books: StateFlow<List<Book>> = _books.asStateFlow()

    private fun load(): List<Book> = runCatching {
        json.decodeFromString(ListSerializer(Book.serializer()), index.readText())
    }.getOrDefault(emptyList())

    @Synchronized
    private fun save(list: List<Book>) {
        _books.value = list
        val tmp = File(root, "library.json.tmp")
        tmp.writeText(json.encodeToString(ListSerializer(Book.serializer()), list))
        tmp.renameTo(index)
    }

    fun dir(id: String) = File(root, id)
    fun sourceFile(book: Book) = File(dir(book.id), "source.${book.format}")
    fun coverFile(id: String) = File(dir(id), "cover.jpg")

    fun book(id: String): Book? = _books.value.firstOrNull { it.id == id }

    @Synchronized
    fun add(book: Book, pages: List<Page>) {
        writePages(book.id, pages)
        save(listOf(book) + _books.value.filterNot { it.id == book.id })
    }

    @Synchronized
    fun update(id: String, change: (Book) -> Book) {
        val list = _books.value.map { if (it.id == id) change(it) else it }
        save(list)
    }

    @Synchronized
    fun delete(id: String) {
        dir(id).deleteRecursively()
        synchronized(pageCache) { pageCache.remove(id) }
        save(_books.value.filterNot { it.id == id })
    }

    fun pages(id: String): List<Page> {
        synchronized(pageCache) { pageCache[id]?.let { return it } }
        val file = File(dir(id), "pages.json")
        val pages = runCatching { json.decodeFromString(ListSerializer(Page.serializer()), file.readText()) }
            .getOrDefault(emptyList())
        synchronized(pageCache) { pageCache[id] = pages }
        return pages
    }

    fun writePages(id: String, pages: List<Page>) {
        val d = dir(id).apply { mkdirs() }
        val tmp = File(d, "pages.json.tmp")
        tmp.writeText(json.encodeToString(ListSerializer(Page.serializer()), pages))
        tmp.renameTo(File(d, "pages.json"))
        synchronized(pageCache) { pageCache[id] = pages }
        _revisions.update { it + (id to ((it[id] ?: 0) + 1)) }
    }

    @Synchronized
    fun merge(added: List<Book>, progress: Map<String, Book>) {
        val current = _books.value.map { book ->
            progress[book.id]?.let { b -> book.copy(page = b.page, sentence = b.sentence, lastOpened = b.lastOpened) } ?: book
        }
        save(added.filter { a -> current.none { it.id == a.id } } + current)
    }

    fun reloadIndex() {
        _books.value = load()
        synchronized(pageCache) { pageCache.clear() }
        _revisions.update { r -> r.mapValues { it.value + 1 } }
    }

    fun saveCover(id: String, bitmap: Bitmap) {
        dir(id).mkdirs()
        coverFile(id).outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 90, it) }
    }

    fun cover(id: String): Bitmap? = coverFile(id).takeIf { it.exists() }?.let {
        BitmapFactory.decodeFile(it.absolutePath)
    }

    fun saveProgress(id: String, page: Int, sentence: Int) =
        update(id) { it.copy(page = page, sentence = sentence, lastOpened = System.currentTimeMillis()) }
}
