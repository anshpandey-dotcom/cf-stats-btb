package com.ansh.recalm

import com.ansh.recalm.data.Sentences
import com.ansh.recalm.importer.TextCleaner
import com.ansh.recalm.tts.EnglishG2p
import com.ansh.recalm.tts.HindiG2p
import com.ansh.recalm.tts.NumberWords
import com.ansh.recalm.tts.Phonemizer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.text.Normalizer

class EngineTest {

    private val fixtures = System.getProperty("recalm.fixtures").orEmpty()

    @Test
    fun hindiMatchesEspeakReference() {
        val ref = File(fixtures, "ref.tsv")
        if (!ref.exists()) return
        var total = 0
        var exact = 0
        val misses = mutableListOf<String>()
        ref.forEachLine { line ->
            val (word, expected) = line.split('\t')
            fun norm(s: String) = Normalizer.normalize(
                s.replace("ˈ", "").replace("ˌ", "").replace("ʌ", "ə").replace("r.", "ɽ").replace("ʲ", "").replace("r", "ɾ"),
                Normalizer.Form.NFD
            )
            total++
            val got = HindiG2p.phonemize(word)
            if (norm(got) == norm(expected)) exact++ else if (misses.size < 15) misses += "$word  $got  $expected"
        }
        println("Hindi G2P: $exact / $total = ${exact * 100 / total}%")
        misses.forEach { println(it) }
        assertTrue(exact * 100 / total >= 88)
    }

    @Test
    fun numbersReadNaturally() {
        assertEquals("एक सौ पच्चीस", NumberWords.hindi(125))
        assertEquals("दो लाख पचास हज़ार", NumberWords.hindi(250000))
        assertEquals("nineteen forty seven", NumberWords.spell("1947", false))
        assertEquals("three point one four", NumberWords.spell("3.14", false))
    }

    @Test
    fun sentencesSplitInBothScripts() {
        val text = "यह पहला वाक्य है। Fig. 2.1 shows the Sun. क्या आप तैयार हैं? Yes!"
        val parts = Sentences.split(text).map { text.substring(it.first, it.last + 1) }
        assertEquals(listOf("यह पहला वाक्य है।", "Fig. 2.1 shows the Sun.", "क्या आप तैयार हैं?", "Yes!"), parts)
    }

    @Test
    fun visualOrderPdfTextIsRepaired() {
        assertEquals("किताब", TextCleaner.fixDevanagariOrder("िकताब"))
        assertEquals("कर्म", TextCleaner.fixDevanagariOrder("कमर्"))
        assertEquals("पिता जी किताब", TextCleaner.fixDevanagariOrder("िपता जी िकताब"))
    }

    @Test
    fun runningHeadersAndQuestionsAreDetected() {
        val raw = (1..8).map { n ->
            "Science Class X\nChapter body line one for page $n continues here.\nMore body text on this page.\n$n"
        }
        val pages = TextCleaner.buildPages(raw)
        assertTrue(pages.all { p -> p.blocks.none { it.text.contains("Science Class X") } })
        val exercise = TextCleaner.buildPages(listOf("1.2 Light\nLight travels fast.\n\nEXERCISES\n\n1. What is light?\n\n2. Define reflection."))
        val kinds = exercise.first().blocks.map { it.kind }
        assertTrue(kinds.contains(3))
    }

    @Test
    fun mixedSentencePhonemizes() {
        val lexicon = File(fixtures, "lexicon-us-en.txt")
        val tokens = File(fixtures, "tokens.txt")
        if (!lexicon.exists() || !tokens.exists()) return
        val vocab = tokens.readLines().mapNotNull { l -> l.lastIndexOf(' ').takeIf { it >= 0 }?.let { l.substring(0, it).ifEmpty { " " } } }.toSet()
        val p = Phonemizer(EnglishG2p(lexicon), vocab)
        val out = File(fixtures, "kotlin_tokens.txt")
        val lines = listOf(
            "रात धीरे-धीरे गहरी हो रही है। आसमान में तारे चमक रहे हैं, और हवा में एक हल्की सी ठंडक है।",
            "आज हम Chapter 3 पढ़ेंगे, जिसका नाम है The Solar System. हमारा Sun एक medium size का star है।",
            "अपनी आँखें बंद कीजिए, और बस इस कहानी को सुनते रहिए। Good night."
        )
        out.writeText(lines.joinToString("\n") { line ->
            p.phonemize(line).joinToString("|") { chunk -> chunk.tokens.joinToString("") }
        })
        val chunks = p.phonemize(lines[1])
        assertTrue(chunks.isNotEmpty())
        val spans = chunks.flatMap { it.words }
        assertTrue(spans.any { lines[1].substring(it.start, it.end) == "Chapter" })
        println(out.readText())
        val piperTokens = File(fixtures, "piper_tokens.txt")
        if (piperTokens.exists()) {
            val piperVocab = piperTokens.readLines().mapNotNull { l -> l.lastIndexOf(' ').takeIf { it >= 0 }?.let { l.substring(0, it).ifEmpty { " " } } }.toSet()
            val map = listOf("A" to "eɪ", "I" to "aɪ", "O" to "oʊ", "W" to "aʊ", "Y" to "ɔɪ", "ʤ" to "dʒ", "ʧ" to "tʃ", "ᵊ" to "ə")
            val piper = Phonemizer(EnglishG2p(lexicon), piperVocab) { ph -> map.fold(ph) { a, (f, t) -> a.replace(f, t) } }
            File(fixtures, "kotlin_piper_tokens.txt").writeText(lines.joinToString("\n") { line ->
                piper.phonemize(line).joinToString("|") { chunk -> chunk.tokens.joinToString("") }
            })
        }
    }
}
