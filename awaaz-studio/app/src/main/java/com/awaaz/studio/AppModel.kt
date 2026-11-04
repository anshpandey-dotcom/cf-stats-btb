package com.awaaz.studio

import android.app.Application
import android.content.Context
import android.content.Intent
import android.media.MediaPlayer
import android.net.Uri
import android.provider.OpenableColumns
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.FileProvider
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.awaaz.studio.audio.Audio
import com.awaaz.studio.audio.Cutout
import com.awaaz.studio.audio.CutoutReport
import com.awaaz.studio.audio.CutoutSettings
import com.awaaz.studio.audio.Recorder
import com.awaaz.studio.engine.Catalog
import com.awaaz.studio.engine.Lang
import com.awaaz.studio.engine.Pack
import com.awaaz.studio.engine.PackState
import com.awaaz.studio.engine.Packs
import com.awaaz.studio.engine.SpeechEngine
import com.awaaz.studio.engine.Style
import com.awaaz.studio.engine.Transcriber
import com.awaaz.studio.engine.Voice
import com.awaaz.studio.studio.RemoteVoice
import com.awaaz.studio.studio.StudioClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class Busy(val label: String, val progress: Float? = null)

class AppModel(app: Application) : AndroidViewModel(app) {
    private val ctx: Context get() = getApplication()
    private val prefs = app.getSharedPreferences("awaaz", Context.MODE_PRIVATE)

    val packs = Packs(app)
    private val engine = SpeechEngine(packs)
    private val transcriber = Transcriber(packs)
    val studio = StudioClient { serverUrl }

    val libraryDir = File(app.filesDir, "library").apply { mkdirs() }
    val library = mutableStateListOf<File>()

    var text by mutableStateOf(prefs.getString("text", SAMPLE) ?: SAMPLE)
    var lang by mutableStateOf(Lang.valueOf(prefs.getString("lang", Lang.Hindi.name)!!))
    var voice by mutableStateOf(Catalog.voice(prefs.getString("voice", "ananya")!!))
    var style by mutableStateOf(Style.valueOf(prefs.getString("style", Style.Narrator.name)!!))
    var speed by mutableFloatStateOf(prefs.getFloat("speed", 1f))
    var autoCutout by mutableStateOf(prefs.getBoolean("autoCutout", true))
    var dictionary by mutableStateOf(prefs.getString("dict", DEFAULT_DICT) ?: DEFAULT_DICT)
    var serverUrl by mutableStateOf(prefs.getString("server", "http://192.168.1.10:8765") ?: "")
    var cutout by mutableStateOf(CutoutSettings())

    var busy by mutableStateOf<Busy?>(null)
    var message by mutableStateOf<String?>(null)
    var lastOutput by mutableStateOf<File?>(null)
    var transcript by mutableStateOf("")
    var cutoutReport by mutableStateOf<CutoutReport?>(null)
    var serverOnline by mutableStateOf(false)
    val remoteVoices = mutableStateListOf<RemoteVoice>()
    var remote by mutableStateOf<RemoteVoice?>(null)

    var playing by mutableStateOf<File?>(null)
    var position by mutableFloatStateOf(0f)
    private var player: MediaPlayer? = null
    private var job: Job? = null
    private var recorder: Recorder? = null
    var recording by mutableStateOf(false)
    var micLevel by mutableFloatStateOf(0f)

    init {
        refreshLibrary()
        viewModelScope.launch {
            while (isActive) {
                player?.let { p -> if (p.isPlaying) position = p.currentPosition.toFloat() / p.duration.coerceAtLeast(1) }
                recorder?.let { micLevel = it.level }
                delay(80)
            }
        }
    }

    fun save() {
        prefs.edit()
            .putString("text", text).putString("lang", lang.name).putString("voice", voice.id)
            .putString("style", style.name).putFloat("speed", speed).putBoolean("autoCutout", autoCutout)
            .putString("dict", dictionary).putString("server", serverUrl).apply()
    }

    fun voicesFor(l: Lang) = Catalog.voices.filter { it.hindi == (l != Lang.English) }

    fun selectLang(l: Lang) {
        lang = l
        if (voice.hindi != (l != Lang.English)) voice = voicesFor(l).first()
        save()
    }

    fun ready(p: Pack) = packs.states[p.id] == PackState.Ready

    fun download(p: Pack) = viewModelScope.launch { packs.download(p) }

    fun refreshLibrary() {
        library.clear()
        library.addAll(libraryDir.listFiles().orEmpty().filter { it.isFile && !it.name.endsWith(".part") }.sortedByDescending { it.lastModified() })
    }

