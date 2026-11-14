package com.fleetmc.kuchlikh.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

class StoredImage(val name: String, val aspect: Float)

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

    fun importImage(bookId: String, uri: Uri, maxSide: Int = 1600): StoredImage? {
        val resolver = context.contentResolver
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) } ?: return null
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxSide) sample *= 2
        val options = BitmapFactory.Options().apply { inSampleSize = sample }
        var bitmap = resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) } ?: return null
        val rotation = resolver.openInputStream(uri)?.use { stream ->
            when (ExifInterface(stream).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                else -> 0f
            }
        } ?: 0f
        val longest = maxOf(bitmap.width, bitmap.height)
        val scale = if (longest > maxSide) maxSide / longest.toFloat() else 1f
        if (rotation != 0f || scale != 1f) {
            val matrix = Matrix().apply {
                postScale(scale, scale)
                postRotate(rotation)
            }
            bitmap = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        }
        val name = "img_" + newId() + ".jpg"
        file(bookId, name).outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 88, it) }
        return StoredImage(name, bitmap.width / bitmap.height.toFloat())
    }

    fun purgeUnused(book: Book) {
        val used = HashSet<String>()
        book.cover?.let { used += it }
        book.pages.forEach { page -> page.image?.let { used += it } }
        dir(book.id).listFiles().orEmpty()
            .filter { it.name.startsWith("img_") && it.name !in used }
            .forEach { it.delete() }
    }
}
