package com.clappingcat.ar.sprite

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Handler
import android.os.HandlerThread

class SpritePlayer(private val context: Context, private val clip: SpriteClip) {

    @Volatile
    var current: Bitmap? = null
        private set

    private val thread = HandlerThread("sprite-${clip.directory}").also { it.start() }
    private val handler = Handler(thread.looper)
    private val pool = arrayOfNulls<Bitmap>(POOL_SIZE)
    private var nextSlot = 0
    private var decodedIndex = -1

    @Volatile
    private var requestedIndex = -1

    fun request(sequence: Int) {
        val index = clip.assetIndex(sequence)
        if (index == requestedIndex) return
        requestedIndex = index
        handler.post { decode() }
    }

    fun release() {
        thread.quitSafely()
    }

    private fun decode() {
        val wanted = requestedIndex
        if (wanted == decodedIndex || wanted < 0) return
        val options = BitmapFactory.Options()
        options.inMutable = true
        options.inPreferredConfig = Bitmap.Config.ARGB_8888
        options.inBitmap = pool[nextSlot]
        val name = "${clip.directory}/f%03d.webp".format(wanted)
        val bitmap = try {
            context.assets.open(name).use { BitmapFactory.decodeStream(it, null, options) }
        } catch (e: IllegalArgumentException) {
            options.inBitmap = null
            context.assets.open(name).use { BitmapFactory.decodeStream(it, null, options) }
        } ?: return
        pool[nextSlot] = bitmap
        nextSlot = (nextSlot + 1) % POOL_SIZE
        decodedIndex = wanted
        current = bitmap
    }

    private companion object {
        const val POOL_SIZE = 4
    }
}