    private fun stamp() = SimpleDateFormat("dd MMM HH-mm-ss", Locale.US).format(Date())

    fun newFile(prefix: String, ext: String) = File(libraryDir, "$prefix ${stamp()}.$ext")

    private fun parseDictionary(): Map<String, String> = dictionary.lines().mapNotNull { line ->
        val i = line.indexOf('=')
        if (i <= 0) null else line.substring(0, i).trim() to line.substring(i + 1).trim()
    }.toMap()

    private fun run(label: String, block: suspend () -> Unit) {
        if (job?.isActive == true) return
        job = viewModelScope.launch {
            busy = Busy(label)
            try {
                block()
            } catch (e: kotlinx.coroutines.CancellationException) {
                message = "Stopped"
            } catch (e: Throwable) {
                message = e.message ?: "Something went wrong"
            } finally {
                busy = null
                refreshLibrary()
            }
        }
    }

    fun cancel() {
        job?.cancel()
        recorder?.stop()
    }

    fun pick(v: Voice) {
        voice = v
        remote = null
        save()
    }

    fun generate() {
        if (text.isBlank()) { message = "Type something first"; return }
        remote?.let { studioSpeak(it.id); return }
        if (!ready(voice.pack)) { message = "Download the ${voice.pack.title} pack first"; return }
        save()
        stop()
        run("Generating") {
            val raw = File(ctx.cacheDir, "speech.wav")
            engine.speak(text, voice, style, speed, lang, if (lang == Lang.English) emptyMap() else parseDictionary(), raw) { busy = Busy("Generating", it) }
            val out = newFile("${voice.name} ${lang.label}", "wav")
            if (autoCutout) {
                busy = Busy("AI Cutout")
                val (pcm, _) = withContext(Dispatchers.Default) {
                    Cutout.process(Audio.read(raw), cutout.copy(silence = cutout.silence * 0.5f, noise = 0f))
                }
                withContext(Dispatchers.IO) { Audio.writeWav(out, pcm) }
            } else {
                raw.copyTo(out, true)
            }
            lastOutput = out
            play(out)
        }
    }

    fun preview(v: Voice) {
        if (!ready(v.pack)) { download(v.pack); return }
        stop()
        run("Preview") {
            val f = File(ctx.cacheDir, "preview-${v.id}.wav")
            if (!f.exists()) engine.speak(Catalog.preview(v), v, Style.Narrator, 1f, if (v.hindi) Lang.Hindi else Lang.English, emptyMap(), f) {}
            play(f)
        }
    }

    fun play(f: File) {
        stop()
        player = MediaPlayer().apply {
            setDataSource(f.absolutePath)
            setOnCompletionListener { playing = null; position = 0f }
            prepare()
            start()
        }
        playing = f
    }

    fun stop() {
        player?.release()
        player = null
        playing = null
        position = 0f
    }

    fun toggle(f: File) = if (playing == f) stop() else play(f)

