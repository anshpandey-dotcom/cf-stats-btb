package com.fleetmc.flipshelf.translate

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.fleetmc.flipshelf.R
import com.fleetmc.flipshelf.core.Importer
import com.fleetmc.flipshelf.core.Library
import com.fleetmc.flipshelf.library.MainActivity
import java.io.File
import java.util.UUID

class TranslateService : Service() {

    @Volatile
    private var cancelled = false
    private var worker: Thread? = null
    private var lastNotify = 0L

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_CANCEL) {
            cancelled = true
            return START_NOT_STICKY
        }
        val id = intent?.getStringExtra(EXTRA_BOOK_ID)
        if (id == null) {
            stopSelf()
            return START_NOT_STICKY
        }
        if (worker?.isAlive == true) return START_NOT_STICKY
        cancelled = false
        ensureChannel()
        ServiceCompat.startForeground(
            this, NOTIFICATION_ID, progressNotification(getString(R.string.translate_starting), 0, true),
            if (Build.VERSION.SDK_INT >= 29) ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC else 0
        )
        worker = Thread { work(id) }.also { it.start() }
        return START_NOT_STICKY
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            val m = getSystemService(NotificationManager::class.java)
            m.createNotificationChannel(NotificationChannel(CHANNEL, getString(R.string.translate_channel), NotificationManager.IMPORTANCE_LOW))
        }
    }

    private fun openApp(): PendingIntent = PendingIntent.getActivity(
        this, 0, Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
    )

    private fun progressNotification(text: String, percent: Int, indeterminate: Boolean): Notification {
        val cancel = PendingIntent.getService(
            this, 1, Intent(this, TranslateService::class.java).setAction(ACTION_CANCEL),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        return NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle(getString(R.string.translate_title))
            .setContentText(text)
            .setOnlyAlertOnce(true)
            .setOngoing(true)
            .setContentIntent(openApp())
            .setProgress(100, percent, indeterminate)
            .addAction(0, getString(R.string.cancel), cancel)
            .build()
    }

    private fun update(job: TranslationState.Job) {
        TranslationState.publish(job)
        val now = SystemClock.uptimeMillis()
        if (now - lastNotify < 700 && job.percent in 1..99) return
        lastNotify = now
        val text = if (job.indeterminate) job.phase else "${job.phase} ${job.percent}%"
        try {
            getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, progressNotification(text, job.percent, job.indeterminate))
        } catch (_: Exception) {
        }
    }

    private fun finish(message: String, success: Boolean) {
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        try {
            val n = NotificationCompat.Builder(this, CHANNEL)
                .setSmallIcon(android.R.drawable.stat_sys_download_done)
                .setContentTitle(getString(if (success) R.string.translate_done else R.string.translate_failed))
                .setContentText(message)
                .setAutoCancel(true)
                .setContentIntent(openApp())
                .build()
            getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID + 1, n)
        } catch (_: Exception) {
        }
        stopSelf()
    }

    private fun work(bookId: String) {
        val library = Library.get(this)
        val book = library.find(bookId)
        if (book == null) {
            finish("Book not found", false)
            return
        }
        val title = book.title
        fun phase(text: String, percent: Int = 0, indeterminate: Boolean = true) =
            update(TranslationState.Job(bookId, title, text, percent, indeterminate))
        var translator: FallbackTranslator? = null
        try {
            phase(getString(R.string.translate_reading))
            val chapters = TextExtractor.extract(applicationContext, library, book) { cancelled }
            if (cancelled) throw TranslationPipeline.Cancelled()

            phase(getString(R.string.translate_checking))
            val sample = chapters.flatMap { it.blocks }.filter { it.level == 0 && it.text.length > 60 }.let { list ->
                list.drop(list.size / 3).take(6).joinToString(" ") { it.text }
            }.take(1500).ifBlank { chapters.flatMap { it.blocks }.take(5).joinToString(" ") { it.text }.take(1500) }
            val web = WebTranslator()
            val fb = FallbackTranslator(web) { MlKitTranslator() }
            translator = fb
            val source = fb.begin(sample)
            if (source == "hi") throw NoTextException(getString(R.string.translate_already_hindi))
            if (fb.usedOffline) phase(getString(R.string.translate_offline_mode))
            if (cancelled) throw TranslationPipeline.Cancelled()

            val translated = TranslationPipeline.run(
                chapters,
                { fb.translate(it) },
                { phase(getString(R.string.translate_working), it, false) },
                { cancelled },
                if (fb.usedOffline) 1 else 4
            )

            phase(getString(R.string.translate_saving), 100, false)
            val newTitle = translateTitle(web, book.title)
            val file = File(library.booksDir, UUID.randomUUID().toString().substring(0, 12) + ".tmp")
            EpubWriter.write(file, newTitle, "hi", translated)
            val created = Importer(applicationContext, library).addGenerated(file, newTitle, library.coverOf(book))
            TranslationState.publish(TranslationState.Job(bookId, created.title, "", 100, false, finished = true))
            finish(created.title, true)
        } catch (e: TranslationPipeline.Cancelled) {
            TranslationState.publish(null)
            finish(getString(R.string.translate_cancelled), false)
        } catch (e: Throwable) {
            val message = e.message ?: e.javaClass.simpleName
            val shown = if (e is NoTextException) message else getString(R.string.translate_network_hint, message)
            TranslationState.publish(TranslationState.Job(bookId, title, "", 0, false, finished = true, error = shown))
            finish(shown, false)
        } finally {
            translator?.close()
        }
    }

    private fun translateTitle(web: WebTranslator, title: String): String {
        val translated = try {
            web.translate(title)
        } catch (_: Exception) {
            title
        }
        return "$translated (हिन्दी)"
    }

    companion object {
        const val EXTRA_BOOK_ID = "book_id"
        const val ACTION_CANCEL = "com.fleetmc.flipshelf.CANCEL_TRANSLATION"
        private const val CHANNEL = "translate"
        private const val NOTIFICATION_ID = 4101

        fun start(context: Context, bookId: String) {
            val intent = Intent(context, TranslateService::class.java).putExtra(EXTRA_BOOK_ID, bookId)
            ContextCompat.startForegroundService(context, intent)
        }

        fun cancel(context: Context) {
            context.startService(Intent(context, TranslateService::class.java).setAction(ACTION_CANCEL))
        }
    }
}
