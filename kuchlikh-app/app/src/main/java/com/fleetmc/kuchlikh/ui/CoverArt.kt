package com.fleetmc.kuchlikh.ui

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.util.LruCache
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalContext
import com.fleetmc.kuchlikh.data.Book
import com.fleetmc.kuchlikh.data.ImageTools
import com.fleetmc.kuchlikh.data.PageSpec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

object CoverBitmaps {

    private val cache = LruCache<String, Bitmap>(10)

    fun load(context: Context, model: Any?, maxSide: Int = 900): Bitmap? {
        val key = when (model) {
            is File -> "${model.path}/${model.lastModified()}"
            is Uri -> model.toString()
            else -> return null
        }
        cache.get(key)?.let { return it }
        val bytes = runCatching {
            when (model) {
                is File -> model.readBytes()
                is Uri -> context.contentResolver.openInputStream(model)?.use { it.readBytes() }
                else -> null
            }
        }.getOrNull() ?: return null
        val bitmap = ImageTools.decode(bytes, maxSide) ?: return null
        cache.put(key, bitmap)
        return bitmap
    }
}

@Composable
fun CoverArt(
    title: String,
    author: String,
    colorIndex: Int,
    style: String,
    font: String,
    image: Any?,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val bitmap by produceState<Bitmap?>(null, image) {
        value = if (image == null) null else withContext(Dispatchers.IO) { CoverBitmaps.load(context, image) }
    }
    Canvas(modifier) {
        drawIntoCanvas {
            val canvas = it.nativeCanvas
            canvas.save()
            canvas.scale(size.width / PageSpec.W, size.height / PageSpec.H)
            CoverPainter.draw(context, canvas, CoverSpec(title, author, CoverPainter.color(colorIndex), style, font, bitmap))
            canvas.restore()
        }
    }
}

@Composable
fun BookCover(book: Book, coverFile: File?, modifier: Modifier = Modifier) {
    CoverArt(book.title, book.author, book.coverColor, book.coverStyle, book.coverFont, coverFile, modifier)
}
