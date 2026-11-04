package com.ansh.recalm.tts

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream
import java.io.BufferedInputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.ConcurrentHashMap
import java.io.InputStream
import java.util.zip.ZipInputStream

sealed interface PackState {
    data object Missing : PackState
    data class Downloading(val progress: Float) : PackState
    data object Installing : PackState
    data object Ready : PackState
    data class Failed(val message: String) : PackState
}

@Serializable
data class CustomVoiceInfo(val id: String, val name: String)

class VoiceManager(private val context: Context) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val root = File(context.filesDir, "voices").apply { mkdirs() }
    private val customRoot = File(root, "custom").apply { mkdirs() }
    private val downloadDir = File(context.filesDir, "downloads").apply { mkdirs() }
    private val jobs = ConcurrentHashMap<String, Job>()

    private val _states = MutableStateFlow(VoiceCatalog.packs.associate { it.id to stateOnDisk(it.id) })
    val states: StateFlow<Map<String, PackState>> = _states.asStateFlow()

    private val _custom = MutableStateFlow(loadCustom())
    val customVoices: StateFlow<List<CustomVoiceInfo>> = _custom.asStateFlow()

    init {
        VoiceCatalog.packs.forEach { pack ->
            if (_states.value[pack.id] != PackState.Ready && partFile(pack).exists()) {
                set(pack.id, PackState.Failed("Download paused at ${(progressOf(pack) * 100).toInt()}%. Tap Download to continue."))
            }
        }
    }

    fun packDir(id: String) = File(root, id)

    private fun stateOnDisk(id: String): PackState =
        if (File(packDir(id), ".ready").exists()) PackState.Ready else PackState.Missing

    fun isReady(id: String) = _states.value[id] == PackState.Ready

    fun installedPackFor(voice: Voice): String? = voice.packIds.firstOrNull { isReady(it) }

    fun isVoiceReady(voiceId: String): Boolean {
        if (voiceId.startsWith("custom-")) return _custom.value.any { it.id == voiceId }
        val voice = VoiceCatalog.voice(voiceId) ?: return false
        return installedPackFor(voice) != null
    }

    fun download(pack: VoicePack) {
        if (jobs[pack.id]?.isActive == true) return
        set(pack.id, PackState.Downloading(progressOf(pack)))
        DownloadService.start(context)
        val job = scope.launch {
            val part = partFile(pack)
            var attempt = 0
            while (true) {
                try {
                    fetch(pack, part)
                    break
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    attempt++
                    if (attempt >= 6) {
                        set(pack.id, PackState.Failed("Download stopped (${e.message ?: "network error"}). Tap Download to continue from where it stopped."))
                        return@launch
                    }
                    delay(2500L * attempt)
                }
            }
            install(pack, part)
        }
        jobs[pack.id] = job
        job.invokeOnCompletion { jobs.remove(pack.id, job) }
    }

    fun cancel(pack: VoicePack) {
        jobs.remove(pack.id)?.cancel()
        partFile(pack).delete()
        sizeFile(pack).delete()
        set(pack.id, PackState.Missing)
    }

    fun delete(pack: VoicePack) {
        packDir(pack.id).deleteRecursively()
        set(pack.id, PackState.Missing)
    }

    val busy: Boolean get() = _states.value.values.any { it is PackState.Downloading || it == PackState.Installing }

    private fun set(id: String, state: PackState) = _states.update { it + (id to state) }

    private fun partFile(pack: VoicePack) = File(downloadDir, "${pack.id}.part")
    private fun sizeFile(pack: VoicePack) = File(downloadDir, "${pack.id}.size")

    private fun progressOf(pack: VoicePack): Float {
        val total = sizeFile(pack).takeIf { it.exists() }?.readText()?.toLongOrNull() ?: return 0f
        return if (total > 0) (partFile(pack).length().toFloat() / total).coerceIn(0f, 1f) else 0f
    }

    private suspend fun fetch(pack: VoicePack, part: File) {
        var url = URL(pack.url)
        var redirects = 0
        while (true) {
            currentCoroutineContext().ensureActive()
            val have = if (part.exists()) part.length() else 0L
            val connection = (url.openConnection() as HttpURLConnection).apply {
                instanceFollowRedirects = false
                connectTimeout = 20_000
                readTimeout = 30_000
                setRequestProperty("User-Agent", "Recalm/1.0 (Android)")
                setRequestProperty("Accept-Encoding", "identity")
                if (have > 0) setRequestProperty("Range", "bytes=$have-")
            }
            try {
                val code = connection.responseCode
                if (code in 300..399) {
                    val location = connection.getHeaderField("Location") ?: throw IOException("Bad redirect")
                    url = URL(url, location)
                    if (++redirects > 8) throw IOException("Too many redirects")
                    continue
                }
                val known = sizeFile(pack).takeIf { it.exists() }?.readText()?.toLongOrNull() ?: -1L
                if (code == 416 && known > 0 && have >= known) return
                if (code !in 200..299) throw IOException("server replied $code")
                val append = code == 206 && have > 0
                val total = if (append) have + connection.contentLengthLong else connection.contentLengthLong
                if (total > 0) sizeFile(pack).writeText(total.toString())
                var written = if (append) have else 0L
                var lastUpdate = 0L
                connection.inputStream.use { input ->
                    FileOutputStream(part, append).use { output ->
                        val buffer = ByteArray(1 shl 16)
                        while (true) {
                            currentCoroutineContext().ensureActive()
                            val n = input.read(buffer)
                            if (n < 0) break
                            output.write(buffer, 0, n)
                            written += n
                            val now = System.currentTimeMillis()
                            if (now - lastUpdate > 400) {
                                lastUpdate = now
                                set(pack.id, PackState.Downloading(if (total > 0) written.toFloat() / total else 0f))
                            }
                        }
                    }
                }
                if (total > 0 && written < total) throw IOException("connection closed early")
                return
            } finally {
                connection.disconnect()
            }
        }
    }

    private fun install(pack: VoicePack, archive: File) {
        set(pack.id, PackState.Installing)
        val target = packDir(pack.id)
        try {
            target.deleteRecursively()
            target.mkdirs()
            archive.inputStream().buffered(1 shl 16).use { extractTarBz2(it, target) }
            File(target, ".ready").writeText("ok")
            set(pack.id, PackState.Ready)
            archive.delete()
            sizeFile(pack).delete()
        } catch (e: Exception) {
            target.deleteRecursively()
            archive.delete()
            sizeFile(pack).delete()
            set(pack.id, PackState.Failed("Could not unpack the voice (${e.message}). Please download it again."))
        }
    }

    private fun extractTarBz2(input: InputStream, target: File) {
        TarArchiveInputStream(BZip2CompressorInputStream(BufferedInputStream(input))).use { tar ->
            val canonicalTarget = target.canonicalPath + File.separator
            var entry = tar.nextEntry
            while (entry != null) {
                val out = File(target, entry.name)
                if (!out.canonicalPath.startsWith(canonicalTarget)) throw SecurityException("Bad archive entry")
                if (entry.isDirectory) out.mkdirs() else {
                    out.parentFile?.mkdirs()
                    out.outputStream().buffered(1 shl 16).use { tar.copyTo(it) }
                }
                entry = tar.nextEntry
            }
        }
    }

    fun modelRoot(packId: String): File {
        val dir = packDir(packId)
        return dir.listFiles()?.firstOrNull { it.isDirectory && it.listFiles()?.any { f -> f.name.endsWith(".onnx") } == true } ?: dir
    }

    fun customDir(id: String) = File(customRoot, id)

    private fun loadCustom(): List<CustomVoiceInfo> = customRoot.listFiles()?.mapNotNull { dir ->
        val meta = File(dir, "voice.json")
        if (meta.exists()) runCatching { Json.decodeFromString<CustomVoiceInfo>(meta.readText()) }.getOrNull() else null
    }?.sortedBy { it.name } ?: emptyList()

    fun importCustom(uri: Uri, name: String): Result<CustomVoiceInfo> = runCatching {
        val id = "custom-" + System.currentTimeMillis()
        val dir = customDir(id)
        dir.mkdirs()
        try {
            context.contentResolver.openInputStream(uri)!!.use { stream ->
                ZipInputStream(stream.buffered()).use { zip ->
                    val canonical = dir.canonicalPath + File.separator
                    var entry = zip.nextEntry
                    while (entry != null) {
                        val out = File(dir, entry.name)
                        if (!out.canonicalPath.startsWith(canonical)) throw SecurityException("Bad archive entry")
                        if (entry.isDirectory) out.mkdirs() else {
                            out.parentFile?.mkdirs()
                            out.outputStream().use { zip.copyTo(it) }
                        }
                        entry = zip.nextEntry
                    }
                }
            }
            val model = dir.walkTopDown().firstOrNull { it.name.endsWith(".onnx") }
                ?: error("No .onnx model found in the zip")
            val tokens = dir.walkTopDown().firstOrNull { it.name == "tokens.txt" }
                ?: error("No tokens.txt found in the zip")
            if (model.parentFile != tokens.parentFile) error("Model and tokens.txt must be in the same folder")
            val info = CustomVoiceInfo(id, name.ifBlank { "My voice" })
            File(dir, "voice.json").writeText(Json.encodeToString(CustomVoiceInfo.serializer(), info))
            _custom.value = loadCustom()
            info
        } catch (e: Exception) {
            dir.deleteRecursively()
            throw e
        }
    }

    fun deleteCustom(id: String) {
        customDir(id).deleteRecursively()
        _custom.value = loadCustom()
    }
}
