package com.attackmode.planner.data

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.MediaScannerConnection
import android.os.Build
import android.os.Environment
import androidx.core.content.ContextCompat
import java.io.File
import java.io.FileOutputStream

object Files {
    const val FOLDER = "Attack Mode"
    const val FILE_NAME = "attack-mode-data.json"

    fun root(): File = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS), FOLDER)

    fun backupFile(): File = File(root(), FILE_NAME)

    fun hasAccess(context: Context): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
        }

    fun displayPath(): String = "Documents/$FOLDER/$FILE_NAME"

    fun readBackup(context: Context): String? {
        val file = backupFile()
        if (!hasAccess(context) || !file.isFile) return null
        return runCatching { file.readText() }.getOrNull()
    }

    fun writeAtomic(target: File, bytes: ByteArray): Boolean {
        val folder = target.parentFile ?: return false
        if (!folder.isDirectory && !folder.mkdirs()) return false
        val temp = File(folder, target.name + ".tmp")
        return runCatching {
            FileOutputStream(temp).use {
                it.write(bytes)
                it.fd.sync()
            }
            if (!temp.renameTo(target)) {
                target.delete()
                if (!temp.renameTo(target)) error("rename failed")
            }
            true
        }.getOrDefault(false)
    }

    fun writeBackup(context: Context, json: String): Boolean {
        if (!hasAccess(context)) return false
        return writeAtomic(backupFile(), json.toByteArray())
    }

    private fun safe(value: String): String = value.replace(Regex("[^A-Za-z0-9._ -]"), "_")

    fun saveFile(context: Context, folder: String, name: String, bytes: ByteArray): Boolean {
        if (!hasAccess(context)) return false
        val dir = if (folder.isEmpty()) root() else File(root(), safe(folder))
        val target = File(dir, safe(name))
        val ok = writeAtomic(target, bytes)
        if (ok) MediaScannerConnection.scanFile(context, arrayOf(target.absolutePath), null, null)
        return ok
    }

    fun list(context: Context, folder: String): List<String> {
        if (!hasAccess(context)) return emptyList()
        val dir = if (folder.isEmpty()) root() else File(root(), safe(folder))
        return dir.listFiles()?.filter { it.isFile && !it.name.endsWith(".tmp") }?.map { it.name }?.sorted().orEmpty()
    }
}
