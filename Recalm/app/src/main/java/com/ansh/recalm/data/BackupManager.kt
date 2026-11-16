package com.ansh.recalm.data

import android.content.Context
import android.net.Uri
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

@Serializable
private data class BackupInfo(
    val version: Int = 1,
    val createdAt: Long = 0L,
    val strings: Map<String, String> = emptyMap(),
    val floats: Map<String, Float> = emptyMap(),
    val ints: Map<String, Int> = emptyMap(),
    val booleans: Map<String, Boolean> = emptyMap()
)

data class RestoreResult(val added: Int, val updated: Int)

class BackupManager(
    private val context: Context,
    private val library: Library,
    private val settings: Settings,
    private val onVoicesChanged: () -> Unit
) {

    private val json = Json { ignoreUnknownKeys = true }
    private val booksRoot get() = File(context.filesDir, "books")
    private val customVoices get() = File(context.filesDir, "voices/custom")

    fun export(uri: Uri, onProgress: (Float, String) -> Unit) {
        val books = library.books.value
        val files = books.flatMap { book ->
            library.dir(book.id).listFiles()?.filter { it.isFile && !it.name.endsWith(".tmp") }?.map { "books/${book.id}/${it.name}" to it }.orEmpty()
        } + (customVoices.takeIf { it.exists() }?.walkTopDown()?.filter { it.isFile }?.map {
            "voices/custom/" + it.relativeTo(customVoices).invariantSeparatorsPath to it
        }?.toList().orEmpty())
        val total = files.sumOf { it.second.length() }.coerceAtLeast(1)
        var done = 0L
        context.contentResolver.openOutputStream(uri)!!.use { raw ->
            ZipOutputStream(raw.buffered(1 shl 16)).use { zip ->
                zip.putNextEntry(ZipEntry("recalm-backup.json"))
                zip.write(json.encodeToString(BackupInfo.serializer(), settings.snapshot().let {
                    BackupInfo(1, System.currentTimeMillis(), it.strings, it.floats, it.ints, it.booleans)
                }).toByteArray())
                zip.closeEntry()
                zip.putNextEntry(ZipEntry("books/library.json"))
                zip.write(json.encodeToString(ListSerializer(Book.serializer()), books).toByteArray())
                zip.closeEntry()
                for ((name, file) in files) {
                    zip.putNextEntry(ZipEntry(name))
                    file.inputStream().use { it.copyTo(zip, 1 shl 16) }
                    zip.closeEntry()
                    done += file.length()
                    onProgress(done.toFloat() / total, "Saving ${file.name}")
                }
            }
        }
    }

    fun restore(uri: Uri, onProgress: (Float, String) -> Unit): RestoreResult {
        var info: BackupInfo? = null
        var backupBooks: List<Book> = emptyList()
        val existing = library.books.value.associateBy { it.id }
        val incoming = HashSet<String>()
        var seenLibrary = false
        val booksCanonical = booksRoot.canonicalPath + File.separator
        val voicesCanonical = customVoices.canonicalPath + File.separator
        val voicesBefore = customVoices.list()?.toSet().orEmpty()
        var count = 0
        context.contentResolver.openInputStream(uri)!!.use { raw ->
            ZipInputStream(raw.buffered(1 shl 16)).use { zip ->
                var entry = zip.nextEntry
                while (entry != null) {
                    val name = entry.name
                    when {
                        entry.isDirectory -> Unit
                        name == "recalm-backup.json" -> info = json.decodeFromString(BackupInfo.serializer(), zip.readBytes().decodeToString())
                        name == "books/library.json" -> {
                            backupBooks = json.decodeFromString(ListSerializer(Book.serializer()), zip.readBytes().decodeToString())
                            backupBooks.filter { it.id !in existing }.forEach { incoming += it.id }
                            seenLibrary = true
                        }
                        name.startsWith("books/") && seenLibrary -> {
                            val id = name.removePrefix("books/").substringBefore('/')
                            if (id in incoming) {
                                val out = File(booksRoot, name.removePrefix("books/"))
                                if (!out.canonicalPath.startsWith(booksCanonical)) throw SecurityException("Bad backup entry")
                                out.parentFile?.mkdirs()
                                out.outputStream().use { zip.copyTo(it, 1 shl 16) }
                            }
                        }
                        name.startsWith("voices/custom/") -> {
                            val out = File(customVoices, name.removePrefix("voices/custom/"))
                            if (!out.canonicalPath.startsWith(voicesCanonical)) throw SecurityException("Bad backup entry")
                            if (name.removePrefix("voices/custom/").substringBefore('/') !in voicesBefore) {
                                out.parentFile?.mkdirs()
                                out.outputStream().use { zip.copyTo(it, 1 shl 16) }
                            }
                        }
                    }
                    count++
                    if (count % 3 == 0) onProgress(0f, "Restoring ${name.substringAfterLast('/')}")
                    entry = zip.nextEntry
                }
            }
        }
        if (info == null && !seenLibrary) throw IllegalArgumentException("This is not a Recalm backup file")
        val added = backupBooks.filter { it.id in incoming && File(library.dir(it.id), "pages.json").exists() }
        val newer = backupBooks.filter { b -> existing[b.id]?.let { b.lastOpened > it.lastOpened } == true }
        library.merge(added, newer.associateBy { it.id })
        info?.let { settings.restore(Settings.Snapshot(it.strings, it.floats, it.ints, it.booleans)) }
        onVoicesChanged()
        return RestoreResult(added.size, newer.size)
    }
}
