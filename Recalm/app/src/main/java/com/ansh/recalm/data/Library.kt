package com.ansh.recalm.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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
