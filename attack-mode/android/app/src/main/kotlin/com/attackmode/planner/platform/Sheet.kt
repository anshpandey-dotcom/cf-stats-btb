package com.attackmode.planner.platform

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import com.attackmode.planner.data.AppState
import com.attackmode.planner.data.Files
import com.attackmode.planner.data.JOURNAL_PROMPTS
import com.attackmode.planner.data.JournalEntry
import com.attackmode.planner.data.SavedSheet
import com.attackmode.planner.data.Store
import com.attackmode.planner.data.Task
import com.attackmode.planner.logic.Dates
import com.attackmode.planner.logic.Progress
import com.attackmode.planner.logic.TaskStats
import java.io.ByteArrayOutputStream
import java.time.Instant

object Sheet {
    const val FOLDER = "Journal"
    private const val WIDTH = 720
    private const val MARGIN = 32
    private const val LOOKBACK_DAYS = 45

    private val bg = Color.rgb(0, 0, 0)
    private val card = Color.rgb(16, 16, 20)
    private val ink = Color.rgb(242, 242, 245)
    private val muted = Color.rgb(142, 142, 152)
    private val line = Color.rgb(36, 36, 42)
    private val accent = Color.rgb(255, 106, 43)
    private val good = Color.rgb(47, 191, 113)

    fun entryHasContent(entry: JournalEntry): Boolean =
        entry.note.isNotBlank() || entry.answers.any { it.isNotBlank() } ||
            entry.factors.sleep != null || entry.factors.sleepQuality != null || entry.factors.diet != null || entry.factors.activity != null

    fun hash(date: String, tasks: List<Task>, entry: JournalEntry): String {
        val text = buildString {
            append(date)
            tasks.forEach { append("|${it.what}|${it.howMuch}|${it.whenText}|${it.whereText}|${it.time}|${it.perceivedDifficulty}|${it.actualDifficulty}|${it.completed}") }
            append(Store.json.encodeToString(JournalEntry.serializer(), entry))
        }
        var hash = 0x811c9dc5L
        for (char in text) {
            hash = hash xor char.code.toLong()
            hash = (hash * 0x01000193L) and 0xFFFFFFFFL
        }
        return hash.toString(16)
    }

    private fun paint(size: Float, color: Int, bold: Boolean = false) = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = size
        this.color = color
        typeface = if (bold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
    }

    private fun layout(text: String, paint: TextPaint, width: Int): StaticLayout =
        StaticLayout.Builder.obtain(text, 0, text.length, paint, width).setAlignment(Layout.Alignment.ALIGN_NORMAL).build()