    fun share(f: File) {
        val uri = FileProvider.getUriForFile(ctx, ctx.packageName + ".files", f)
        val mime = when (f.extension.lowercase()) { "txt" -> "text/plain"; "mp4" -> "video/mp4"; "m4a" -> "audio/mp4"; else -> "audio/wav" }
        val send = Intent(Intent.ACTION_SEND).setType(mime).putExtra(Intent.EXTRA_STREAM, uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        ctx.startActivity(Intent.createChooser(send, f.name).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    fun delete(f: File) {
        if (playing == f) stop()
        f.delete()
        if (lastOutput == f) lastOutput = null
        refreshLibrary()
    }

    fun exportM4a(f: File) = run("Exporting") {
        val out = File(libraryDir, f.nameWithoutExtension + ".m4a")
        withContext(Dispatchers.Default) { Audio.writeM4a(out, Audio.read(f)) }
        message = "Saved ${out.name}"
    }

    fun importToLibrary(uri: Uri): File {
        val name = ctx.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
            if (it.moveToFirst()) it.getString(0) else null
        } ?: "import-${stamp()}"
        val f = File(ctx.cacheDir, name)
        ctx.contentResolver.openInputStream(uri)!!.use { input -> f.outputStream().use { input.copyTo(it) } }
        return f
    }

    fun runCutout(source: File) = run("AI Cutout") {
        val (pcm, report) = withContext(Dispatchers.Default) { Cutout.process(Audio.read(source), cutout) }
        val out = newFile("${source.nameWithoutExtension.take(24)} clean", "wav")
        withContext(Dispatchers.IO) { Audio.writeWav(out, pcm) }
        cutoutReport = report
        lastOutput = out
        play(out)
    }

    fun startRecording(onDone: (com.awaaz.studio.audio.Pcm) -> Unit) {
        if (recording) return
        stop()
        val r = Recorder()
        recorder = r
        recording = true
        viewModelScope.launch {
            val pcm = r.record()
            recording = false
            recorder = null
            onDone(pcm)
        }
    }

    fun stopRecording() = recorder?.stop()

    private fun whisper(accurate: Boolean): Pack? {
        val pack = if (accurate) Catalog.whisperSmall else Catalog.whisperBase
        if (!ready(pack)) { message = "Download ${pack.title} first"; return null }
        return pack
    }

    private suspend fun listen(pcm: com.awaaz.studio.audio.Pcm, pack: Pack, hindi: Boolean) {
        transcript = transcriber.transcribe(pcm, pack, if (hindi) "hi" else "en") { busy = Busy("Converting speech to text", it) }
        if (transcript.isNotBlank()) newFile("Transcript", "txt").writeText(transcript)
    }

    fun transcribe(pcm: com.awaaz.studio.audio.Pcm, accurate: Boolean, hindi: Boolean) {
        val pack = whisper(accurate) ?: return
        run("Listening") { listen(pcm, pack, hindi) }
    }

    fun transcribeFile(f: File, accurate: Boolean, hindi: Boolean) {
        val pack = whisper(accurate) ?: return
        run("Reading audio") { listen(withContext(Dispatchers.Default) { Audio.read(f) }, pack, hindi) }
    }

    fun saveRecording(pcm: com.awaaz.studio.audio.Pcm, prefix: String): File {
        val f = newFile(prefix, "wav")
        Audio.writeWav(f, pcm)
        refreshLibrary()
        return f
    }

    fun checkServer() = viewModelScope.launch {
        save()
        serverOnline = studio.ping()
        if (serverOnline) runCatching { remoteVoices.clear(); remoteVoices.addAll(studio.voices()) }
        message = if (serverOnline) "Studio PC connected" else "Can't reach Studio PC at $serverUrl"
    }

    private fun needServer(): Boolean {
        if (!serverOnline) message = "Connect your Studio PC first (Studio → Studio PC)"
        return serverOnline
    }

    fun cloneVoice(name: String, sample: File) {
        if (!needServer()) return
        run("Cloning voice") {
            val v = studio.clone(name, sample)
            remoteVoices.add(v)
            message = "Voice \"${v.name}\" is ready"
        }
    }

    fun studioSpeak(voiceId: String) {
        if (!needServer() || text.isBlank()) return
        run("Generating with cloned voice") {
            val out = newFile("Clone ${lang.label}", "wav")
            studio.speak(Tags.strip(text), lang.code, voiceId, out)
            lastOutput = out
            play(out)
        }
    }

    fun voiceChange(input: File, voiceId: String) {
        if (!needServer()) return
        run("Changing voice") {
            val out = newFile("Voice changer", "wav")
            studio.convert(input, voiceId, out)
            lastOutput = out
            play(out)
        }
    }

    fun dub(input: File, target: String, voiceId: String) {
        if (!needServer()) return
        run("Dubbing video") {
            val out = newFile("Dub ${target.uppercase()}", "mp4")
            studio.dub(input, target, voiceId, out)
            lastOutput = out
            message = "Dubbed video saved to Library"
        }
    }

    fun music(prompt: String, seconds: Int) {
        if (!needServer()) return
        run("Composing music") {
            val out = newFile("Music", "wav")
            studio.music(prompt, seconds, out)
            lastOutput = out
            play(out)
        }
    }

    override fun onCleared() {
        stop()
        engine.release()
        transcriber.release()
    }

    companion object {
        const val SAMPLE = "दोस्तों, क्या आपने कभी सोचा है कि सिर्फ़ एक मोबाइल से पूरा YouTube चैनल चलाया जा सकता है? [pause 0.5s] आज के वीडियो में हम यही जानेंगे।"
        const val DEFAULT_DICT = "YouTube = यूट्यूब\nvideo = वीडियो\nsubscribe = सब्सक्राइब\nchannel = चैनल"
    }
}

object Tags {
    fun strip(t: String) = t.replace(Regex("""\[\s*pause[^\]]*]""", RegexOption.IGNORE_CASE), " ")
}
