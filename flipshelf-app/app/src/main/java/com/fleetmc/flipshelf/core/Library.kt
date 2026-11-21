package com.fleetmc.flipshelf.core

import android.content.Context
import org.json.JSONArray
import java.io.File

class Library private constructor(context: Context) {

    private val root = context.applicationContext.filesDir
    val booksDir = File(root, "books").apply { mkdirs() }
    val coversDir = File(root, "covers").apply { mkdirs() }
    private val store = File(root, "library.json")
    private val books = ArrayList<Book>()

    init {
        load()
    }

    @Synchronized
    private fun load() {
        books.clear()
        if (!store.exists()) return
        try {
            val arr = JSONArray(store.readText())
            for (i in 0 until arr.length()) books.add(Book.fromJson(arr.getJSONObject(i)))
        } catch (_: Exception) {
        }
    }

    @Synchronized
    private fun save() {
        val arr = JSONArray()
        books.forEach { arr.put(it.toJson()) }
        val tmp = File(root, "library.json.tmp")
        tmp.writeText(arr.toString())
        tmp.renameTo(store)
    }

    @Synchronized
    fun all(): List<Book> = books.sortedWith(
        compareByDescending<Book> { it.openedAt }.thenByDescending { it.addedAt }
    )

    @Synchronized
    fun find(id: String): Book? = books.firstOrNull { it.id == id }

    @Synchronized
    fun add(book: Book) {
        books.add(book)
        save()
    }

    @Synchronized
    fun update(book: Book) {
        val i = books.indexOfFirst { it.id == book.id }
        if (i >= 0) books[i] = book
        save()
    }

    @Synchronized
    fun remove(book: Book) {
        books.removeAll { it.id == book.id }
        save()
        val f = fileOf(book)
        if (f.isDirectory) f.deleteRecursively() else f.delete()
        coverOf(book).delete()
    }

    fun fileOf(book: Book) = File(booksDir, book.file)

    fun coverOf(book: Book) = File(coversDir, book.id + ".jpg")

    companion object {
        @Volatile
        private var instance: Library? = null

        fun get(context: Context): Library =
            instance ?: synchronized(this) {
                instance ?: Library(context).also { instance = it }
            }
    }
}
