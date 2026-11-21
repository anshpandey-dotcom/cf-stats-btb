package com.fleetmc.flipshelf.library

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import android.widget.Button
import android.widget.Toast
import com.google.android.material.progressindicator.LinearProgressIndicator
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.PopupMenu
import android.widget.ProgressBar
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SearchView
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.fleetmc.flipshelf.R
import com.fleetmc.flipshelf.core.Book
import com.fleetmc.flipshelf.core.Importer
import com.fleetmc.flipshelf.core.Format
import com.fleetmc.flipshelf.core.Library
import com.fleetmc.flipshelf.translate.TranslateService
import com.fleetmc.flipshelf.translate.TranslationState
import com.fleetmc.flipshelf.reader.ReaderActivity
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton
import com.google.android.material.snackbar.Snackbar
import java.util.concurrent.Executors
import kotlin.math.max

class MainActivity : AppCompatActivity() {

    private lateinit var library: Library
    private lateinit var importer: Importer
    private lateinit var adapter: BookAdapter
    private lateinit var list: RecyclerView
    private lateinit var empty: View
    private lateinit var fab: ExtendedFloatingActionButton
    private val worker = Executors.newSingleThreadExecutor()
    private var query = ""
    private var spans = 2

    private var pendingTranslate: Book? = null
    private lateinit var banner: View
    private lateinit var bannerText: TextView
    private lateinit var bannerProgress: LinearProgressIndicator
    private val translateListener = TranslationState.Listener { job ->
        if (job == null) {
            banner.visibility = View.GONE
        } else if (job.finished) {
            banner.visibility = View.GONE
            refresh()
            job.error?.let { Snackbar.make(list, it, Snackbar.LENGTH_LONG).show() }
        } else {
            banner.visibility = View.VISIBLE
            bannerText.text = if (job.indeterminate) "${job.phase}  ${job.title}" else "${job.phase} ${job.percent}%  ${job.title}"
            bannerProgress.isIndeterminate = job.indeterminate
            if (!job.indeterminate) bannerProgress.setProgressCompat(job.percent, true)
        }
    }

