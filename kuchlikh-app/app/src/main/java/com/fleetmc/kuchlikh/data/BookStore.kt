package com.fleetmc.kuchlikh.data

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

class StoredImage(val name: String, val aspect: Float, val transparent: Boolean)

class BookStore(private val context: Context) {

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = false }
    private val root: File get() = File(context.filesDir, "books").apply { mkdirs() }

    fun dir(bookId: String): File = File(root, bookId).apply { mkdirs() }

    fun file(bookId: String, name: String): File = File(dir(bookId), name)

    fun list(): List<Book> = root.listFiles().orEmpty()
        .mapNotNull { folder -> runCatching { load(folder.name) }.getOrNull() }
        .sortedByDescending { it.updatedAt }

    fun load(bookId: String): Book? {
        val file = File(File(root, bookId), "book.json")
        if (!file.exists()) return null
        return json.decodeFromString<Book>(file.readText())
    }

    @Synchronized
    fun save(book: Book) {
        val target = File(dir(book.id), "book.json")
        val temp = File(dir(book.id), "book.json.tmp")
        temp.writeText(json.encodeToString(book))
        if (!temp.renameTo(target)) {
            target.writeText(temp.readText())
            temp.delete()
        }
    }

    fun create(title: String, author: String, coverColor: Int): Book {
        val now = System.currentTimeMillis()
        val book = Book(
            id = newId(),
            title = title,
            author = author,
            coverColor = coverColor,
            createdAt = now,
            updatedAt = now,
        )
        save(book)
        return book
    }

    fun delete(bookId: String) {
        File(root, bookId).deleteRecursively()
    }

    fun duplicate(bookId: String): Book? {
        val source = load(bookId) ?: return null
        val now = System.currentTimeMillis()
        val copy = source.copy(id = newId(), title = source.title + " (copy)", createdAt = now, updatedAt = now)
        File(root, bookId).listFiles().orEmpty()
            .filter { it.name != "book.json" && it.isFile }
            .forEach { it.copyTo(File(dir(copy.id), it.name), overwrite = true) }
        save(copy)
        return copy
    }

    fun importImage(bookId: String, uri: Uri): StoredImage? {
        val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: return null
        return importBytes(bookId, bytes)
    }

    fun importBytes(bookId: String, bytes: ByteArray, maxSide: Int = 1600): StoredImage? {
        val bitmap = ImageTools.decode(bytes, maxSide) ?: return null
        return saveBitmap(bookId, bitmap)
    }

    fun saveBitmap(bookId: String, bitmap: Bitmap): StoredImage {
        val transparent = ImageTools.hasTransparency(bitmap)
        val name = "img_" + newId() + if (transparent) ".png" else ".jpg"
        file(bookId, name).outputStream().use {
            if (transparent) bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) else bitmap.compress(Bitmap.CompressFormat.JPEG, 88, it)
        }
        return StoredImage(name, bitmap.width / bitmap.height.toFloat(), transparent)
    }

    fun loadBitmap(bookId: String, name: String, maxSide: Int = 1600): Bitmap? {
        val target = file(bookId, name)
        if (!target.exists()) return null
        return ImageTools.decode(target.readBytes(), maxSide)
    }

    fun purgeUnused(book: Book) {
        val used = HashSet<String>()
        book.cover?.let { used += it }
        book.pages.forEach { page ->
            page.image?.let { used += it }
            page.imageSrc?.let { used += it }
        }
        dir(book.id).listFiles().orEmpty()
            .filter { it.name.startsWith("img_") && it.name !in used }
            .forEach { it.delete() }
    }
}
