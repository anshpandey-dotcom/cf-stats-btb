package com.ansh.recalm.tts

enum class Engine { KOKORO, PIPER }

data class VoicePack(
    val id: String,
    val title: String,
    val url: String,
    val sizeMb: Int,
    val engine: Engine,
    val note: String
)

data class Voice(
    val id: String,
    val name: String,
    val accent: String,
    val female: Boolean,
    val engine: Engine,
    val speakerId: Int,
    val packIds: List<String>,
    val recommended: Boolean = false
)

object VoiceCatalog {

    private const val BASE = "https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/"

    const val KOKORO_STANDARD = "kokoro-standard"
    const val KOKORO_COMPACT = "kokoro-compact"

    val packs = listOf(
        VoicePack(KOKORO_STANDARD, "Kokoro Studio", BASE + "kokoro-multi-lang-v1_0.tar.bz2", 350, Engine.KOKORO,
            "Best quality and the smoothest playback. Unlocks every Kokoro voice."),
        VoicePack(KOKORO_COMPACT, "Kokoro Compact", BASE + "kokoro-int8-multi-lang-v1_0.tar.bz2", 132, Engine.KOKORO,
            "Smaller download for low storage. Same voices, can pause between sentences on older phones."),
        VoicePack("piper-pratham", "Pratham", BASE + "vits-piper-hi_IN-pratham-medium.tar.bz2", 67, Engine.PIPER,
            "Light Hindi male voice. Runs smoothly on any phone."),
        VoicePack("piper-priyamvada", "Priyamvada", BASE + "vits-piper-hi_IN-priyamvada-medium.tar.bz2", 67, Engine.PIPER,
            "Light Hindi female voice. Runs smoothly on any phone."),
        VoicePack("piper-rohan", "Rohan", BASE + "vits-piper-hi_IN-rohan-medium.tar.bz2", 67, Engine.PIPER,
            "Light Hindi male voice. Runs smoothly on any phone.")
    )

    private val kokoroPacks = listOf(KOKORO_STANDARD, KOKORO_COMPACT)

    private fun kokoro(id: String, name: String, accent: String, female: Boolean, sid: Int, recommended: Boolean = false) =
        Voice("kokoro-$id", name, accent, female, Engine.KOKORO, sid, kokoroPacks, recommended)

    val voices: List<Voice> = listOf(
        kokoro("hf_alpha", "Alpha", "Hindi + English", true, 31, true),
        kokoro("hf_beta", "Beta", "Hindi + English", true, 32, true),
        kokoro("hm_omega", "Omega", "Hindi + English", false, 33, true),
        kokoro("hm_psi", "Psi", "Hindi + English", false, 34, true),
        Voice("piper-priyamvada", "Priyamvada", "Hindi + English", true, Engine.PIPER, 0, listOf("piper-priyamvada")),
        Voice("piper-pratham", "Pratham", "Hindi + English", false, Engine.PIPER, 0, listOf("piper-pratham")),
        Voice("piper-rohan", "Rohan", "Hindi + English", false, Engine.PIPER, 0, listOf("piper-rohan")),
        kokoro("af_heart", "Heart", "American English", true, 3),
        kokoro("af_bella", "Bella", "American English", true, 2),
        kokoro("af_nicole", "Nicole", "American English, whisper", true, 6),
        kokoro("af_sky", "Sky", "American English", true, 10),
        kokoro("af_nova", "Nova", "American English", true, 7),
        kokoro("af_sarah", "Sarah", "American English", true, 9),
        kokoro("af_river", "River", "American English", true, 8),
        kokoro("af_alloy", "Alloy", "American English", true, 0),
        kokoro("af_aoede", "Aoede", "American English", true, 1),
        kokoro("af_jessica", "Jessica", "American English", true, 4),
        kokoro("af_kore", "Kore", "American English", true, 5),
        kokoro("am_michael", "Michael", "American English", false, 16),
        kokoro("am_fenrir", "Fenrir", "American English", false, 14),
        kokoro("am_puck", "Puck", "American English", false, 18),
        kokoro("am_onyx", "Onyx", "American English", false, 17),
        kokoro("am_adam", "Adam", "American English", false, 11),
        kokoro("am_echo", "Echo", "American English", false, 12),
        kokoro("am_eric", "Eric", "American English", false, 13),
        kokoro("am_liam", "Liam", "American English", false, 15),
        kokoro("am_santa", "Santa", "American English", false, 19),
        kokoro("bf_emma", "Emma", "British English", true, 21),
        kokoro("bf_isabella", "Isabella", "British English", true, 22),
        kokoro("bf_alice", "Alice", "British English", true, 20),
        kokoro("bf_lily", "Lily", "British English", true, 23),
        kokoro("bm_george", "George", "British English", false, 26),
        kokoro("bm_fable", "Fable", "British English", false, 25),
        kokoro("bm_lewis", "Lewis", "British English", false, 27),
        kokoro("bm_daniel", "Daniel", "British English", false, 24)
    )

    const val DEFAULT_VOICE = "kokoro-hf_alpha"

    fun voice(id: String): Voice? = voices.firstOrNull { it.id == id }

    fun pack(id: String): VoicePack? = packs.firstOrNull { it.id == id }
}
