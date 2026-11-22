package com.fleetmc.verity.ui.components

import android.content.Context
import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException

object AssetBitmapCache {

    private val cache = object : LruCache<String, ImageBitmap>((Runtime.getRuntime().maxMemory() / 8).toInt()) {
        override fun sizeOf(key: String, value: ImageBitmap): Int = value.asAndroidBitmap().allocationByteCount
    }

    fun peek(path: String): ImageBitmap? = cache.get(path)

    suspend fun load(context: Context, path: String): ImageBitmap? = withContext(Dispatchers.IO) {
        cache.get(path) ?: decode(context, path)?.also { cache.put(path, it) }
    }

    private fun decode(context: Context, path: String): ImageBitmap? = try {
        context.assets.open(path).use { BitmapFactory.decodeStream(it) }?.asImageBitmap()
    } catch (error: IOException) {
        null
    }
}
