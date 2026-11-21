package com.fleetmc.flipshelf.reader

import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.util.LruCache
import com.fleetmc.flipshelf.core.BookSource
import java.util.concurrent.Executors

class PageCache(
    private val source: BookSource,
    private val isWanted: (Int) -> Boolean,
    private val onReady: () -> Unit
) {
    private val worker = Executors.newSingleThreadExecutor { r -> Thread(r, "page-render").apply { priority = Thread.NORM_PRIORITY - 1 } }
    private val main = Handler(Looper.getMainLooper())
    private val lru = LruCache<Int, Bitmap>(7)
    private val pending = HashSet<Int>()
    private var generation = 0
    private var hiIndex = -1
    private var hiBitmap: Bitmap? = null
    private var hiPending = -1
    var width = 0
        private set
    var height = 0
        private set

    fun reset(w: Int, h: Int) {
        generation++
        width = w
        height = h
        lru.evictAll()
        pending.clear()
        hiBitmap = null
        hiIndex = -1
        hiPending = -1
    }

    fun get(index: Int): Bitmap? = lru.get(index)

    fun hi(index: Int): Bitmap? = if (index == hiIndex) hiBitmap else null

    fun request(index: Int) {
        if (width <= 0 || index < 0 || index >= source.pageCount) return
        if (lru.get(index) != null || !pending.add(index)) return
        val gen = generation
        val w = width
        val h = height
        worker.execute {
            if (gen != generation || !isWanted(index)) {
                main.post { pending.remove(index) }
                return@execute
            }
            val bmp = try {
                source.render(index, w, h)
            } catch (_: Throwable) {
                null
            }
            main.post {
                pending.remove(index)
                if (gen == generation && bmp != null) {
                    lru.put(index, bmp)
                    onReady()
                }
            }
        }
    }

    fun requestHi(index: Int, scale: Float) {
        if (width <= 0 || index == hiIndex || index == hiPending) return
        hiPending = index
        val gen = generation
        val w = (width * scale).toInt()
        val h = (height * scale).toInt()
        worker.execute {
            val bmp = try {
                source.render(index, w, h)
            } catch (_: Throwable) {
                null
            }
            main.post {
                if (gen == generation && hiPending == index) {
                    hiPending = -1
                    if (bmp != null) {
                        hiBitmap = bmp
                        hiIndex = index
                        onReady()
                    }
                }
            }
        }
    }

    fun clearHi() {
        hiBitmap = null
        hiIndex = -1
        hiPending = -1
    }

    fun submit(task: Runnable) {
        worker.execute(task)
    }

    fun shutdown() {
        generation++
        worker.shutdown()
    }
}
