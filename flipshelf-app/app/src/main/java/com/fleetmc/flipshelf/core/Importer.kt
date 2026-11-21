package com.fleetmc.flipshelf.core

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.net.Uri
import android.provider.OpenableColumns
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import java.util.zip.ZipFile

class Importer(private val context: Context, private val library: Library) {

    class ImportException(message: String) : Exception(message)

    private fun displayName(uri: Uri): String {
        if (uri.scheme == "content") {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
                if (it.moveToFirst()) {
                    val n = it.getString(0)
                    if (!n.isNullOrBlank()) return n
                }
            }
        }
        return uri.lastPathSegment?.substringAfterLast('/') ?: "book"
    }

    private fun copy(uri: Uri, target: File) {
        val input = context.contentResolver.openInputStream(uri) ?: throw ImportException("Cannot open file")
        input.use { src -> FileOutputStream(target).use { dst -> src.copyTo(dst) } }
    }

    fun isImage(name: String) = ImageSource.isImageName(name)

    private fun detect(name: String, file: File): Format {
        val ext = name.substringAfterLast('.', "").lowercase()
        return when {
            ext == "pdf" -> Format.PDF
            ext == "epub" -> Format.EPUB
            ext == "txt" -> Format.TEXT
            ext in setOf("cbz", "zip") -> if (looksLikeEpub(file)) Format.EPUB else Format.COMIC
            ImageSource.isImageName(name) -> Format.IMAGES
            else -> sniff(file)
        }
    }

    private fun looksLikeEpub(file: File): Boolean = try {
        ZipFile(file).use { it.getEntry("META-INF/container.xml") != null }
    } catch (_: Exception) {
        false
    }

    private fun sniff(file: File): Format {
        val head = ByteArray(8)
        val n = file.inputStream().use { it.read(head) }
        if (n >= 4 && head[0] == '%'.code.toByte() && head[1] == 'P'.code.toByte() && head[2] == 'D'.code.toByte() && head[3] == 'F'.code.toByte()) {
            return Format.PDF
        }
        if (n >= 2 && head[0] == 'P'.code.toByte() && head[1] == 'K'.code.toByte()) {
            return if (looksLikeEpub(file)) Format.EPUB else Format.COMIC
        }
        throw ImportException("Unsupported file type")
    }

    fun importUri(uri: Uri): Book {
        val name = displayName(uri)
        val id = UUID.randomUUID().toString().substring(0, 12)
        val temp = File(library.booksDir, "$id.tmp")
        try {
            copy(uri, temp)
            val format = detect(name, temp)
            if (format == Format.IMAGES) {
                val dir = File(library.booksDir, id)
                dir.mkdirs()
                temp.renameTo(File(dir, "0001." + name.substringAfterLast('.', "jpg").lowercase()))
                return finish(Book(id, name.substringBeforeLast('.'), Format.IMAGES, id))
            }
            val ext = when (format) {
                Format.PDF -> "pdf"
                Format.EPUB -> "epub"
                Format.TEXT -> "txt"
                else -> "cbz"
            }
            val target = File(library.booksDir, "$id.$ext")
            if (!temp.renameTo(target)) throw ImportException("Cannot store file")
            return finish(Book(id, name.substringBeforeLast('.').ifBlank { name }, format, target.name))
        } catch (e: Exception) {
            temp.delete()
            throw if (e is ImportException) e else ImportException(e.message ?: "Import failed")
        }
    }

    fun importImages(uris: List<Uri>, title: String): Book {
        val id = UUID.randomUUID().toString().substring(0, 12)
        val dir = File(library.booksDir, id)
        dir.mkdirs()
        try {
            uris.forEachIndexed { i, uri ->
                val ext = displayName(uri).substringAfterLast('.', "jpg").lowercase()
                copy(uri, File(dir, String.format("%04d.%s", i + 1, ext)))
            }
            return finish(Book(id, title, Format.IMAGES, id))
        } catch (e: Exception) {
            dir.deleteRecursively()
            throw if (e is ImportException) e else ImportException(e.message ?: "Import failed")
        }
    }

    private fun finish(book: Book): Book {
        val file = library.fileOf(book)
        try {
            val source = BookSources.openFile(book.format, file)
            source.use { src ->
                src.prepare(COVER_LAYOUT_W, COVER_LAYOUT_H, 1f)
                book.pages = src.pageCount
                if (book.format == Format.EPUB) {
                    val parser = EpubParser(file)
                    if (parser.title.isNotBlank()) book.title = parser.title
                    val cover = parser.coverBitmap(COVER_W * 2)
                    if (cover != null) {
                        saveCover(book, cover)
                    } else {
                        saveCover(book, src.render(0, COVER_W, COVER_H))
                    }
                } else {
                    val h = (COVER_W / src.aspect).toInt().coerceIn(COVER_W / 2, COVER_W * 3)
                    saveCover(book, src.render(0, COVER_W, h))
                }
            }
        } catch (e: Exception) {
            if (file.isDirectory) file.deleteRecursively() else file.delete()
            throw ImportException(e.message ?: "Cannot read this file")
        }
        library.add(book)
        return book
    }

    private fun saveCover(book: Book, bitmap: Bitmap) {
        val flat = Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.RGB_565)
        val canvas = Canvas(flat)
        canvas.drawColor(Color.WHITE)
        canvas.drawBitmap(bitmap, 0f, 0f, Paint(Paint.FILTER_BITMAP_FLAG))
        FileOutputStream(library.coverOf(book)).use { flat.compress(Bitmap.CompressFormat.JPEG, 85, it) }
    }

    companion object {
        const val COVER_W = 360
        const val COVER_H = 520
        const val COVER_LAYOUT_W = 720
        const val COVER_LAYOUT_H = 1060
    }
}
