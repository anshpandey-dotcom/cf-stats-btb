package com.ansh.recalm.tts

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
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
    private val prefs = context.getSharedPreferences("voice_downloads", Context.MODE_PRIVATE)
    private val downloads = context.getSystemService(DownloadManager::class.java)

    private val _states = MutableStateFlow(VoiceCatalog.packs.associate { it.id to stateOnDisk(it.id) })
    val states: StateFlow<Map<String, PackState>> = _states.asStateFlow()

    private val _custom = MutableStateFlow(loadCustom())
    val customVoices: StateFlow<List<CustomVoiceInfo>> = _custom.asStateFlow()

    init {
        VoiceCatalog.packs.forEach { pack ->
            val id = prefs.getLong(pack.id, -1L)
            if (id >= 0 && _states.value[pack.id] != PackState.Ready) watch(pack, id)
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
        val request = DownloadManager.Request(Uri.parse(pack.url))
            .setTitle("Recalm voice: ${pack.title}")
            .setDescription("Downloading voice for offline reading")
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE)
            .setDestinationInExternalFilesDir(context, null, "${pack.id}.tar.bz2")
            .setAllowedOverMetered(true)
        File(context.getExternalFilesDir(null), "${pack.id}.tar.bz2").delete()
        val id = downloads.enqueue(request)
        prefs.edit().putLong(pack.id, id).apply()
        set(pack.id, PackState.Downloading(0f))
        watch(pack, id)
    }

    fun cancel(pack: VoicePack) {
        val id = prefs.getLong(pack.id, -1L)
        if (id >= 0) downloads.remove(id)
        prefs.edit().remove(pack.id).apply()
        set(pack.id, PackState.Missing)
    }

    fun delete(pack: VoicePack) {
        packDir(pack.id).deleteRecursively()
        set(pack.id, PackState.Missing)
    }

    private fun set(id: String, state: PackState) = _states.update { it + (id to state) }

    private fun watch(pack: VoicePack, downloadId: Long) = scope.launch {
        while (true) {
            val cursor = downloads.query(DownloadManager.Query().setFilterById(downloadId))
            if (cursor == null || !cursor.moveToFirst()) {
                cursor?.close()
                prefs.edit().remove(pack.id).apply()
                set(pack.id, stateOnDisk(pack.id))
                return@launch
            }
            val status = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
            val done = cursor.getLong(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR))
            val total = cursor.getLong(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES))
            cursor.close()
            when (status) {
                DownloadManager.STATUS_SUCCESSFUL -> {
                    prefs.edit().remove(pack.id).apply()
                    install(pack)
                    return@launch
                }
                DownloadManager.STATUS_FAILED -> {
                    prefs.edit().remove(pack.id).apply()
                    set(pack.id, PackState.Failed("Download failed. Check your connection and try again."))
                    return@launch
                }
                else -> set(pack.id, PackState.Downloading(if (total > 0) done.toFloat() / total else 0f))
            }
            delay(700)
        }
    }

    private fun install(pack: VoicePack) {
        set(pack.id, PackState.Installing)
        val archive = File(context.getExternalFilesDir(null), "${pack.id}.tar.bz2")
        val target = packDir(pack.id)
        try {
            target.deleteRecursively()
            target.mkdirs()
            archive.inputStream().buffered(1 shl 16).use { extractTarBz2(it, target) }
            File(target, ".ready").writeText("ok")
            set(pack.id, PackState.Ready)
        } catch (e: Exception) {
            target.deleteRecursively()
            set(pack.id, PackState.Failed("Could not install voice: ${e.message}"))
        } finally {
            archive.delete()
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
