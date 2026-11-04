package com.awaaz.studio.engine

enum class Lang(val label: String, val code: String) {
    Hindi("Hindi", "hi"),
    Hinglish("Hinglish", "hi"),
    Sanskrit("Sanskrit", "hi"),
    English("English", "en")
}

data class Pack(
    val id: String,
    val title: String,
    val repo: String,
    val files: List<String>,
    val sizeMb: Int
) {
    fun url(file: String) = "https://huggingface.co/$repo/resolve/main/$file"
}

enum class EngineKind { Kokoro, Piper }

data class Voice(
    val id: String,
    val name: String,
    val detail: String,
    val hindi: Boolean,
    val female: Boolean,
    val pack: Pack,
    val kind: EngineKind,
    val sid: Int = 0,
    val model: String = "",
    val accent: String = "en-us",
    val color: Long
)

object Catalog {
    val kokoro = Pack(
        "kokoro",
        "Studio voices (Hindi + English)",
        "csukuangfj/kokoro-int8-multi-lang-v1_0",
        listOf("model.int8.onnx", "voices.bin", "tokens.txt", "lexicon-us-en.txt", "lexicon-gb-en.txt"),
        150
    )
    private fun piper(name: String, mb: Int) = Pack(
        "piper-$name",
        name.substringAfter('-').substringBefore('-').replaceFirstChar { it.uppercase() },
        "csukuangfj/vits-piper-$name",
        listOf("$name.onnx", "tokens.txt"),
        mb
    )
    val rohan = piper("hi_IN-rohan-medium", 63)
    val pratham = piper("hi_IN-pratham-medium", 64)
    val priyamvada = piper("hi_IN-priyamvada-medium", 64)
    val lessac = piper("en_US-lessac-high", 109)

    val whisperBase = Pack(
        "whisper-base", "Mic to text · Fast", "csukuangfj/sherpa-onnx-whisper-base",
        listOf("base-encoder.int8.onnx", "base-decoder.int8.onnx", "base-tokens.txt"), 160
    )
    val whisperSmall = Pack(
        "whisper-small", "Mic to text · Accurate", "csukuangfj/sherpa-onnx-whisper-small",
        listOf("small-encoder.int8.onnx", "small-decoder.int8.onnx", "small-tokens.txt"), 375
    )

    val voicePacks = listOf(kokoro, rohan, pratham, priyamvada, lessac)
    val speechPacks = listOf(whisperBase, whisperSmall)

    private fun k(id: String, name: String, detail: String, hindi: Boolean, female: Boolean, sid: Int, accent: String, color: Long) =
        Voice(id, name, detail, hindi, female, kokoro, EngineKind.Kokoro, sid = sid, accent = accent, color = color)

    private fun p(id: String, name: String, detail: String, hindi: Boolean, female: Boolean, pack: Pack, color: Long) =
        Voice(id, name, detail, hindi, female, pack, EngineKind.Piper, model = pack.files.first(), color = color)

    val voices = listOf(
        k("ananya", "Ananya", "Hindi · warm female", true, true, 31, "hi", 0xFFE8572A),
        k("kavya", "Kavya", "Hindi · bright female", true, true, 32, "hi", 0xFFD9467A),
        k("arjun", "Arjun", "Hindi · deep male", true, false, 33, "hi", 0xFF3A7BD5),
        k("vikram", "Vikram", "Hindi · clear male", true, false, 34, "hi", 0xFF1F8A5B),
        p("rohan", "Rohan", "Hindi · male · light", true, false, rohan, 0xFF6A5ACD),
        p("pratham", "Pratham", "Hindi · male · light", true, false, pratham, 0xFF8C6A3F),
        p("priyamvada", "Priyamvada", "Hindi · female · light", true, true, priyamvada, 0xFFB0457E),
        k("heart", "Heart", "English US · female", false, true, 3, "en-us", 0xFFE8572A),
        k("bella", "Bella", "English US · female", false, true, 2, "en-us", 0xFFD9467A),
        k("nicole", "Nicole", "English US · soft female", false, true, 6, "en-us", 0xFF9C6ADE),
        k("sarah", "Sarah", "English US · female", false, true, 9, "en-us", 0xFF2E9C9C),
        k("michael", "Michael", "English US · male", false, false, 16, "en-us", 0xFF3A7BD5),
        k("fenrir", "Fenrir", "English US · deep male", false, false, 14, "en-us", 0xFF333333),
        k("adam", "Adam", "English US · male", false, false, 11, "en-us", 0xFF4F6D7A),
        k("emma", "Emma", "English UK · female", false, true, 21, "en-gb", 0xFFC2410C),
        k("george", "George", "English UK · male", false, false, 26, "en-gb", 0xFF1F8A5B),
        k("lewis", "Lewis", "English UK · male", false, false, 27, "en-gb", 0xFF7A5C3A),
        p("lessac", "Lessac", "English US · female · light", false, true, lessac, 0xFF6B7280)
    )

    fun voice(id: String) = voices.firstOrNull { it.id == id } ?: voices.first()

    fun preview(v: Voice) = if (v.hindi)
        "नमस्ते, मैं ${v.name} हूँ। आपकी कहानी, आपकी आवाज़ में।"
    else
        "Hi, I'm ${v.name}. Let's make your next video sound amazing."
}

enum class Style(val label: String, val speed: Float, val pause: Float) {
    Narrator("Narrator", 1.0f, 0.32f),
    Calm("Calm", 0.9f, 0.5f),
    Excited("Excited", 1.12f, 0.18f),
    News("News", 1.05f, 0.28f),
    Devotional("Devotional", 0.84f, 0.7f)
}
