package com.anshpandey.pingai

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.view.View
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.android.material.button.MaterialButton
import java.util.concurrent.Executors

class ReplyActivity : AppCompatActivity() {

    private lateinit var incoming: String
    private lateinit var replyView: TextView
    private lateinit var progress: ProgressBar
    private var current = ""
    private val worker = Executors.newSingleThreadExecutor()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val text = readIncoming().trim()
        if (text.isEmpty()) {
            Toast.makeText(this, "No text received", Toast.LENGTH_SHORT).show()
            finish()
            return
        }
        incoming = text

        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(18), dp(20), dp(14))
        }
        box.addView(TextView(this).apply {
            this.text = "PING AI"
            textSize = 18f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(ContextCompat.getColor(context, R.color.green_dark))
        })
        box.addView(TextView(this).apply {
            this.text = "Message: " + incoming.take(300)
            textSize = 13f
            setTextColor(ContextCompat.getColor(context, R.color.text_soft))
            setPadding(0, dp(8), 0, dp(10))
        })
        progress = ProgressBar(this)
        box.addView(progress)
        replyView = TextView(this).apply {
            textSize = 15f
            setTextIsSelectable(true)
            setTextColor(ContextCompat.getColor(context, R.color.text_main))
        }
        box.addView(replyView)

        box.addView(action("Copy reply") { copy() })
        box.addView(action("Send via…") { share() })
        box.addView(action("Try again") { generate() })
        box.addView(TextView(this).apply {
            this.text = "Made By Ansh"
            textSize = 11f
            gravity = android.view.Gravity.CENTER
            setTextColor(Color.parseColor("#667781"))
            setPadding(0, dp(10), 0, 0)
        })
        setContentView(box)
        generate()
    }

    private fun readIncoming(): String = when (intent.action) {
        Intent.ACTION_PROCESS_TEXT -> intent.getCharSequenceExtra(Intent.EXTRA_PROCESS_TEXT)?.toString()
        Intent.ACTION_SEND -> intent.getStringExtra(Intent.EXTRA_TEXT)
        else -> intent.getStringExtra(EXTRA_TEXT)
    }.orEmpty()

    private fun generate() {
        current = ""
        replyView.text = ""
        progress.visibility = View.VISIBLE
        worker.execute {
            val result = try {
                ReplyEngine(applicationContext).helper(incoming)
            } catch (e: Exception) {
                null to (e.message ?: "Something went wrong")
            }
            runOnUiThread {
                progress.visibility = View.GONE
                if (result is String) {
                    current = result
                    replyView.text = result
                } else {
                    replyView.text = "Could not get a reply: " + (result as Pair<*, *>).second
                }
            }
        }
    }

    private fun copy() {
        if (current.isEmpty()) return
        val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText("PING AI reply", current))
        Toast.makeText(this, "Copied", Toast.LENGTH_SHORT).show()
    }

    private fun share() {
        if (current.isEmpty()) return
        val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, current)
        startActivity(Intent.createChooser(send, "Send reply via"))
    }

    private fun action(text: String, onClick: () -> Unit) = MaterialButton(this).apply {
        this.text = text
        isAllCaps = false
        setOnClickListener { onClick() }
        layoutParams = LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(8) }
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    companion object {
        const val EXTRA_TEXT = "text"
    }
}
