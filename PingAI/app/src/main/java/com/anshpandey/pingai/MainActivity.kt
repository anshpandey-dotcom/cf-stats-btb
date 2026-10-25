package com.anshpandey.pingai

import android.content.ComponentName
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.text.InputType
import android.text.SpannableString
import android.text.Spanned
import android.text.TextPaint
import android.text.method.LinkMovementMethod
import android.text.style.ClickableSpan
import android.text.style.ForegroundColorSpan
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity() {

    private lateinit var store: Store
    private lateinit var statusText: TextView
    private lateinit var repliesBox: LinearLayout
    private lateinit var contactsBox: LinearLayout
    private lateinit var logText: TextView
    private val worker = Executors.newSingleThreadExecutor()

    private val apps = listOf(
        "com.whatsapp" to "WhatsApp",
        "com.whatsapp.w4b" to "WhatsApp Business",
        "org.telegram.messenger" to "Telegram",
        "com.facebook.orca" to "Messenger"
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        store = Store(this)

        WindowCompat.setDecorFitsSystemWindows(window, false)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.WHITE)
        }
        val header = header()
        root.addView(header)

        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(12), dp(12), dp(12))
        }
        content.addView(if (BuildConfig.AUTO) statusCard() else helperCard())
        content.addView(aiCard())
        content.addView(repliesCard())
        if (BuildConfig.AUTO) {
            content.addView(filterCard())
            content.addView(unknownCard())
            content.addView(behaviourCard())
            content.addView(logCard())
        }

        val scroll = ScrollView(this).apply { addView(content) }
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        root.addView(footer())
        setContentView(root)

        ViewCompat.setOnApplyWindowInsetsListener(root) { _, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val ime = insets.getInsets(WindowInsetsCompat.Type.ime())
            header.setPadding(dp(18) + bars.left, dp(18) + bars.top, dp(18) + bars.right, dp(14))
            root.setPadding(0, 0, 0, maxOf(bars.bottom, ime.bottom))
            insets
        }
    }

    override fun onResume() {
        super.onResume()
        refreshLists()
        if (!BuildConfig.AUTO) return
        refreshStatus()
        logText.text = store.logLines().joinToString("\n").ifEmpty { "No activity yet." }
        if (hasAccess() && Build.VERSION.SDK_INT >= 24) {
            runCatching {
                NotificationListenerService.requestRebind(ComponentName(this, PingListenerService::class.java))
            }
        }
    }

    private fun hasAccess(): Boolean =
        NotificationManagerCompat.getEnabledListenerPackages(this).contains(packageName)

    private fun refreshStatus() {
        val on = hasAccess()
        statusText.text = if (on) "Notification access: granted" else "Notification access: NOT granted"
        statusText.setTextColor(if (on) ContextCompat.getColor(this, R.color.green_mid) else Color.parseColor("#C62828"))
    }

    private fun header(): View {
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(ContextCompat.getColor(context, R.color.green_dark))
            setPadding(dp(18), dp(18), dp(18), dp(14))
        }
        box.addView(TextView(this).apply {
            text = "PING AI"
            textSize = 24f
            setTextColor(Color.WHITE)
            setTypeface(typeface, Typeface.BOLD)
        })
        box.addView(TextView(this).apply {
            text = "Your smart auto-reply assistant"
            textSize = 13f
            setTextColor(Color.parseColor("#CFEFE9"))
        })
        return box
    }

    private fun footer(): View {
        val full = "Made By Ansh"
        val span = SpannableString(full)
        val start = full.indexOf("Ansh")
        span.setSpan(object : ClickableSpan() {
            override fun onClick(widget: View) {
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(INSTAGRAM)))
            }

            override fun updateDrawState(ds: TextPaint) {
                ds.color = ContextCompat.getColor(this@MainActivity, R.color.link_blue)
                ds.isUnderlineText = true
                ds.isFakeBoldText = true
            }
        }, start, full.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        span.setSpan(
            ForegroundColorSpan(ContextCompat.getColor(this, R.color.link_blue)),
            start, full.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
        )
        return TextView(this).apply {
            text = span
            textSize = 14f
            gravity = Gravity.CENTER
            movementMethod = LinkMovementMethod.getInstance()
            setTextColor(ContextCompat.getColor(context, R.color.text_soft))
            setBackgroundColor(Color.WHITE)
            setPadding(0, dp(10), 0, dp(10))
        }
    }

    private fun helperCard(): View = card("Reply Helper") { box ->
        box.addView(hint("Get an AI reply for any message in three ways:"))
        box.addView(hint("1) In any chat app, select the message text, open the menu and choose PING AI Reply.\n2) Share text to PING AI Reply.\n3) Paste a message below."))
        val input = edit("", "Paste the message you received")
        input.minLines = 3
        box.addView(input)
        box.addView(button("Generate reply") {
            val text = input.text.toString().trim()
            if (text.isEmpty()) {
                Toast.makeText(this, "Paste a message first", Toast.LENGTH_SHORT).show()
            } else {
                startActivity(Intent(this, ReplyActivity::class.java).putExtra(ReplyActivity.EXTRA_TEXT, text))
            }
        })
    }

    private fun statusCard(): View = card("Auto Reply") { box ->
        statusText = TextView(this).apply { textSize = 13f }
        box.addView(statusText)
        box.addView(hint("Setup: 1) Open App info, tap the three dots at the top right and choose Allow restricted settings. 2) Grant notification access. 3) Turn on auto reply."))
        box.addView(button("Open App info") {
            startActivity(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName"))
            )
        })
        box.addView(button("Grant notification access") {
            startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
        })
        box.addView(button("Allow background running (battery)") {
            runCatching { startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)) }
        })
        box.addView(SwitchCompat(this).apply {
            text = "Enable auto reply"
            isChecked = store.enabled
            setOnCheckedChangeListener { _, on ->
                store.enabled = on
                if (on && !hasAccess()) {
                    Toast.makeText(context, "Grant notification access first", Toast.LENGTH_LONG).show()
                }
            }
        })
        box.addView(label("Apps to reply in"))
        apps.forEach { (pkg, name) ->
            box.addView(SwitchCompat(this).apply {
                text = name
                isChecked = store.appEnabled(pkg)
                setOnCheckedChangeListener { _, on -> store.setAppEnabled(pkg, on) }
            })
        }
    }

    private fun aiCard(): View = card("AI Setup") { box ->
        box.addView(label("Your name (used in the assistant tag)"))
        val name = edit(store.userName, "Your name")
        box.addView(name)

        box.addView(label("Gemini API key (free)"))
        val key = edit(store.apiKey, "Paste key from AI Studio")
        key.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        box.addView(key)
        box.addView(button("Get free key (AI Studio)") {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://aistudio.google.com/apikey")))
        })

        box.addView(label("Gemini model"))
        val model = edit(store.model, Store.DEFAULT_MODEL)
        box.addView(model)

        box.addView(label("Custom instructions for the AI (optional)"))
        val persona = edit(store.personality, "e.g. Be friendly and keep replies short. Never share my address.")
        persona.minLines = 4
        box.addView(persona)

        box.addView(label("Assistant tag shown before every reply ({name} = your name)"))
        val tag = edit(store.tagTemplate, Store.DEFAULT_TAG)
        tag.minLines = 2
        box.addView(tag)

        box.addView(label("Message when AI is unavailable"))
        val fallback = edit(store.fallbackText, Store.DEFAULT_FALLBACK)
        fallback.minLines = 2
        box.addView(fallback)

        fun save() {
            store.userName = name.text.toString()
            store.apiKey = key.text.toString()
            store.model = model.text.toString()
            store.personality = persona.text.toString()
            store.tagTemplate = tag.text.toString()
            store.fallbackText = fallback.text.toString()
        }

        box.addView(button("Save") {
            save()
            Toast.makeText(this, "Saved", Toast.LENGTH_SHORT).show()
        })
        box.addView(button("Test AI") {
            save()
            if (store.apiKey.isBlank()) {
                Toast.makeText(this, "Paste your API key first", Toast.LENGTH_SHORT).show()
                return@button
            }
            Toast.makeText(this, "Testing…", Toast.LENGTH_SHORT).show()
            worker.execute {
                val result = try {
                    "AI says: " + ReplyEngine(applicationContext).test()
                } catch (e: Exception) {
                    "Failed: " + e.message
                }
                runOnUiThread {
                    AlertDialog.Builder(this).setMessage(result).setPositiveButton("OK", null).show()
                }
            }
        })
    }

    private fun repliesCard(): View = card("Custom Replies") { box ->
        box.addView(hint("If a message contains any of the words, this exact reply is sent instead of AI. Separate words with commas."))
        repliesBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        box.addView(repliesBox)
        box.addView(button("Add custom reply") { addReplyDialog() })
        box.addView(SwitchCompat(this).apply {
            text = "Add assistant tag to custom replies"
            isChecked = store.tagCustom
            setOnCheckedChangeListener { _, on -> store.tagCustom = on }
        })
    }

    private fun filterCard(): View = card("Who gets a reply") { box ->
        box.addView(hint("Add saved names or phone numbers."))
        box.addView(radios(
            listOf("Everyone", "Only people in the list", "Everyone except the list"),
            store.contactMode
        ) { store.contactMode = it })
        contactsBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        box.addView(contactsBox)
        box.addView(button("Add name or number") { addContactDialog() })
    }

    private fun unknownCard(): View = card("Unknown numbers") { box ->
        box.addView(hint("People not saved in your contacts (shown as a phone number)."))
        box.addView(radios(
            listOf("AI introduces itself and asks who they are", "Send a fixed message", "Don't reply"),
            store.unknownMode
        ) { store.unknownMode = it })
        box.addView(label("Fixed message"))
        val text = edit(store.unknownText, Store.DEFAULT_UNKNOWN)
        text.minLines = 2
        box.addView(text)
        box.addView(button("Save fixed message") {
            store.unknownText = text.text.toString()
            Toast.makeText(this, "Saved", Toast.LENGTH_SHORT).show()
        })
    }

    private fun behaviourCard(): View = card("Behaviour") { box ->
        box.addView(SwitchCompat(this).apply {
            text = "Skip group chats"
            isChecked = store.skipGroups
            setOnCheckedChangeListener { _, on -> store.skipGroups = on }
        })
        box.addView(label("Reply delay (seconds)"))
        val delay = edit(store.delaySeconds.toString(), "3")
        delay.inputType = InputType.TYPE_CLASS_NUMBER
        box.addView(delay)
        box.addView(button("Save delay") {
            store.delaySeconds = delay.text.toString().toIntOrNull() ?: 3
            Toast.makeText(this, "Saved", Toast.LENGTH_SHORT).show()
        })
    }

    private fun logCard(): View = card("Recent activity") { box ->
        logText = TextView(this).apply { textSize = 12f }
        box.addView(logText)
        box.addView(button("Clear") {
            store.clearLog()
            logText.text = "No activity yet."
        })
    }

    private fun refreshLists() {
        repliesBox.removeAllViews()
        val replies = store.customReplies()
        replies.forEachIndexed { i, item ->
            repliesBox.addView(row("${item.keywords}\n→ ${item.reply}") {
                replies.removeAt(i)
                store.saveCustomReplies(replies)
                refreshLists()
            })
        }
        if (!BuildConfig.AUTO) return
        contactsBox.removeAllViews()
        val contacts = store.contacts()
        contacts.forEachIndexed { i, item ->
            contactsBox.addView(row(item) {
                contacts.removeAt(i)
                store.saveContacts(contacts)
                refreshLists()
            })
        }
    }

    private fun addReplyDialog() {
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(8), dp(20), 0)
        }
        val words = edit("", "Words (hi, hello, how are you)")
        val reply = edit("", "Reply to send")
        box.addView(words)
        box.addView(reply)
        AlertDialog.Builder(this)
            .setTitle("New custom reply")
            .setView(box)
            .setPositiveButton("Add") { _, _ ->
                val k = words.text.toString().trim()
                val r = reply.text.toString().trim()
                if (k.isNotEmpty() && r.isNotEmpty()) {
                    val list = store.customReplies()
                    list.add(CustomReply(k, r))
                    store.saveCustomReplies(list)
                    refreshLists()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun addContactDialog() {
        val input = edit("", "Name or phone number")
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(8), dp(20), 0)
            addView(input)
        }
        AlertDialog.Builder(this)
            .setTitle("Add to list")
            .setView(box)
            .setPositiveButton("Add") { _, _ ->
                val v = input.text.toString().trim()
                if (v.isNotEmpty()) {
                    val list = store.contacts()
                    list.add(v)
                    store.saveContacts(list)
                    refreshLists()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun card(title: String, build: (LinearLayout) -> Unit): View {
        val card = MaterialCardView(this).apply {
            radius = dp(12).toFloat()
            cardElevation = dp(2).toFloat()
            setCardBackgroundColor(Color.WHITE)
            layoutParams = LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(12) }
        }
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(14), dp(16), dp(14))
        }
        box.addView(TextView(this).apply {
            text = title
            textSize = 17f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(ContextCompat.getColor(context, R.color.green_dark))
        })
        build(box)
        card.addView(box)
        return card
    }

    private fun label(text: String) = TextView(this).apply {
        this.text = text
        textSize = 13f
        setTextColor(ContextCompat.getColor(context, R.color.text_soft))
        setPadding(0, dp(10), 0, 0)
    }

    private fun hint(text: String) = TextView(this).apply {
        this.text = text
        textSize = 12f
        setTextColor(ContextCompat.getColor(context, R.color.text_soft))
        setPadding(0, dp(4), 0, dp(4))
    }

    private fun edit(value: String, hintText: String) = EditText(this).apply {
        setText(value)
        hint = hintText
        textSize = 14f
        setTextColor(ContextCompat.getColor(context, R.color.text_main))
        layoutParams = LinearLayout.LayoutParams(-1, -2)
    }

    private fun button(text: String, onClick: () -> Unit) = MaterialButton(this).apply {
        this.text = text
        isAllCaps = false
        setOnClickListener { onClick() }
        layoutParams = LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(8) }
    }

    private fun row(text: String, onDelete: () -> Unit): View {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(6), 0, dp(6))
        }
        row.addView(TextView(this).apply {
            this.text = text
            textSize = 13f
            setTextColor(ContextCompat.getColor(context, R.color.text_main))
        }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        row.addView(TextView(this).apply {
            this.text = "✕"
            textSize = 18f
            setPadding(dp(12), dp(4), dp(4), dp(4))
            setTextColor(Color.parseColor("#C62828"))
            setOnClickListener { onDelete() }
        })
        return row
    }

    private fun radios(options: List<String>, selected: Int, onSelect: (Int) -> Unit): View {
        val group = RadioGroup(this)
        options.forEachIndexed { i, text ->
            group.addView(RadioButton(this).apply {
                id = View.generateViewId()
                this.text = text
                textSize = 14f
                isChecked = i == selected
                setOnClickListener { onSelect(i) }
            })
        }
        return group
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    companion object {
        private const val INSTAGRAM = "https://www.instagram.com/iam.anshpandey?psln=ZXM4azFzeGMwazI1"
    }
}
