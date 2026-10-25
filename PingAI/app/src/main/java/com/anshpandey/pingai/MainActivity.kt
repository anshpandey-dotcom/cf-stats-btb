package com.anshpandey.pingai

import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
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
import android.view.Gravity
import android.view.View
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
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
    private var tab = 0
    private var askedAccess = false
    private var showSetup = false
    private var summaryText = ""
    private var leave: (() -> Unit)? = null

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
        leave?.invoke()
        super.onPause()
    }

    private fun hasAccess(): Boolean =
        NotificationManagerCompat.getEnabledListenerPackages(this).contains(packageName)

    private fun setupComplete(): Boolean =
        store.userName.isNotBlank() && (!BuildConfig.AUTO || (hasAccess() && store.enabled))

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

    private fun homePage(): View = scroll { page ->
        if (BuildConfig.AUTO) {
            page.addView(autoCard())
            if (!setupComplete() || showSetup) {
                page.addView(setupCard())
            } else {
                page.addView(ui.outlined("Show setup steps") {
                    showSetup = true
                    showTab(tab)
                })
            }
            page.addView(usageCard())
            page.addView(awayCard())
        } else {
            page.addView(helperCard())
            if (store.userName.isBlank()) page.addView(setupCard())
        }
        page.addView(ui.card("How it works") { box -> box.addView(ui.hint(howText())) })
    }

    private fun howText(): String = if (BuildConfig.AUTO) {
        "PING AI waits a few seconds until the person stops typing, then sends one short reply in their language. " +
            "It stays quiet for thanks, ok, stickers and bye, and never replies to its own messages. " +
            "Open the Summary tab any time to see who messaged, what looked important and who got no reply."
    } else {
        "Select a message in any chat app and choose PING AI Reply, share text to PING AI Reply, or paste a message here. " +
            "You get a reply written in your style that you can copy or send."
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

    private fun usageCard(): View = ui.card("AI usage today") { box ->
        val exact = store.quotaText(store.provider)
        box.addView(ui.body(if (exact.isEmpty()) "Exact numbers appear after the first reply or Test AI." else exact))
        box.addView(ui.hint(limitNote(store.provider)))
    }

    private fun limitNote(p: Int) = when (p) {
        Store.PROVIDER_GROQ -> "Groq free plan: the small 8B model allows roughly 14,000 requests a day, the 70B model about 1,000. Each reply and each summary uses one request. Check console.groq.com/docs/rate-limits for current numbers."
        Store.PROVIDER_OPENROUTER -> "OpenRouter free models: about 50 requests a day, or about 1,000 after adding 10 dollars of credit. Check openrouter.ai/docs for current numbers."
        else -> "Google AI Studio free tier limits depend on the model. See the Rate limits page in AI Studio for your exact numbers."
    }

    private fun setupCard(): View = ui.card("Setup") { box ->
        fun line(done: Boolean, text: String) = box.addView(ui.body((if (done) "✓  " else "○  ") + text))
        line(store.userName.isNotBlank(), "Add your name in the AI tab")
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
        }
        if (store.userName.isBlank()) box.addView(ui.button("Open AI tab") { showTab(1) })
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
        val note = ui.edit(store.awayNote, "e.g. going out for work")
        box.addView(note)
        box.addView(ui.label("Back around (optional)"))
        val back = ui.edit(store.awayBack, "e.g. 6 pm")
        box.addView(back)
        box.addView(ui.label("Away for (hours)"))
        val hours = ui.edit("2", "2")
        hours.inputType = InputType.TYPE_CLASS_NUMBER
        box.addView(hours)
        box.addView(ui.button("Start away") {
            store.awayNote = note.text.toString()
            store.awayBack = back.text.toString()
            val h = hours.text.toString().toIntOrNull() ?: 2
            store.awayUntil = System.currentTimeMillis() + h.coerceIn(1, 72) * 3_600_000L
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
        box.addView(ui.hint("Get an AI reply for any message:"))
        box.addView(ui.hint("1) In any chat app, select the message text, open the menu and choose PING AI Reply.\n2) Share text to PING AI Reply.\n3) Paste a message below."))
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

            box.addView(ui.label("AI provider"))
            var provider = store.provider
            var use70b = store.use70b
            var keyField: EditText? = null
            var modelField: EditText? = null
            val holder = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }

            fun render() {
                holder.removeAllViews()
                holder.addView(ui.label("API key (free). Leave empty to use the built-in free AI."))
                val key = ui.edit(keyOf(provider), "Paste your key")
                key.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
                holder.addView(key)
                holder.addView(ui.hint(stepsOf(provider)))
                holder.addView(ui.button("Get free key") {
                    startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(urlOf(provider))))
                })
                holder.addView(ui.label("Model"))
                val model = ui.edit(modelOf(provider), defaultModelOf(provider))
                if (provider == Store.PROVIDER_GEMINI) {
                    holder.addView(ui.radios(
                        listOf("Gemini Flash (fast)", "Gemma (Google open-source)"),
                        if (model.text.toString().startsWith("gemma")) 1 else 0
                    ) { model.setText(if (it == 1) "gemma-3-27b-it" else Store.DEFAULT_MODEL) })
                }
                holder.addView(model)
                if (provider == Store.PROVIDER_GROQ) {
                    holder.addView(ui.switchRow("Use the smarter 70B model (lower free limit, falls back to 8B)", use70b) {
                        use70b = it
                    })
                }
                keyField = key
                modelField = model
            }

            box.addView(ui.radios(
                listOf("Groq (fastest, recommended)", "OpenRouter (many free models)", "Google Gemini / Gemma"),
                provider
            ) {
                provider = it
                render()
            })
            render()
            box.addView(holder)

            box.addView(ui.label("Reply length"))
            box.addView(ui.radios(
                listOf("Minimal (one short line)", "Normal (one or two sentences)", "Detailed (two to four sentences)"),
                store.replyLength
            ) { store.replyLength = it })

            box.addView(ui.label("Custom instructions for the AI (optional)"))
            val persona = ui.edit(store.personality, "e.g. Be friendly and keep replies short. Never share my address.", 4)
            box.addView(persona)

            box.addView(ui.label("Your own message samples (optional)"))
            box.addView(ui.hint("Paste 5 to 10 messages you really wrote, one per line. The AI copies your style. Leave empty if you prefer."))
            val samples = ui.edit(store.styleSamples, "bhai kal milte hain\nthik hai done\nabhi busy hu, baad me baat karta hu", 5)
            box.addView(samples)

            fun save() {
                store.userName = name.text.toString()
                store.provider = provider
                store.use70b = use70b
                val key = keyField?.text.toString()
                val model = modelField?.text.toString()
                when (provider) {
                    Store.PROVIDER_GROQ -> {
                        store.groqKey = key
                        store.groqModel = model
                    }
                    Store.PROVIDER_OPENROUTER -> {
                        store.openRouterKey = key
                        store.openRouterModel = model
                    }
                    else -> {
                        store.apiKey = key
                        store.model = model
                    }
                }
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
        box.addView(ui.hint("Choose who gets a reply. For each person you can note how to talk to them."))
        box.addView(ui.radios(
            listOf("Everyone", "Only people in the list", "Everyone except the list"),
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
        box.addView(ui.label("Urgent words (plays a tone and alerts you)"))
        val urgent = ui.edit(store.urgentWords, Store.DEFAULT_URGENT, 3)
        box.addView(urgent)

        fun save() {
            store.waitSeconds = wait.text.toString().toIntOrNull() ?: 10
            store.maxPerDay = limit.text.toString().toIntOrNull() ?: 20
            store.urgentWords = urgent.text.toString()
        }
        leave = { save() }
        box.addView(ui.button("Save") {
            save()
            toast("Saved")
        })
    }

    private fun summaryPage(): View = scroll { page ->
        page.addView(ui.card("Summary") { box ->
            box.addView(ui.hint("See who messaged, what looked important, questions waiting for you and who did not get a reply."))
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
            val fmt = SimpleDateFormat("dd MMM HH:mm", Locale.getDefault())
            val events = store.events().takeLast(40).reversed()
            if (events.isEmpty()) {
                box.addView(ui.hint("No activity yet."))
            } else {
                events.forEach {
                    val mark = when (it.kind) {
                        "in" -> "⬅"
                        "out" -> "➡"
                        else -> "⏸"
                    }
                    box.addView(ui.body("${fmt.format(Date(it.time))}  $mark ${it.who}: ${it.text}"))
                }
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
        val note = ui.edit("", "How to talk to them (e.g. close friend, casual)", 2)
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

    private fun keyOf(p: Int) = when (p) {
        Store.PROVIDER_GROQ -> store.groqKey
        Store.PROVIDER_OPENROUTER -> store.openRouterKey
        else -> store.apiKey
    }

    private fun modelOf(p: Int) = when (p) {
        Store.PROVIDER_GROQ -> store.groqModel
        Store.PROVIDER_OPENROUTER -> store.openRouterModel
        else -> store.model
    }

    private fun defaultModelOf(p: Int) = when (p) {
        Store.PROVIDER_GROQ -> Store.DEFAULT_GROQ
        Store.PROVIDER_OPENROUTER -> Store.DEFAULT_OPENROUTER
        else -> Store.DEFAULT_MODEL
    }

    private fun urlOf(p: Int) = when (p) {
        Store.PROVIDER_GROQ -> "https://console.groq.com/keys"
        Store.PROVIDER_OPENROUTER -> "https://openrouter.ai/keys"
        else -> "https://aistudio.google.com/apikey"
    }

    private fun stepsOf(p: Int) = when (p) {
        Store.PROVIDER_GROQ -> "Tap the button, sign up with your email, tap Create API Key, copy the key (it is shown only once) and paste it above."
        Store.PROVIDER_OPENROUTER -> "Tap the button, sign up, create a key, copy it and paste it above. Pick a model whose name ends with :free."
        else -> "Tap the button, sign in with Google, tap Create API key, copy it and paste it above."
    }

    companion object {
        private const val INSTAGRAM = "https://www.instagram.com/iam.anshpandey?psln=ZXM4azFzeGMwazI1"
    }
}
