package com.ansh.recalm

import android.app.Application
import com.ansh.recalm.data.BackupManager
import com.ansh.recalm.data.Library
import com.ansh.recalm.data.Settings
import com.ansh.recalm.importer.Importer
import com.ansh.recalm.importer.OcrWorker
import com.ansh.recalm.player.PlaybackEngine
import com.ansh.recalm.tts.VoiceManager

class RecalmApp : Application() {

    lateinit var library: Library
        private set
    lateinit var settings: Settings
        private set
    lateinit var voices: VoiceManager
        private set
    lateinit var engine: PlaybackEngine
        private set
    val importer: Importer by lazy { Importer(this, library).also { it.afterImport = { id -> scanner.enqueue(id) } } }
    lateinit var scanner: OcrWorker
        private set
    val backups: BackupManager by lazy { BackupManager(this, library, settings) { voices.refreshCustom() } }

    override fun onCreate() {
        super.onCreate()
        library = Library(this)
        settings = Settings(this)
        voices = VoiceManager(this)
        scanner = OcrWorker(library) { importer }
        engine = PlaybackEngine(this, library, settings, voices, scanner)
    }
}
