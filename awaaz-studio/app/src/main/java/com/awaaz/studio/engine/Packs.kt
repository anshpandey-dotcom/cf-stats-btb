package com.awaaz.studio.engine

import android.content.Context
import androidx.compose.runtime.mutableStateMapOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

sealed interface PackState {
    data object Missing : PackState
    data class Loading(val progress: Float) : PackState
    data object Ready : PackState
    data class Failed(val reason: String) : PackState
}

class Packs(private val context: Context) {
    val states = mutableStateMapOf<String, PackState>()
    private val root = File(context.filesDir, "packs")

    init {
        (Catalog.voicePacks + Catalog.speechPacks).forEach { states[it.id] = if (isReady(it)) PackState.Ready else PackState.Missing }
    }

    fun dir(pack: Pack) = File(root, pack.id)

    fun isReady(pack: Pack) = File(dir(pack), ".done").exists()

    fun espeakDir(): File {
        val target = File(context.filesDir, "espeak-ng-data")
        val stamp = File(target, ".v1")
        if (stamp.exists()) return target
        target.deleteRecursively()
        copyAssets("espeak-ng-data", target)
        stamp.writeText("1")
        return target
    }

    private fun copyAssets(path: String, out: File) {
        val list = context.assets.list(path).orEmpty()
        if (list.isEmpty()) {
            out.parentFile?.mkdirs()
            context.assets.open(path).use { input -> out.outputStream().use { input.copyTo(it) } }
        } else {
            out.mkdirs()
            list.forEach { copyAssets("$path/$it", File(out, it)) }
        }
    }

    suspend fun download(pack: Pack) = withContext(Dispatchers.IO) {
        if (states[pack.id] is PackState.Loading) return@withContext
        states[pack.id] = PackState.Loading(0f)
        val folder = dir(pack).apply { mkdirs() }
        try {
            val sizes = pack.files.map { head(pack.url(it)) }
            val total = sizes.sum().coerceAtLeast(1L)
            var done = 0L
            pack.files.forEachIndexed { i, name ->
                val target = File(folder, name)
                if (target.exists() && target.length() == sizes[i]) {
                    done += sizes[i]
                    return@forEachIndexed
                }
                val part = File(folder, "$name.part")
                fetch(pack.url(name), part) { n ->
                    done += n
                    states[pack.id] = PackState.Loading(done.toFloat() / total)
                }
                part.renameTo(target)
            }
            File(folder, ".done").writeText("ok")
            states[pack.id] = PackState.Ready
        } catch (e: Exception) {
            states[pack.id] = PackState.Failed(e.message ?: "Network error")
        }
    }

    fun remove(pack: Pack) {
        dir(pack).deleteRecursively()
        states[pack.id] = PackState.Missing
    }

    private fun open(url: String, method: String): HttpURLConnection {
        var target = URL(url)
        repeat(6) {
            val c = (target.openConnection() as HttpURLConnection).apply {
                requestMethod = method
                instanceFollowRedirects = false
                connectTimeout = 20000
                readTimeout = 30000
                setRequestProperty("User-Agent", "AwaazStudio/1.0")
            }
            val code = c.responseCode
            if (code in 300..399) {
                target = URL(target, c.getHeaderField("Location"))
                c.disconnect()
            } else {
                if (code !in 200..299) throw IllegalStateException("Download failed ($code)")
                return c
            }
        }
        throw IllegalStateException("Too many redirects")
    }

    private fun head(url: String): Long {
        val c = open(url, "HEAD")
        val n = c.contentLengthLong
        c.disconnect()
        return n
    }

    private fun fetch(url: String, out: File, onBytes: (Long) -> Unit) {
        val c = open(url, "GET")
        c.inputStream.use { input ->
            out.outputStream().use { output ->
                val buf = ByteArray(1 shl 16)
                while (true) {
                    val n = input.read(buf)
                    if (n < 0) break
                    output.write(buf, 0, n)
                    onBytes(n.toLong())
                }
            }
        }
        c.disconnect()
    }
}
