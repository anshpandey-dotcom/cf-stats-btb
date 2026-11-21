package com.fleetmc.flipshelf.library

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Handler
import android.os.Looper
import android.util.LruCache
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.fleetmc.flipshelf.R
import com.fleetmc.flipshelf.core.Book
import com.fleetmc.flipshelf.core.Library
import com.google.android.material.progressindicator.LinearProgressIndicator
import java.util.concurrent.Executors
import kotlin.math.roundToInt

class BookAdapter(
    private val library: Library,
    private val spanCount: () -> Int,
    private val onOpen: (Book) -> Unit,
    private val onMenu: (Book, View) -> Unit
) : ListAdapter<Book, BookAdapter.Holder>(Diff) {

    private val covers = LruCache<String, Bitmap>(48)
    private val loader = Executors.newFixedThreadPool(2)
    private val main = Handler(Looper.getMainLooper())

    class Holder(view: View) : RecyclerView.ViewHolder(view) {
        val cover: ImageView = view.findViewById(R.id.cover)
        val badge: TextView = view.findViewById(R.id.badge)
        val title: TextView = view.findViewById(R.id.title)
        val sub: TextView = view.findViewById(R.id.sub)
        val progress: LinearProgressIndicator = view.findViewById(R.id.progress)
        val menu: ImageButton = view.findViewById(R.id.menu)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_book, parent, false)
        val holder = Holder(view)
        val density = parent.resources.displayMetrics.density
        val total = if (parent.width > 0) parent.width else parent.resources.displayMetrics.widthPixels
        val width = total / spanCount() - (12 * density).toInt()
        holder.cover.layoutParams.height = (width * 1.42f).toInt()
        return holder
    }

    override fun onBindViewHolder(holder: Holder, position: Int) {
        val book = getItem(position)
        holder.title.text = book.title
        holder.badge.text = book.format.label
        val percent = (book.progress * 100).roundToInt()
        holder.progress.setProgressCompat(percent, false)
        holder.sub.text = if (book.openedAt == 0L && percent == 0) {
            holder.itemView.context.getString(R.string.not_started)
        } else {
            holder.itemView.context.getString(R.string.read_percent, percent)
        }
        holder.itemView.setOnClickListener { onOpen(book) }
        holder.itemView.setOnLongClickListener {
            onMenu(book, holder.menu)
            true
        }
        holder.menu.setOnClickListener { onMenu(book, holder.menu) }
        holder.cover.tag = book.id
        val cached = covers.get(book.id)
        if (cached != null) {
            holder.cover.setImageBitmap(cached)
        } else {
            holder.cover.setImageDrawable(null)
            loader.execute {
                val bmp = BitmapFactory.decodeFile(library.coverOf(book).absolutePath)
                if (bmp != null) {
                    covers.put(book.id, bmp)
                    main.post { if (holder.cover.tag == book.id) holder.cover.setImageBitmap(bmp) }
                }
            }
        }
    }

    fun forget(book: Book) {
        covers.remove(book.id)
    }

    private object Diff : DiffUtil.ItemCallback<Book>() {
        override fun areItemsTheSame(a: Book, b: Book) = a.id == b.id
        override fun areContentsTheSame(a: Book, b: Book) = a == b
    }
}
