package com.ansh.recalm.player

import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import androidx.core.content.ContextCompat
import com.ansh.recalm.data.Library
import com.ansh.recalm.data.Sentences
import com.ansh.recalm.data.Settings
import com.ansh.recalm.tts.EnglishG2p
import com.ansh.recalm.tts.Engine
import com.ansh.recalm.tts.KokoroSynthesizer
import com.ansh.recalm.tts.PiperSynthesizer
import com.ansh.recalm.tts.Synthesizer
import com.ansh.recalm.tts.Utterance
import com.ansh.recalm.tts.VoiceCatalog
import com.ansh.recalm.tts.VoiceManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.Executors
import kotlinx.coroutines.asCoroutineDispatcher

data class PlayerState(
    val bookId: String? = null,
    val page: Int = 0,
    val sentence: Int = 0,
    val wordStart: Int = -1,
    val wordEnd: Int = -1,
    val playing: Boolean = false,
    val active: Boolean = false,
    val loading: String? = null,
    val error: String? = null,
    val sleepEndsAt: Long? = null,
    val musicPlaying: Boolean = false
)

private class Spoken(val page: Int, val sentence: Int, val utterance: Utterance)

private class Written(val startFrame: Long, val spoken: Spoken)

class PlaybackEngine(
    private val context: Context,
    private val library: Library,
    private val settings: Settings,
    private val voices: VoiceManager
) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val synthDispatcher = Executors.newSingleThreadExecutor { Thread(it, "tts").apply { priority = Thread.MAX_PRIORITY } }
        .asCoroutineDispatcher()
    private val control = Mutex()

    private val _state = MutableStateFlow(PlayerState())
    val state: StateFlow<PlayerState> = _state.asStateFlow()

    private val ambient = AmbientSynth()
    private var synth: Synthesizer? = null
    private var synthKey: String? = null
    private var runJob: Job? = null
    private var timerJob: Job? = null
    @Volatile private var paused = false
    @Volatile private var track: AudioTrack? = null
    @Volatile private var voiceGain = 1f

    private val audioManager = context.getSystemService(AudioManager::class.java)
    private var focusRequest: AudioFocusRequest? = null
    private var resumeOnFocus = false

    init {
        scope.launch {
            settings.state.collect { p ->
                ambient.setVolume(p.musicVolume)
            }
        }
    }

    fun play(bookId: String, page: Int, sentence: Int) {
        scope.launch {
            control.withLock {
                stopRun()
                paused = false
                _state.update {
                    it.copy(bookId = bookId, page = page, sentence = sentence, wordStart = -1, wordEnd = -1,
                        playing = true, active = true, error = null)
                }
                startService()
                requestFocus()
                startMusicIfChosen()
                runJob = scope.launch { run(bookId, page, sentence) }
            }
        }
    }

    fun resumeBook(bookId: String) {
        val book = library.book(bookId) ?: return
        val s = _state.value
        if (s.bookId == bookId && s.active) {
            if (paused) resume()
            return
        }
        play(bookId, book.page, book.sentence)
    }

    fun toggle() {
        val s = _state.value
        when {
            !s.active && s.bookId != null -> resumeBook(s.bookId)
            paused -> resume()
            else -> pause()
        }
    }

    fun pause() {
        if (!_state.value.active) return
        paused = true
        track?.pause()
        _state.update { it.copy(playing = false) }
        if (!settings.value.keepMusicAfterVoice) ambient.setFade(0f)
    }

    fun resume() {
        if (!_state.value.active) return
        paused = false
        requestFocus()
        startService()
        ambient.setFade(1f)
        track?.play()
        _state.update { it.copy(playing = true) }
    }

    fun stop() {
        scope.launch {
            control.withLock {
                stopRun()
                ambient.stop()
                cancelTimer()
                abandonFocus()
                _state.update { it.copy(playing = false, active = false, wordStart = -1, wordEnd = -1, musicPlaying = false, loading = null) }
            }
        }
    }

    fun skipSentence(delta: Int) {
        val s = _state.value
        val id = s.bookId ?: return
        val pages = library.pages(id)
        if (pages.isEmpty()) return
        var page = s.page
        var sentence = s.sentence + delta
        while (true) {
            val count = Sentences.forPage(pages[page]).size
            if (sentence < 0) {
                if (page == 0) {
                    sentence = 0
                    break
                }
                page--
                sentence += Sentences.forPage(pages[page]).size
            } else if (sentence >= count) {
                if (page >= pages.size - 1) {
                    sentence = (count - 1).coerceAtLeast(0)
                    break
                }
                sentence -= count
                page++
            } else break
        }
        play(id, page, sentence)
    }

    fun restartAtCurrent() {
        val s = _state.value
        val id = s.bookId ?: return
        if (s.active) play(id, s.page, s.sentence)
    }

    fun setMusic(id: String) {
        settings.update { it.copy(musicId = id) }
        if (id == "none") {
            ambient.stop()
            _state.update { it.copy(musicPlaying = false) }
        } else {
            ambient.stop()
            ambient.setFade(1f)
            ambient.setVolume(settings.value.musicVolume)
            ambient.play(id)
            startService()
            _state.update { it.copy(musicPlaying = true) }
        }
    }

    fun stopMusic() {
        ambient.stop()
        _state.update { it.copy(musicPlaying = false) }
    }

    private fun startMusicIfChosen() {
        val id = settings.value.musicId
        ambient.setFade(1f)
        if (id != "none" && !ambient.isPlaying) {
            ambient.setVolume(settings.value.musicVolume)
            ambient.play(id)
            _state.update { it.copy(musicPlaying = true) }
        }
    }

    fun setSleepTimer(minutes: Int) {
        cancelTimer()
        if (minutes <= 0) return
        settings.update { it.copy(sleepMinutes = minutes) }
        val endsAt = System.currentTimeMillis() + minutes * 60_000L
        _state.update { it.copy(sleepEndsAt = endsAt) }
        timerJob = scope.launch {
            val fadeMs = 45_000L
            delay((endsAt - System.currentTimeMillis() - fadeMs).coerceAtLeast(0))
            val start = System.currentTimeMillis()
            while (isActive) {
                val k = 1f - (System.currentTimeMillis() - start).toFloat() / fadeMs
                if (k <= 0f) break
                voiceGain = k
                track?.setVolume(k)
                ambient.setFade(k)
                delay(250)
            }
            control.withLock {
                stopRun()
                ambient.stop()
                abandonFocus()
                _state.update { it.copy(playing = false, active = false, sleepEndsAt = null, musicPlaying = false, wordStart = -1, wordEnd = -1) }
            }
            voiceGain = 1f
            ambient.setFade(1f)
        }
    }

    fun cancelTimer() {
        timerJob?.cancel()
        timerJob = null
        voiceGain = 1f
        track?.setVolume(1f)
        ambient.setFade(1f)
        _state.update { it.copy(sleepEndsAt = null) }
    }

    private suspend fun stopRun() {
        runJob?.cancelAndJoin()
        runJob = null
        track?.let {
            runCatching { it.pause(); it.flush(); it.release() }
        }
        track = null
    }

    private fun startService() {
        val intent = Intent(context, ReaderService::class.java)
        runCatching { ContextCompat.startForegroundService(context, intent) }
    }

    @Volatile private var englishG2p: EnglishG2p? = null

    private fun english(): EnglishG2p = englishG2p ?: synchronized(this) {
        englishG2p ?: run {
            val file = File(context.filesDir, "lexicon-en.txt")
            if (!file.exists() || file.length() == 0L) {
                val tmp = File(context.filesDir, "lexicon-en.txt.tmp")
                context.assets.open("lexicon-en.txt").use { input -> tmp.outputStream().use { input.copyTo(it) } }
                tmp.renameTo(file)
            }
            EnglishG2p(file).also { englishG2p = it }
        }
    }

    private suspend fun loadSynth(): Synthesizer? {
        val voiceId = settings.value.voiceId
        val key: String
        val factory: () -> Synthesizer
        if (voiceId.startsWith("custom-")) {
            val dir = voices.customDir(voiceId)
            if (!dir.exists()) return null
            key = voiceId
            factory = { PiperSynthesizer(dir, english()) }
        } else {
            val voice = VoiceCatalog.voice(voiceId) ?: return null
            val pack = voices.installedPackFor(voice) ?: return null
            val dir: File = voices.modelRoot(pack)
            key = "$pack:${voice.speakerId}"
            factory = when (voice.engine) {
                Engine.KOKORO -> { { KokoroSynthesizer(dir, voice.speakerId, english()) } }
                Engine.PIPER -> { { PiperSynthesizer(dir, english()) } }
            }
        }
        if (key == synthKey && synth != null) return synth
        _state.update { it.copy(loading = "Waking up the voice") }
        return withContext(synthDispatcher) {
            synth?.release()
            synth = null
            synthKey = null
            val created = runCatching { factory() }.getOrNull()
            synth = created
            synthKey = if (created != null) key else null
            created
        }.also { _state.update { s -> s.copy(loading = null) } }
    }

    private suspend fun run(bookId: String, startPage: Int, startSentence: Int) {
        val engine = loadSynth()
        if (engine == null) {
            _state.update {
                it.copy(playing = false, active = false, loading = null,
                    error = "Download a voice first. Open Voices and pick one you like.")
            }
            return
        }
        val pages = library.pages(bookId)
        if (pages.isEmpty()) return
        val queue = Channel<Spoken>(capacity = 3)
        val speed = settings.value.speed
        val producer = scope.launch(synthDispatcher) {
            var page = startPage.coerceIn(0, pages.size - 1)
            var sentence = startSentence
            try {
                while (isActive && page < pages.size) {
                    val refs = Sentences.forPage(pages[page])
                    while (isActive && sentence < refs.size) {
                        val ref = refs[sentence]
                        val text = pages[page].blocks[ref.block].text.substring(ref.start, ref.end)
                        val speakable = text.any { it.isLetterOrDigit() } && !settings.shouldSkip(ref.kind)
                        if (speakable) {
                            val utterance = runCatching { engine.speak(text, speed) }.getOrNull()
                            if (utterance != null && utterance.samples.isNotEmpty()) {
                                queue.send(Spoken(page, sentence, utterance))
                            }
                        }
                        sentence++
                    }
                    page++
                    sentence = 0
                }
            } finally {
                queue.close()
            }
        }
        val out = createTrack(engine.sampleRate)
        track = out
        val written = ArrayDeque<Written>()
        var total = 0L
        val ticker = scope.launch { tick(bookId, written) }
        try {
            if (!paused) out.play()
            for (item in queue) {
                synchronized(written) { written.addLast(Written(total, item)) }
                val pause = if (item.utterance.samples.size > 0) (engine.sampleRate * 0.18f).toInt() else 0
                writeAll(out, item.utterance.samples)
                writeAll(out, FloatArray(pause))
                total += item.utterance.samples.size + pause
            }
            while (currentCoroutineContext().isActive) {
                val head = out.playbackHeadPosition.toLong() and 0xFFFFFFFFL
                if (head >= total) break
                delay(100)
            }
            if (currentCoroutineContext().isActive) {
                val last = pages.size - 1
                library.saveProgress(bookId, last, 0)
                _state.update { it.copy(playing = false, active = false, wordStart = -1, wordEnd = -1) }
                if (!settings.value.keepMusicAfterVoice) {
                    ambient.stop()
                    _state.update { it.copy(musicPlaying = false) }
                }
            }
        } finally {
            producer.cancel()
            ticker.cancel()
        }
    }

    private suspend fun writeAll(out: AudioTrack, samples: FloatArray) {
        var offset = 0
        while (offset < samples.size) {
            if (!currentCoroutineContext().isActive) return
            if (paused) {
                delay(60)
                continue
            }
            val n = out.write(samples, offset, minOf(4096, samples.size - offset), AudioTrack.WRITE_NON_BLOCKING)
            if (n < 0) return
            if (n == 0) delay(25) else offset += n
        }
    }

    private suspend fun tick(bookId: String, written: ArrayDeque<Written>) {
        var lastSaved = -1 to -1
        while (scope.isActive) {
            val out = track
            if (out != null) {
                val head = out.playbackHeadPosition.toLong() and 0xFFFFFFFFL
                val current = synchronized(written) {
                    while (written.size > 1 && written[1].startFrame <= head) written.removeFirst()
                    written.firstOrNull()
                }
                if (current != null && head >= current.startFrame) {
                    val spoken = current.spoken
                    val local = (head - current.startFrame).toInt()
                    val mark = spoken.utterance.marks.lastOrNull { it.sample <= local }
                    _state.update {
                        it.copy(page = spoken.page, sentence = spoken.sentence,
                            wordStart = mark?.start ?: -1, wordEnd = mark?.end ?: -1)
                    }
                    if (lastSaved != spoken.page to spoken.sentence) {
                        lastSaved = spoken.page to spoken.sentence
                        library.saveProgress(bookId, spoken.page, spoken.sentence)
                    }
                }
            }
            delay(45)
        }
    }

    private fun createTrack(rate: Int): AudioTrack {
        val min = AudioTrack.getMinBufferSize(rate, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_FLOAT)
        return AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setSampleRate(rate)
                    .setEncoding(AudioFormat.ENCODING_PCM_FLOAT)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build()
            )
            .setBufferSizeInBytes(maxOf(min * 2, rate * 4 / 2))
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()
            .also { it.setVolume(voiceGain) }
    }

    private val focusListener = AudioManager.OnAudioFocusChangeListener { change ->
        when (change) {
            AudioManager.AUDIOFOCUS_LOSS -> {
                resumeOnFocus = false
                pause()
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                resumeOnFocus = _state.value.playing
                pause()
            }
            AudioManager.AUDIOFOCUS_GAIN -> if (resumeOnFocus) {
                resumeOnFocus = false
                resume()
            }
        }
    }

    private fun requestFocus() {
        val request = focusRequest ?: AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
            .setOnAudioFocusChangeListener(focusListener)
            .setWillPauseWhenDucked(false)
            .build()
            .also { focusRequest = it }
        audioManager.requestAudioFocus(request)
    }

    private fun abandonFocus() {
        focusRequest?.let { audioManager.abandonAudioFocusRequest(it) }
    }

    fun previewVoice(voiceId: String, text: String, onDone: () -> Unit) {
        scope.launch {
            settings.update { it.copy(voiceId = voiceId) }
            val engine = control.withLock {
                if (_state.value.active) pause()
                loadSynth()
            }
            if (engine == null) {
                onDone()
                return@launch
            }
            val utterance = withContext(synthDispatcher) { runCatching { engine.speak(text, settings.value.speed) }.getOrNull() }
            if (utterance != null) {
                val out = createTrack(utterance.sampleRate)
                out.play()
                out.write(utterance.samples, 0, utterance.samples.size, AudioTrack.WRITE_BLOCKING)
                delay(utterance.samples.size * 1000L / utterance.sampleRate + 200)
                out.release()
            }
            onDone()
        }
    }

    fun clearError() = _state.update { it.copy(error = null) }
}
