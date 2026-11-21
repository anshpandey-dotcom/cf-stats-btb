package com.fleetmc.flipshelf.reader

import android.content.Context
import android.os.Build
import android.os.Bundle
import android.text.InputType
import android.view.KeyEvent
import android.view.View
import android.view.WindowManager
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.fleetmc.flipshelf.R
import com.fleetmc.flipshelf.core.Book
import com.fleetmc.flipshelf.core.BookSource
import com.fleetmc.flipshelf.core.BookSources
import com.fleetmc.flipshelf.core.Library
import com.google.android.material.appbar.MaterialToolbar
import kotlin.math.roundToInt

class ReaderActivity : AppCompatActivity(), PageCurlView.Host {

    private lateinit var library: Library
    private lateinit var book: Book
    private lateinit var curl: PageCurlView
    private lateinit var toolbar: MaterialToolbar
    private lateinit var topBar: FrameLayout
    private lateinit var bottomBar: LinearLayout
    private lateinit var pageLabel: TextView
    private lateinit var seek: SeekBar
    private lateinit var loading: View
    private lateinit var errorView: TextView
    private lateinit var controller: WindowInsetsControllerCompat

    private var source: BookSource? = null
    private var cache: PageCache? = null
    private var chromeVisible = true
    private var textScale = 1f
    private var layoutKey = ""
    private var restoreFraction = 0f
    private val prefs by lazy { getSharedPreferences("reader", Context.MODE_PRIVATE) }
    private val hideChrome = Runnable { setChrome(false) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= 28) {
            window.attributes.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContentView(R.layout.activity_reader)

        library = Library.get(this)
        val id = intent.getStringExtra(EXTRA_BOOK_ID)
        val found = id?.let { library.find(it) }
        if (found == null) {
            finish()
            return
        }
        book = found
        restoreFraction = book.progress

        curl = findViewById(R.id.curl)
        toolbar = findViewById(R.id.toolbar)
        topBar = findViewById(R.id.topBar)
        bottomBar = findViewById(R.id.bottomBar)
        pageLabel = findViewById(R.id.pageLabel)
        seek = findViewById(R.id.seek)
        loading = findViewById(R.id.loading)
        errorView = findViewById(R.id.error)
        controller = WindowInsetsControllerCompat(window, window.decorView)
        controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE

        textScale = prefs.getFloat("text_scale", 1f)
        curl.night = prefs.getBoolean("night", false)
        curl.soundEnabled = prefs.getBoolean("sound", true)
        curl.sound = FlipSound()
        curl.keepScreenOn = true
        curl.host = this

        toolbar.title = book.title
        toolbar.setNavigationOnClickListener { finish() }
        toolbar.menu.findItem(R.id.action_night).isChecked = curl.night
        toolbar.menu.findItem(R.id.action_sound).isChecked = curl.soundEnabled
        toolbar.setOnMenuItemClickListener { onMenu(it.itemId) }

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.root)) { _, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
            topBar.setPadding(bars.left, bars.top, bars.right, 0)
            bottomBar.setPadding(bars.left + dp(16), dp(24), bars.right + dp(16), bars.bottom)
            insets
        }

        seek.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(bar: SeekBar, progress: Int, fromUser: Boolean) {
                if (fromUser) pageLabel.text = getString(R.string.page_of, progress + 1, seek.max + 1)
            }

            override fun onStartTrackingTouch(bar: SeekBar) {
                handlerRemove()
            }

            override fun onStopTrackingTouch(bar: SeekBar) {
                curl.goTo(bar.progress)
            }
        })

        Thread {
            try {
                val s = BookSources.open(library, book)
                runOnUiThread { if (isDestroyed) s.close() else start(s) }
            } catch (e: Throwable) {
                runOnUiThread { showError(e.message ?: e.javaClass.simpleName) }
            }
        }.start()

        setChrome(true)
        curl.postDelayed(hideChrome, 2600)
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun handlerRemove() {
        curl.removeCallbacks(hideChrome)
    }

    private fun start(s: BookSource) {
        source = s
        toolbar.menu.findItem(R.id.action_larger).isVisible = s.reflowable
        toolbar.menu.findItem(R.id.action_smaller).isVisible = s.reflowable
        cache = PageCache(s, { curl.isWanted(it) }, { curl.invalidate() }).also { curl.cache = it }
        curl.aspect = s.aspect
        if (curl.pageWidth > 0) layoutBook(curl.pageWidth, curl.pageHeight)
    }

    private fun showError(message: String) {
        loading.visibility = View.GONE
        errorView.text = getString(R.string.open_failed, message)
        errorView.visibility = View.VISIBLE
    }

    private fun layoutBook(w: Int, h: Int) {
        val s = source ?: return
        val c = cache ?: return
        val key = "$w x $h x $textScale"
        if (key == layoutKey) return
        layoutKey = key
        val fraction = if (curl.pageCount > 0) curl.index / curl.pageCount.toFloat() else restoreFraction
        c.reset(w, h)
        loading.visibility = View.VISIBLE
        c.submit {
            try {
                s.prepare(w, h, textScale)
                val count = s.pageCount
                runOnUiThread {
                    if (key != layoutKey || isDestroyed) return@runOnUiThread
                    loading.visibility = View.GONE
                    curl.setPageCount(count, (fraction * count).roundToInt())
                    seek.max = (count - 1).coerceAtLeast(0)
                    seek.progress = curl.index
                    updateLabel()
                }
            } catch (e: Throwable) {
                runOnUiThread { showError(e.message ?: e.javaClass.simpleName) }
            }
        }
    }

    override fun onPageSizeChanged(width: Int, height: Int) {
        layoutBook(width, height)
    }

    override fun onPageChanged(index: Int) {
        seek.progress = index
        updateLabel()
        if (chromeVisible) {
            handlerRemove()
            curl.postDelayed(hideChrome, 1800)
        }
    }

    override fun onCenterTap() {
        setChrome(!chromeVisible)
    }

    private fun updateLabel() {
        pageLabel.text = getString(R.string.page_of, curl.index + 1, curl.pageCount)
    }

    private fun setChrome(visible: Boolean) {
        chromeVisible = visible
        handlerRemove()
        val bars = androidx.core.view.WindowInsetsCompat.Type.systemBars()
        if (visible) controller.show(bars) else controller.hide(bars)
        val alpha = if (visible) 1f else 0f
        for (v in listOf(topBar, bottomBar)) {
            if (visible) v.visibility = View.VISIBLE
            v.animate().alpha(alpha).setDuration(180).withEndAction {
                if (!visible) v.visibility = View.INVISIBLE
            }.start()
        }
    }

    private fun onMenu(id: Int): Boolean {
        when (id) {
            R.id.action_goto -> askPage()
            R.id.action_larger -> changeText(0.1f)
            R.id.action_smaller -> changeText(-0.1f)
            R.id.action_night -> {
                curl.night = !curl.night
                toolbar.menu.findItem(R.id.action_night).isChecked = curl.night
                prefs.edit().putBoolean("night", curl.night).apply()
            }
            R.id.action_sound -> {
                curl.soundEnabled = !curl.soundEnabled
                toolbar.menu.findItem(R.id.action_sound).isChecked = curl.soundEnabled
                prefs.edit().putBoolean("sound", curl.soundEnabled).apply()
            }
            else -> return false
        }
        return true
    }

    private fun changeText(delta: Float) {
        val next = (textScale + delta).coerceIn(0.7f, 1.8f)
        if (next == textScale) return
        textScale = next
        prefs.edit().putFloat("text_scale", textScale).apply()
        layoutBook(curl.pageWidth, curl.pageHeight)
    }

    private fun askPage() {
        val input = EditText(this)
        input.inputType = InputType.TYPE_CLASS_NUMBER
        input.hint = getString(R.string.page_number)
        val box = FrameLayout(this)
        box.setPadding(dp(24), dp(8), dp(24), 0)
        box.addView(input)
        AlertDialog.Builder(this)
            .setTitle(R.string.go_to_page)
            .setView(box)
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.ok) { _, _ ->
                input.text.toString().toIntOrNull()?.let { curl.goTo(it - 1) }
            }
            .show()
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        return when (keyCode) {
            KeyEvent.KEYCODE_VOLUME_DOWN -> {
                curl.flipNext()
                true
            }
            KeyEvent.KEYCODE_VOLUME_UP -> {
                curl.flipPrevious()
                true
            }
            else -> super.onKeyDown(keyCode, event)
        }
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean {
        if (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN || keyCode == KeyEvent.KEYCODE_VOLUME_UP) return true
        return super.onKeyUp(keyCode, event)
    }

    private fun saveProgress() {
        if (!::book.isInitialized || curl.pageCount == 0) return
        book.progress = if (curl.pageCount > 1) curl.index / curl.pageCount.toFloat() else 0f
        if (curl.index >= curl.pageCount - 1) book.progress = 1f
        book.pages = curl.pageCount
        book.openedAt = System.currentTimeMillis()
        library.update(book)
    }

    override fun onPause() {
        super.onPause()
        saveProgress()
    }

    override fun onDestroy() {
        super.onDestroy()
        if (!::curl.isInitialized) return
        curl.removeCallbacks(hideChrome)
        curl.sound?.release()
        val c = cache
        val s = source
        if (c != null) {
            if (s != null) c.submit { s.close() }
            c.shutdown()
        } else s?.close()
    }

    companion object {
        const val EXTRA_BOOK_ID = "book_id"
    }
}
