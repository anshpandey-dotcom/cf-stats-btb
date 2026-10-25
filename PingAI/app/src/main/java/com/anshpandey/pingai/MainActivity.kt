package com.anshpandey.pingai

import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.text.InputType
import android.text.SpannableString
import android.text.Spanned
import android.text.TextPaint
import android.text.method.LinkMovementMethod
import android.text.style.ClickableSpan
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity() {

    private lateinit var store: Store
    private lateinit var ui: UiKit
    private lateinit var pageHost: FrameLayout
    private lateinit var tabBar: LinearLayout
    private val worker = Executors.newSingleThreadExecutor()
    private val handler = Handler(Looper.getMainLooper())
    private var tab = 0
    private var askedAccess = false
    private var showSetup = false
    private var summaryText = ""
    private var leave: (() -> Unit)? = null
    private var liveText: TextView? = null
    private var usageText: TextView? = null
    private var usageBar: ProgressBar? = null

    private val tabs = if (BuildConfig.AUTO) listOf("Home", "AI", "Rules", "Summary") else listOf("Home", "AI")
    private val tabIcons = mapOf("Home" to "🏠", "AI" to "✨", "Rules" to "👥", "Summary" to "📝")

    private val apps = listOf(
        "com.whatsapp" to "WhatsApp",
        "com.whatsapp.w4b" to "WhatsApp Business",
        "org.telegram.messenger" to "Telegram",
        "com.facebook.orca" to "Messenger",
        "com.instagram.android" to "Instagram",
        "com.discord" to "Discord"
    )

    private val ticker = object : Runnable {
        override fun run() {
            refreshLive()
            refreshUsage()
            handler.postDelayed(this, 2000)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        store = Store(this)
        ui = UiKit(this)
        WindowCompat.setDecorFitsSystemWindows(window, false)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(ui.color(R.color.page_bg))
        }
        val header = header()
        pageHost = FrameLayout(this)
        tabBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setBackgroundColor(ui.color(R.color.card_bg))
        }
        root.addView(header)
        root.addView(pageHost, LinearLayout.LayoutParams(-1, 0, 1f))
        root.addView(footer())
        root.addView(tabBar)
        setContentView(root)

        ViewCompat.setOnApplyWindowInsetsListener(root) { _, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val ime = insets.getInsets(WindowInsetsCompat.Type.ime())
            header.setPadding(ui.dp(18) + bars.left, ui.dp(16) + bars.top, ui.dp(18) + bars.right, ui.dp(14))
            root.setPadding(0, 0, 0, maxOf(bars.bottom, ime.bottom))
            insets
        }

        buildTabBar()

        if (BuildConfig.AUTO && Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, "android.permission.POST_NOTIFICATIONS") != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(this, arrayOf("android.permission.POST_NOTIFICATIONS"), 1)
        }
    }

    override fun onResume() {
        super.onResume()
        showTab(tab)
        handler.removeCallbacks(ticker)
        handler.post(ticker)
        if (!BuildConfig.AUTO) return
        if (hasAccess() && Build.VERSION.SDK_INT >= 24) {
            runCatching {
                NotificationListenerService.requestRebind(ComponentName(this, PingListenerService::class.java))
            }
        } else if (!hasAccess() && !askedAccess) {
            askedAccess = true
            AlertDialog.Builder(this)
                .setTitle("Allow notification access")
                .setMessage("PING AI Auto reads incoming message notifications only to write and send replies for you. Nothing is read until you allow it.\n\nIf the switch is greyed out, open App info, tap the three dots and choose Allow restricted settings.")
                .setPositiveButton("Allow") { _, _ ->
                    startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                }
                .setNegativeButton("Not now", null)
                .show()
        }
    }

    override fun onPause() {
        handler.removeCallbacks(ticker)
        leave?.invoke()
        super.onPause()
    }

    private fun hasAccess(): Boolean =
        NotificationManagerCompat.getEnabledListenerPackages(this).contains(packageName)

    private fun setupComplete(): Boolean =
        store.userName.isNotBlank() && store.groqKey.isNotBlank() && (!BuildConfig.AUTO || (hasAccess() && store.enabled))

    private fun header(): View {
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(ui.color(R.color.green_dark))
        }
        box.addView(TextView(this).apply {
            text = "PING AI"
            textSize = 24f
            setTextColor(Color.WHITE)
            setTypeface(typeface, Typeface.BOLD)
        })
        box.addView(TextView(this).apply {
            text = if (BuildConfig.AUTO) "Smart auto-reply while you are away" else "Instant AI replies for your chats"
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
                ds.color = ui.color(R.color.link_blue)
                ds.isUnderlineText = true
                ds.isFakeBoldText = true
            }
        }, start, full.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        return TextView(this).apply {
            text = span
            textSize = 13f
            gravity = Gravity.CENTER
            movementMethod = LinkMovementMethod.getInstance()
            setTextColor(ui.color(R.color.text_soft))
            setBackgroundColor(ui.color(R.color.card_bg))
            setPadding(0, ui.dp(8), 0, ui.dp(4))
        }
    }

    private fun buildTabBar() {
        tabBar.removeAllViews()
        tabs.forEachIndexed { i, name ->
            tabBar.addView(TextView(this).apply {
                text = (tabIcons[name] ?: "") + "\n" + name
                textSize = 12f
                gravity = Gravity.CENTER
                setPadding(0, ui.dp(6), 0, ui.dp(8))
                setOnClickListener { showTab(i) }
            }, LinearLayout.LayoutParams(0, -2, 1f))
        }
    }

    private fun showTab(index: Int) {
        leave?.invoke()
        leave = null
        liveText = null
        usageText = null
        usageBar = null
        tab = index
        pageHost.removeAllViews()
        val page = when (tabs[index]) {
            "AI" -> aiPage()
            "Rules" -> rulesPage()
            "Summary" -> summaryPage()
            else -> homePage()
        }
        pageHost.addView(page)
        for (i in 0 until tabBar.childCount) {
            val v = tabBar.getChildAt(i) as TextView
            val on = i == index
            v.setTextColor(ui.color(if (on) R.color.green_mid else R.color.text_soft))
            v.setTypeface(null, if (on) Typeface.BOLD else Typeface.NORMAL)
        }
        refreshLive()
        refreshUsage()
    }

    private fun scroll(build: (LinearLayout) -> Unit): View {
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(ui.dp(12), ui.dp(12), ui.dp(12), ui.dp(12))
        }
        build(content)
        return ScrollView(this).apply { addView(content) }
    }

    private fun toast(text: String) = Toast.makeText(this, text, Toast.LENGTH_SHORT).show()

    private fun open(url: String) = startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))

    private fun homePage(): View = scroll { page ->
        if (BuildConfig.AUTO) {
            page.addView(autoCard())
            page.addView(liveCard())
            page.addView(usageCard())
            if (!setupComplete() || showSetup) {
                page.addView(setupCard())
            } else {
                page.addView(ui.outlined("Show setup steps") {
                    showSetup = true
                    showTab(tab)
                })
            }
            page.addView(awayCard())
        } else {
            page.addView(helperCard())
            page.addView(usageCard())
            if (store.userName.isBlank() || store.groqKey.isBlank()) page.addView(setupCard())
        }
    }

    private fun autoCard(): View = ui.card("Auto reply") { box ->
        fun label(on: Boolean) = if (on) "ON - replying for you" else "OFF"
        val sw = SwitchCompat(this).apply {
            textSize = 18f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(ui.color(R.color.text_main))
            isChecked = store.enabled
            text = label(store.enabled)
            setOnCheckedChangeListener { button, on ->
                store.enabled = on
                (button as SwitchCompat).text = label(on)
                if (on && !hasAccess()) toast("Grant notification access first")
            }
        }
        box.addView(sw)
        val ok = hasAccess()
        box.addView(TextView(this).apply {
            text = if (ok) "Notification access: granted" else "Notification access: not granted"
            textSize = 13f
            setPadding(0, ui.dp(6), 0, 0)
            setTextColor(if (ok) ui.color(R.color.green_mid) else 0xFFC62828.toInt())
        })
    }

    private fun liveCard(): View = ui.card("Live") { box ->
        val t = ui.body("")
        liveText = t
        box.addView(t)
        box.addView(ui.outlined("Reconnect listener") {
            if (Build.VERSION.SDK_INT >= 24) {
                runCatching {
                    NotificationListenerService.requestRebind(ComponentName(this, PingListenerService::class.java))
                }
            }
            toast("Reconnecting…")
        })
        box.addView(ui.hint("If it stays disconnected, open notification access, turn PING AI off and on again."))
        box.addView(ui.outlined("Open notification access") {
            startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
        })
    }

    private fun usageCard(): View = ui.card("Groq usage today") { box ->
        val t = ui.body("")
        val bar = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            progressTintList = ColorStateList.valueOf(ui.color(R.color.green_mid))
            layoutParams = LinearLayout.LayoutParams(-1, ui.dp(10))
        }
        usageText = t
        usageBar = bar
        box.addView(t)
        box.addView(bar)
        box.addView(ui.outlined("Open Groq usage page") { open("https://console.groq.com/dashboard/usage") })
        box.addView(ui.outlined("Open Groq limits") { open("https://console.groq.com/settings/limits") })
    }

    private fun refreshLive() {
        val view = liveText ?: return
        val now = System.currentTimeMillis()
        val lines = mutableListOf<String>()
        lines.add(if (Live.connected) "✅ Listener connected" else "⚠️ Listener not connected")
        if (Live.lastSeen.isNotEmpty()) lines.add("Last notification: " + Live.lastSeen)
        Live.waiting.values.forEach { (name, at) ->
            lines.add("⏳ $name: replying in ${maxOf(0L, (at - now) / 1000)}s")
        }
        store.events().takeLast(5).reversed().forEach { lines.add(eventLine(it)) }
        view.text = lines.joinToString("\n").ifEmpty { "Waiting for messages…" }
    }

    private fun refreshUsage() {
        val view = usageText ?: return
        val bar = usageBar ?: return
        val q = store.quota()
        val tokens = tokenText(store.tokensToday())
        if (q == null || q.limit <= 0) {
            view.text = "No request yet today.\nTokens used by PING AI today: $tokens"
            bar.max = 1
            bar.progress = 0
        } else {
            val used = (q.limit - q.remaining).coerceAtLeast(0)
            val time = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(q.at))
            view.text = "Requests: $used of ${q.limit} used (${q.remaining} left)\n" +
                "Tokens used by PING AI today: $tokens\nChecked at $time"
            bar.max = q.limit
            bar.progress = used
        }
    }

    private fun tokenText(n: Int): String = if (n >= 1000) String.format(Locale.US, "%.1fk", n / 1000.0) else n.toString()

    private fun eventLine(e: Event): String {
        val fmt = SimpleDateFormat("HH:mm", Locale.getDefault())
        val mark = when (e.kind) {
            "in" -> "⬅"
            "out" -> "➡"
            "sys" -> "ℹ"
            else -> "⏸"
        }
        return "${fmt.format(Date(e.time))}  $mark ${e.who}: ${e.text}"
    }

    private fun setupCard(): View = ui.card("Setup") { box ->
        fun line(done: Boolean, text: String) = box.addView(ui.body((if (done) "✓  " else "○  ") + text))
        line(store.userName.isNotBlank(), "Add your name in the AI tab")
        line(store.groqKey.isNotBlank(), "Add your free Groq key in the AI tab")
        if (BuildConfig.AUTO) {
            line(hasAccess(), "Allow notification access")
            line(store.enabled, "Turn on auto reply")
            if (!hasAccess()) {
                box.addView(ui.outlined("Open App info (Allow restricted settings)") {
                    startActivity(
                        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName"))
                    )
                })
                box.addView(ui.button("Grant notification access") {
                    startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                })
            }
            box.addView(ui.outlined("Allow background running (battery)") {
                runCatching { startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)) }
            })
            if (Build.VERSION.SDK_INT >= 34) {
                box.addView(ui.outlined("Allow full-screen call alerts") {
                    runCatching {
                        startActivity(
                            Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, Uri.parse("package:$packageName"))
                        )
                    }
                })
            }
        }
        if (store.userName.isBlank() || store.groqKey.isBlank()) box.addView(ui.button("Open AI tab") { showTab(1) })
        if (showSetup && setupComplete()) {
            box.addView(ui.outlined("Hide setup") {
                showSetup = false
                showTab(tab)
            })
        }
    }

    private fun awayCard(): View = ui.card("Away status") { box ->
        val fmt = SimpleDateFormat("dd MMM, HH:mm", Locale.getDefault())
        if (store.awayActive()) {
            box.addView(ui.body("Away until " + fmt.format(Date(store.awayUntil))))
        } else {
            box.addView(ui.hint("Tell PING AI where you are so replies can mention it."))
        }
        box.addView(ui.label("Where are you or why are you away (optional)"))
        val note = ui.edit(store.awayNote, "e.g. out for work")
        box.addView(note)
        box.addView(ui.label("Back around (optional)"))
        val back = ui.edit(store.awayBack, "e.g. 6 pm")
        box.addView(back)
        box.addView(ui.label("Instructions for this away period (optional)"))
        val extra = ui.edit(store.awayInstructions, "e.g. Say I will reply tonight. Do not share my location.", 3)
        box.addView(extra)
        box.addView(ui.label("Away for (hours)"))
        val hours = ui.edit("2", "2")
        hours.inputType = InputType.TYPE_CLASS_NUMBER
        box.addView(hours)
        box.addView(ui.button("Start away") {
            store.awayNote = note.text.toString()
            store.awayBack = back.text.toString()
            store.awayInstructions = extra.text.toString()
            val h = hours.text.toString().toIntOrNull() ?: 2
            store.awayUntil = System.currentTimeMillis() + h.coerceIn(1, 72) * 3_600_000L
            store.clearMemories()
            showTab(tab)
        })
        if (store.awayActive()) {
            box.addView(ui.outlined("End away") {
                store.awayUntil = 0L
                showTab(tab)
            })
        }
    }

    private fun helperCard(): View = ui.card("Reply Helper") { box ->
        box.addView(ui.hint("Select a message in any chat app and choose PING AI Reply, share text to PING AI Reply, or paste a message here."))
        val input = ui.edit("", "Paste the message you received", 3)
        box.addView(input)
        box.addView(ui.button("Generate reply") {
            val text = input.text.toString().trim()
            if (text.isEmpty()) {
                toast("Paste a message first")
            } else {
                startActivity(Intent(this, ReplyActivity::class.java).putExtra(ReplyActivity.EXTRA_TEXT, text))
            }
        })
    }

    private fun aiPage(): View = scroll { page ->
        page.addView(ui.card("AI Setup") { box ->
            box.addView(ui.label("Your name"))
            val name = ui.edit(store.userName, "Your name")
            box.addView(name)

            box.addView(ui.label("Groq API key (free)"))
            val key = ui.edit(store.groqKey, "Paste your key")
            key.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            box.addView(key)
            box.addView(ui.hint("Tap the button, sign up with your email, tap Create API Key, copy the key (it is shown only once) and paste it above."))
            box.addView(ui.button("Get free key") { open("https://console.groq.com/keys") })

            box.addView(ui.label("Model"))
            val model = ui.edit(store.groqModel, Store.DEFAULT_GROQ)
            box.addView(model)
            box.addView(ui.outlined("Choose from models on your Groq account") {
                store.groqKey = key.text.toString()
                toast("Loading models…")
                worker.execute {
                    val result = try {
                        ReplyEngine(applicationContext).availableModels()
                    } catch (e: Exception) {
                        null to (e.message ?: "failed")
                    }
                    runOnUiThread {
                        if (result is List<*>) {
                            val names = result.map { it.toString() }
                            AlertDialog.Builder(this)
                                .setTitle("Pick a model")
                                .setItems(names.toTypedArray()) { _, i -> model.setText(names[i]) }
                                .show()
                        } else {
                            val msg = (result as Pair<*, *>).second.toString()
                            AlertDialog.Builder(this).setMessage("Could not load models: $msg")
                                .setPositiveButton("OK", null).show()
                        }
                    }
                }
            })
            var use70b = store.use70b
            box.addView(ui.switchRow("Use the smarter 70B model when possible (lower free limit)", use70b) { use70b = it })

            box.addView(ui.label("Reply length"))
            box.addView(ui.radios(
                listOf("Minimal (one short line)", "Normal (one or two sentences)", "Detailed (two to four sentences)"),
                store.replyLength
            ) { store.replyLength = it })

            box.addView(ui.label("Custom instructions for the AI (optional)"))
            val persona = ui.edit(store.personality, "e.g. Be friendly and keep replies short.", 4)
            box.addView(persona)

            box.addView(ui.label("Your own message samples (optional)"))
            box.addView(ui.hint("Paste 5 to 10 messages you really wrote, one per line. The AI copies your style."))
            val samples = ui.edit(store.styleSamples, "e.g. sure, see you tomorrow\nok done, will call later", 5)
            box.addView(samples)

            fun save() {
                store.userName = name.text.toString()
                store.groqKey = key.text.toString()
                store.groqModel = model.text.toString()
                store.use70b = use70b
                store.personality = persona.text.toString()
                store.styleSamples = samples.text.toString()
            }
            leave = { save() }

            box.addView(ui.button("Save") {
                save()
                toast("Saved")
            })
            box.addView(ui.outlined("Test AI") {
                save()
                toast("Testing…")
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
        })
        if (!BuildConfig.AUTO) page.addView(repliesCard())
    }

    private fun rulesPage(): View = scroll { page ->
        page.addView(peopleCard())
        page.addView(unknownCard())
        page.addView(repliesCard())
        page.addView(behaviourCard())
    }

    private fun peopleCard(): View = ui.card("People") { box ->
        box.addView(ui.hint("Add just a name (part of it is enough) or a phone number. Add a tone note to tell the AI how to talk to them."))
        box.addView(ui.radios(
            listOf("Everyone gets a reply", "Only people in the list", "Everyone except the list"),
            store.contactMode
        ) { store.contactMode = it })
        val people = store.people()
        people.forEachIndexed { i, p ->
            box.addView(ui.row(p.name + if (p.note.isBlank()) "" else "\n" + p.note) {
                people.removeAt(i)
                store.savePeople(people)
                showTab(tab)
            })
        }
        box.addView(ui.button("Add person") { addPersonDialog() })
    }

    private fun unknownCard(): View = ui.card("Unknown numbers") { box ->
        box.addView(ui.hint("People not saved in your contacts (shown as a phone number)."))
        box.addView(ui.radios(
            listOf("AI asks who they are and what they need", "Send a fixed message", "Don't reply"),
            store.unknownMode
        ) { store.unknownMode = it })
        box.addView(ui.label("Fixed message ({name} = your name)"))
        val text = ui.edit(store.unknownText, Store.DEFAULT_UNKNOWN, 3)
        box.addView(text)
        box.addView(ui.outlined("Save fixed message") {
            store.unknownText = text.text.toString()
            toast("Saved")
        })
    }

    private fun repliesCard(): View = ui.card("Custom replies") { box ->
        box.addView(ui.hint("If a message contains any of the words, this exact reply is sent instead of AI. Separate words with commas."))
        val replies = store.customReplies()
        replies.forEachIndexed { i, item ->
            box.addView(ui.row("${item.keywords}\n→ ${item.reply}") {
                replies.removeAt(i)
                store.saveCustomReplies(replies)
                showTab(tab)
            })
        }
        box.addView(ui.button("Add custom reply") { addReplyDialog() })
    }

    private fun behaviourCard(): View = ui.card("Behaviour") { box ->
        box.addView(ui.label("Apps to reply in"))
        apps.forEach { (pkg, name) ->
            box.addView(ui.switchRow(name, store.appEnabled(pkg)) { store.setAppEnabled(pkg, it) })
        }
        box.addView(ui.switchRow("Skip group chats", store.skipGroups) { store.skipGroups = it })
        box.addView(ui.label("Wait after the last message before replying (seconds)"))
        val wait = ui.edit(store.waitSeconds.toString(), "10")
        wait.inputType = InputType.TYPE_CLASS_NUMBER
        box.addView(wait)
        box.addView(ui.label("Maximum replies per person per day"))
        val limit = ui.edit(store.maxPerDay.toString(), "20")
        limit.inputType = InputType.TYPE_CLASS_NUMBER
        box.addView(limit)
        box.addView(ui.label("Stop replying after this many messages from one person in 10 minutes"))
        val spam = ui.edit(store.spamLimit.toString(), "30")
        spam.inputType = InputType.TYPE_CLASS_NUMBER
        box.addView(spam)
        box.addView(ui.switchRow("Let the AI stay silent on pointless messages", store.smartSilence) {
            store.smartSilence = it
        })
        box.addView(ui.label("Call request phrases (rings your phone like a call). Leave empty to turn off."))
        val calls = ui.edit(store.callTriggers, "e.g. call me, urgent call", 3)
        box.addView(calls)
        box.addView(ui.label("Never reply if the message contains these words. Leave empty to turn off."))
        val block = ui.edit(store.blockWords, "Add your own words, separated by commas", 3)
        box.addView(block)

        fun save() {
            store.waitSeconds = wait.text.toString().toIntOrNull() ?: 10
            store.maxPerDay = limit.text.toString().toIntOrNull() ?: 20
            store.spamLimit = spam.text.toString().toIntOrNull() ?: 30
            store.callTriggers = calls.text.toString()
            store.blockWords = block.text.toString()
        }
        leave = { save() }
        box.addView(ui.button("Save") {
            save()
            toast("Saved")
        })
    }

    private fun summaryPage(): View = scroll { page ->
        page.addView(ui.card("Summary") { box ->
            box.addView(ui.hint("Who messaged, what looked important, questions waiting, photos or voice messages, and who did not get a reply."))
            val out = ui.body(summaryText.ifEmpty { "No summary yet." })
            out.setTextIsSelectable(true)
            fun run(hinglish: Boolean) {
                out.text = "Working…"
                worker.execute {
                    val result = try {
                        ReplyEngine(applicationContext).summarize(hinglish)
                    } catch (e: Exception) {
                        "Could not summarize: " + e.message
                    }
                    runOnUiThread {
                        summaryText = result
                        out.text = result
                    }
                }
            }
            box.addView(ui.button("Summarize (English)") { run(false) })
            box.addView(ui.outlined("Hinglish version") { run(true) })
            box.addView(out)
        })
        page.addView(ui.card("Recent activity") { box ->
            val events = store.events().takeLast(40).reversed()
            if (events.isEmpty()) {
                box.addView(ui.hint("No activity yet."))
            } else {
                events.forEach { box.addView(ui.body(eventLine(it))) }
            }
            box.addView(ui.outlined("Clear activity") {
                store.clearEvents()
                summaryText = ""
                showTab(tab)
            })
        })
    }

    private fun addPersonDialog() {
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(ui.dp(20), ui.dp(8), ui.dp(20), 0)
        }
        val name = ui.edit("", "Name or phone number")
        val note = ui.edit("", "e.g. close friend, casual", 2)
        box.addView(name)
        box.addView(ui.label("Tone note (optional)"))
        box.addView(note)
        AlertDialog.Builder(this)
            .setTitle("Add person")
            .setView(box)
            .setPositiveButton("Add") { _, _ ->
                val n = name.text.toString().trim()
                if (n.isNotEmpty()) {
                    val list = store.people()
                    list.add(Person(n, note.text.toString().trim()))
                    store.savePeople(list)
                    showTab(tab)
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun addReplyDialog() {
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(ui.dp(20), ui.dp(8), ui.dp(20), 0)
        }
        val words = ui.edit("", "Words (hi, hello, how are you)")
        val reply = ui.edit("", "Reply to send", 2)
        box.addView(words)
        box.addView(ui.label("Reply"))
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
                    showTab(tab)
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    companion object {
        private const val INSTAGRAM = "https://www.instagram.com/iam.anshpandey?psln=ZXM4azFzeGMwazI1"
    }
}