    private val notificationPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) {
        pendingTranslate?.let { launchTranslate(it) }
        pendingTranslate = null
    }

    private val picker = registerForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isNotEmpty()) importUris(uris, false)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        library = Library.get(this)
        importer = Importer(this, library)

        val toolbar = findViewById<MaterialToolbar>(R.id.toolbar)
        list = findViewById(R.id.list)
        empty = findViewById(R.id.empty)
        fab = findViewById(R.id.fab)
        banner = findViewById(R.id.translateBanner)
        bannerText = findViewById(R.id.translateText)
        bannerProgress = findViewById(R.id.translateProgress)
        findViewById<Button>(R.id.translateCancel).setOnClickListener { TranslateService.cancel(this) }

        val widthDp = resources.displayMetrics.widthPixels / resources.displayMetrics.density
        spans = max(2, (widthDp / 160f).toInt())
        adapter = BookAdapter(library, { spans }, { openBook(it) }, { book, anchor -> showMenu(book, anchor) })
        list.layoutManager = GridLayoutManager(this, spans)
        list.adapter = adapter

        val search = toolbar.menu.findItem(R.id.action_search).actionView as SearchView
        search.queryHint = getString(R.string.search_hint)
        search.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(q: String?) = true
            override fun onQueryTextChange(q: String?): Boolean {
                query = q.orEmpty()
                refresh()
                return true
            }
        })

        fab.setOnClickListener {
            picker.launch(
                arrayOf(
                    "application/pdf",
                    "application/epub+zip",
                    "application/zip",
                    "application/x-cbz",
                    "application/vnd.comicbook+zip",
                    "text/plain",
                    "image/*",
                    "application/octet-stream"
                )
            )
        }

        list.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(rv: RecyclerView, dx: Int, dy: Int) {
                if (dy > 6) fab.shrink() else if (dy < -6) fab.extend()
            }
        })

        if (savedInstanceState == null) handleViewIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleViewIntent(intent)
    }

    private fun handleViewIntent(intent: Intent?) {
        val data = intent?.data
        if (intent?.action == Intent.ACTION_VIEW && data != null) {
            intent.action = null
            importUris(listOf(data), true)
        }
    }

    override fun onStart() {
        super.onStart()
        TranslationState.add(translateListener)
    }

    override fun onStop() {
        super.onStop()
        TranslationState.remove(translateListener)
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    private fun translate(book: Book) {
        if (book.format == Format.COMIC || book.format == Format.IMAGES) {
            Toast.makeText(this, R.string.translate_unavailable, Toast.LENGTH_SHORT).show()
            return
        }
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            pendingTranslate = book
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
            return
        }
        launchTranslate(book)
    }

    private fun launchTranslate(book: Book) {
        TranslateService.start(this, book.id)
        Snackbar.make(list, R.string.translate_started, Snackbar.LENGTH_LONG).show()
    }

    private fun refresh() {
        val q = query.trim().lowercase()
        val books = library.all()
            .filter { q.isEmpty() || it.title.lowercase().contains(q) }
            .map { it.copy() }
        adapter.submitList(books)
        empty.visibility = if (books.isEmpty() && q.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun openBook(book: Book) {
        startActivity(Intent(this, ReaderActivity::class.java).putExtra(ReaderActivity.EXTRA_BOOK_ID, book.id))
    }

    private fun showMenu(book: Book, anchor: View) {
        val menu = PopupMenu(this, anchor)
        menu.menu.add(0, 1, 0, R.string.rename)
        if (book.format != Format.COMIC && book.format != Format.IMAGES) menu.menu.add(0, 3, 1, R.string.translate_hindi)
        menu.menu.add(0, 2, 2, R.string.delete)
        menu.setOnMenuItemClickListener {
            when (it.itemId) {
                1 -> rename(book)
                2 -> confirmDelete(book)
                3 -> translate(book)
            }
            true
        }
        menu.show()
    }

    private fun rename(book: Book) {
        val input = EditText(this)
        input.setText(book.title)
        input.setSelection(input.text.length)
        val box = FrameLayout(this)
        val pad = (24 * resources.displayMetrics.density).toInt()
        box.setPadding(pad, pad / 3, pad, 0)
        box.addView(input)
        AlertDialog.Builder(this)
            .setTitle(R.string.rename)
            .setView(box)
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.ok) { _, _ ->
                val name = input.text.toString().trim()
                if (name.isNotEmpty()) {
                    library.find(book.id)?.let {
                        it.title = name
                        library.update(it)
                        refresh()
                    }
                }
            }
            .show()
    }

    private fun confirmDelete(book: Book) {
        AlertDialog.Builder(this)
            .setTitle(R.string.delete_title)
            .setMessage(getString(R.string.delete_message, book.title))
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.delete) { _, _ ->
                worker.execute {
                    library.find(book.id)?.let { library.remove(it) }
                    runOnUiThread {
                        adapter.forget(book)
                        refresh()
                    }
                }
            }
            .show()
    }

    private fun importUris(uris: List<Uri>, openAfter: Boolean) {
        val bar = ProgressBar(this)
        val label = TextView(this)
        val box = LinearLayout(this)
        val pad = (24 * resources.displayMetrics.density).toInt()
        box.orientation = LinearLayout.HORIZONTAL
        box.setPadding(pad, pad, pad, pad)
        label.setPadding(pad, 0, 0, 0)
        box.addView(bar)
        box.addView(label)
        label.text = getString(R.string.importing, 1, uris.size)
        val dialog = AlertDialog.Builder(this).setView(box).setCancelable(false).create()
        dialog.show()

        worker.execute {
            val added = ArrayList<Book>()
            val errors = ArrayList<String>()
            val names = uris.map { it.lastPathSegment.orEmpty() }
            val allImages = uris.size > 1 && names.all { importer.isImage(it) }
            if (allImages) {
                try {
                    runOnUiThread { label.text = getString(R.string.importing, 1, 1) }
                    added.add(importer.importImages(uris, getString(R.string.image_book_title)))
                } catch (e: Exception) {
                    errors.add(getString(R.string.import_failed, getString(R.string.image_book_title), e.message ?: ""))
                }
            } else {
                uris.forEachIndexed { i, uri ->
                    runOnUiThread { label.text = getString(R.string.importing, i + 1, uris.size) }
                    try {
                        added.add(importer.importUri(uri))
                    } catch (e: Exception) {
                        errors.add(getString(R.string.import_failed, names[i], e.message ?: ""))
                    }
                }
            }
            runOnUiThread {
                if (isDestroyed) return@runOnUiThread
                dialog.dismiss()
                refresh()
                if (errors.isNotEmpty()) {
                    Snackbar.make(list, errors.first(), Snackbar.LENGTH_LONG).show()
                } else if (added.isNotEmpty() && !openAfter) {
                    Snackbar.make(list, getString(R.string.import_done, added.size), Snackbar.LENGTH_SHORT).show()
                }
                if (openAfter && added.isNotEmpty()) openBook(added.first())
            }
        }
    }
}