    fun render(state: AppState, date: String): ByteArray {
        val tasks = state.tasks.filter { it.date == date }
        val entry = state.extras.journal[date] ?: JournalEntry()
        val inner = WIDTH - MARGIN * 2
        val xp = Progress.xpInfo(state.tasks)
        val streak = Progress.streakInfo(state.tasks, Dates.today())
        val summary = TaskStats.summarize(tasks)

        fun build(canvas: Canvas?): Int {
            var y = 0f
            fun rect(left: Float, top: Float, right: Float, bottom: Float, color: Int, radius: Float = 0f) {
                canvas ?: return
                val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color }
                if (radius > 0) canvas.drawRoundRect(left, top, right, bottom, radius, radius, p) else canvas.drawRect(left, top, right, bottom, p)
            }
            fun text(value: String, p: TextPaint, x: Float = MARGIN.toFloat()): Float {
                val l = layout(value, p, inner - (x - MARGIN).toInt())
                canvas?.let { it.save(); it.translate(x, y); l.draw(it); it.restore() }
                return l.height.toFloat()
            }

            rect(0f, 0f, WIDTH.toFloat(), 104f, card)
            y = 26f
            y += text("ATTACK DAY", paint(30f, accent, true)) + 4
            y += text(Dates.long(date).uppercase() + ", " + date.take(4), paint(14f, muted))
            y = 120f

            val stat = "${summary.completed}/${summary.planned} done   ·   level ${xp.level}   ·   streak ${streak.current}"
            y += text(stat, paint(16f, ink, true)) + 14
            rect(MARGIN.toFloat(), y, (WIDTH - MARGIN).toFloat(), y + 8, line, 4f)
            rect(MARGIN.toFloat(), y, MARGIN + inner * summary.rate.toFloat(), y + 8, good, 4f)
            y += 28

            if (tasks.isNotEmpty()) {
                y += text("TASKS", paint(13f, muted, true)) + 10
                tasks.forEach { task ->
                    val title = layout(task.what, paint(18f, if (task.completed) muted else ink, true), inner - 60)
                    val meta = listOf(task.howMuch, listOf(task.time, task.whenText).filter { it.isNotBlank() }.joinToString(" "), task.whereText)
                        .filter { it.isNotBlank() }.joinToString("  ·  ")
                    val metaLayout = if (meta.isNotEmpty()) layout(meta, paint(14f, muted), inner - 60) else null
                    val height = title.height + (metaLayout?.height ?: 0) + 44
                    rect(MARGIN.toFloat(), y, (WIDTH - MARGIN).toFloat(), y + height, card, 16f)
                    rect(MARGIN + 14f, y + 14, MARGIN + 38f, y + 38, if (task.completed) good else line, 8f)
                    canvas?.let {
                        it.save(); it.translate(MARGIN + 52f, y + 10); title.draw(it)
                        metaLayout?.let { m -> it.translate(0f, title.height + 2f); m.draw(it) }
                        it.restore()
                        val pdText = "PD ${task.perceivedDifficulty}" + (task.actualDifficulty?.let { a -> "  AD $a" } ?: "")
                        it.drawText(pdText, MARGIN + 52f, y + height - 12, paint(12f, accent, true))
                    }
                    y += height + 10
                }
                y += 8
            }

            val factors = entry.factors
            val factorLine = listOfNotNull(
                factors.sleep?.let { "Sleep ${it}h" },
                factors.sleepQuality?.let { "Sleep quality ${it.toInt()}/5" },
                factors.diet?.let { "Diet ${it.toInt()}/5" },
                factors.activity?.let { "Activity ${it.toInt()} min" },
            ).joinToString("   ·   ")
            if (factorLine.isNotEmpty()) {
                y += text("DAILY CHECK", paint(13f, muted, true)) + 6
                y += text(factorLine, paint(15f, ink)) + 18
            }

            val answered = JOURNAL_PROMPTS.indices.filter { entry.answers.getOrNull(it)?.isNotBlank() == true }
            if (answered.isNotEmpty() || entry.note.isNotBlank()) {
                y += text("FEEDBACK JOURNAL", paint(13f, muted, true)) + 8
                answered.forEach { index ->
                    y += text(JOURNAL_PROMPTS[index], paint(13f, accent, true)) + 2
                    y += text(entry.answers[index], paint(15f, ink)) + 14
                }
                if (entry.note.isNotBlank()) {
                    y += text("Anything else", paint(13f, accent, true)) + 2
                    y += text(entry.note, paint(15f, ink)) + 14
                }
            }
            return (y + MARGIN).toInt()
        }

        val height = build(null)
        val bitmap = Bitmap.createBitmap(WIDTH, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(bg)
        build(canvas)
        val out = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        bitmap.recycle()
        return out.toByteArray()
    }

    fun dayHasContent(state: AppState, date: String): Boolean =
        state.tasks.any { it.date == date } || entryHasContent(state.extras.journal[date] ?: JournalEntry())

    fun pendingDates(state: AppState): List<String> {
        val today = Dates.today()
        val oldest = Dates.shift(today, -LOOKBACK_DAYS)
        val dates = (state.tasks.map { it.date } + state.extras.journal.keys).toSet()
        return dates.filter { it in oldest..today }.sorted().filter { date ->
            dayHasContent(state, date) &&
                state.extras.saved[date]?.hash != hash(date, state.tasks.filter { it.date == date }, state.extras.journal[date] ?: JournalEntry())
        }
    }

    fun save(context: Context, date: String): Result<Int> = runCatching {
        val state = Store.state.value
        val png = render(state, date)
        check(Files.saveFile(context, FOLDER, "$date.png", png)) { "Allow file access first so sheets can be saved in Documents/Attack Mode." }
        val digest = hash(date, state.tasks.filter { it.date == date }, state.extras.journal[date] ?: JournalEntry())
        Store.updateExtras { it.copy(saved = it.saved + (date to SavedSheet(digest, Instant.now().toString(), png.size))) }
        png.size
    }

    fun autoSave(context: Context): Int {
        val state = Store.state.value
        if (state.extras.pngMode != "auto" || !Files.hasAccess(context)) return 0
        var saved = 0
        for (date in pendingDates(state)) if (save(context, date).isSuccess) saved++
        return saved
    }
}
