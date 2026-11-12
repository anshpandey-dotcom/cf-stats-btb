package com.ansh.memyclock.voice

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream
import java.io.BufferedInputStream
import java.io.File
import java.io.FilterInputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL

data class Voice(
    val id: String,
    val name: String,
    val detail: String,
    val folder: String = "",
    val sizeMb: Int = 0
) {
    val neural get() = folder.isNotEmpty()
    val model get() = folder.removePrefix("vits-piper-") + ".onnx"
}

object Voices {
    val all = listOf(
        Voice("priyamvada", "Priyamvada", "Indian Hindi, female, neural", "vits-piper-hi_IN-priyamvada-medium", 64),
        Voice("pratham", "Pratham", "Indian Hindi, male, neural", "vits-piper-hi_IN-pratham-medium", 64),
        Voice("rohan", "Rohan", "Indian Hindi, male, neural", "vits-piper-hi_IN-rohan-medium", 64),
        Voice("device", "Phone voice", "Hindi voice already on this phone")
    )

    fun byId(id: String) = all.firstOrNull { it.id == id } ?: all.first()

    fun dir(context: Context, voice: Voice) = File(context.filesDir, "voices/${voice.folder}")

    fun installed(context: Context, voice: Voice) =
        !voice.neural || File(dir(context, voice), ".ready").exists()

    fun remove(context: Context, voice: Voice) {
        if (voice.neural) dir(context, voice).deleteRecursively()
    }

    suspend fun download(context: Context, voice: Voice, progress: (Float) -> Unit) = withContext(Dispatchers.IO) {
        val root = File(context.filesDir, "voices").apply { mkdirs() }
        val target = dir(context, voice)
        target.deleteRecursively()
        val url = "https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/${voice.folder}.tar.bz2"
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 20_000
            readTimeout = 30_000
            instanceFollowRedirects = true
        }
        try {
            if (connection.responseCode !in 200..299) error("Download failed (${connection.responseCode})")
            val length = connection.contentLengthLong.takeIf { it > 0 } ?: (voice.sizeMb * 1_048_576L)
            val counted = Counting(connection.inputStream) { progress((it.toFloat() / length).coerceIn(0f, 1f)) }
            TarArchiveInputStream(BZip2CompressorInputStream(BufferedInputStream(counted, 1 shl 16))).use { tar ->
                while (true) {
                    ensureActive()
                    val entry = tar.nextEntry ?: break
                    val out = File(root, entry.name)
                    if (!out.canonicalPath.startsWith(root.canonicalPath + File.separator)) continue
                    if (entry.isDirectory) {
                        out.mkdirs()
                    } else {
                        out.parentFile?.mkdirs()
                        out.outputStream().use { tar.copyTo(it, 1 shl 16) }
                    }
                }
            }
            if (!File(target, voice.model).exists()) error("Voice files are incomplete")
            File(target, ".ready").writeText("ok")
        } catch (e: Throwable) {
            target.deleteRecursively()
            throw e
        } finally {
            connection.disconnect()
        }
    }

    private class Counting(input: InputStream, val onRead: (Long) -> Unit) : FilterInputStream(input) {
        private var total = 0L
        private var reported = 0L

        override fun read(): Int = super.read().also { if (it >= 0) add(1) }

        override fun read(b: ByteArray, off: Int, len: Int): Int =
            super.read(b, off, len).also { if (it > 0) add(it.toLong()) }

        private fun add(count: Long) {
            total += count
            if (total - reported > 262_144) {
                reported = total
                onRead(total)
            }
        }
    }
}
