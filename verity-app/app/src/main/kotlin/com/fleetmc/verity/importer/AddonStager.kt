package com.fleetmc.verity.importer

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import com.fleetmc.verity.data.Addon
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class AddonStager(private val context: Context) {

    suspend fun stage(addon: Addon): Uri = withContext(Dispatchers.IO) {
        val directory = File(context.cacheDir, CACHE_DIRECTORY).apply { mkdirs() }
        val target = File(directory, addon.file)
        context.assets.open(addon.filePath).use { input ->
            target.outputStream().use { output -> input.copyTo(output) }
        }
        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", target)
    }

    private companion object {
        const val CACHE_DIRECTORY = "addons"
    }
}
