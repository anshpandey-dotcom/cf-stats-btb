package com.fleetmc.flipshelf

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.os.Looper
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import androidx.test.core.app.ApplicationProvider
import com.fleetmc.flipshelf.core.BookSource
import com.fleetmc.flipshelf.reader.PageCache
import com.fleetmc.flipshelf.reader.PageCurlView
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.io.FileOutputStream
import java.time.Duration

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class CurlRenderTest {

    private class FakeSource(override val pageCount: Int) : BookSource {
        override val aspect = 0.7f
        override val reflowable = false
        override fun prepare(layoutWidth: Int, layoutHeight: Int, textScale: Float) = Unit
        override fun render(index: Int, width: Int, height: Int): Bitmap {
            val b = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            val c = Canvas(b)
            c.drawColor(if (index % 2 == 0) Color.WHITE else Color.rgb(255, 244, 214))
            val p = Paint(Paint.ANTI_ALIAS_FLAG)
            p.color = Color.rgb(30, 30, 30)
            p.textSize = width * 0.12f
            c.drawText("Page ${index + 1}", width * 0.08f, height * 0.1f, p)
            p.textSize = width * 0.045f
            for (i in 0 until 24) c.drawText("Line $i the quick brown fox jumps over the lazy dog", width * 0.08f, height * (0.16f + i * 0.034f), p)
            return b
        }

        override fun close() = Unit
    }

    private val out = File("build/curl-android").apply { mkdirs() }

    private fun snap(view: View, name: String) {
        val bmp = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        view.draw(Canvas(bmp))
        FileOutputStream(File(out, "$name.png")).use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private fun pump(ms: Long) {
        val end = System.currentTimeMillis() + ms
        while (System.currentTimeMillis() < end) {
            shadowOf(Looper.getMainLooper()).idle()
            Thread.sleep(15)
        }
    }

    private fun event(action: Int, x: Float, y: Float, t: Long): MotionEvent =
        MotionEvent.obtain(t - 40, t, action, x, y, 0)

    @Test
    fun dragAndRelease() {
        val ctx = ApplicationProvider.getApplicationContext<Application>()
        val view = PageCurlView(ctx)
        val source = FakeSource(10)
        var changed = -1
        lateinit var cache: PageCache
        view.host = object : PageCurlView.Host {
            override fun onPageChanged(index: Int) {
                changed = index
            }

            override fun onCenterTap() = Unit
            override fun onPageSizeChanged(width: Int, height: Int) {
                cache.reset(width, height)
                view.setPageCount(10, 0)
            }
        }
        cache = PageCache(source, { true }, { view.invalidate() })
        view.cache = cache
        view.measure(View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(2000, View.MeasureSpec.EXACTLY))
        view.layout(0, 0, 1080, 2000)
        view.aspect = 0.7f
        assertTrue(view.pageWidth > 0)
        pump(800)
        snap(view, "1_rest")

        val t0 = SystemClock.uptimeMillis()
        view.dispatchTouchEvent(event(MotionEvent.ACTION_DOWN, 980f, 1650f, t0))
        view.dispatchTouchEvent(event(MotionEvent.ACTION_MOVE, 930f, 1630f, t0 + 16))
        view.dispatchTouchEvent(event(MotionEvent.ACTION_MOVE, 820f, 1600f, t0 + 32))
        pump(100)
        snap(view, "2_drag_small")
        view.dispatchTouchEvent(event(MotionEvent.ACTION_MOVE, 600f, 1500f, t0 + 48))
        pump(100)
        snap(view, "3_drag_mid")
        view.dispatchTouchEvent(event(MotionEvent.ACTION_MOVE, 250f, 1350f, t0 + 64))
        pump(100)
        snap(view, "4_drag_far")
        view.dispatchTouchEvent(event(MotionEvent.ACTION_UP, 200f, 1340f, t0 + 80))
        pump(1200)
        assertEquals(1, view.index)
        assertEquals(1, changed)
        snap(view, "5_after_flip")

        val t1 = SystemClock.uptimeMillis() + 5000
        view.dispatchTouchEvent(event(MotionEvent.ACTION_DOWN, 60f, 900f, t1))
        view.dispatchTouchEvent(event(MotionEvent.ACTION_MOVE, 120f, 900f, t1 + 16))
        view.dispatchTouchEvent(event(MotionEvent.ACTION_MOVE, 300f, 880f, t1 + 32))
        view.dispatchTouchEvent(event(MotionEvent.ACTION_MOVE, 520f, 860f, t1 + 48))
        pump(100)
        snap(view, "6_back_drag")
        view.dispatchTouchEvent(event(MotionEvent.ACTION_MOVE, 1070f, 850f, t1 + 64))
        pump(60)
        val probe = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        view.draw(Canvas(probe))
        assertEquals(255, Color.blue(probe.getPixel(view.width / 2, view.height / 2)))
        view.dispatchTouchEvent(event(MotionEvent.ACTION_UP, 1070f, 850f, t1 + 80))
        pump(1200)
        assertEquals(0, view.index)

        view.flipNext()
        pump(300)
        snap(view, "7_tap_mid_anim")
        pump(1200)
        assertEquals(1, view.index)

        view.night = true
        view.flipNext()
        pump(330)
        snap(view, "8_night_anim")
        pump(1200)
        assertEquals(2, view.index)
    }
}
