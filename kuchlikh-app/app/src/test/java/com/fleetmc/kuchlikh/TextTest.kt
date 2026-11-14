package com.fleetmc.kuchlikh

import com.fleetmc.kuchlikh.text.Autocorrect
import com.fleetmc.kuchlikh.text.Transliterator
import com.fleetmc.kuchlikh.text.TypingAssist
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TextTest {

    @Test
    fun hinglishRules() {
        Transliterator.setLexicon(emptyMap())
        val cases = mapOf(
            "mera naam" to "मेरा नाम",
            "kya" to "क्या",
            "hindi" to "हिंदी",
            "andar" to "अंदर",
            "pyar" to "प्यार",
            "dekhta" to "देखता",
            "sunta" to "सुनता",
            "likhenge" to "लिखेंगे",
            "jaunga" to "जाऊँगा",
            "khaya" to "खाया",
            "bolo" to "बोलो",
            "dard" to "दर्द",
            "pakka" to "पक्का",
            "rang" to "रंग",
            "prem" to "प्रेम",
            "baaton" to "बातों",
            "mitra" to "मित्र",
            "lamba" to "लंबा",
            "USA" to "USA",
        )
        cases.forEach { (roman, expected) -> assertEquals(roman, expected, Transliterator.text(roman)) }
    }

    @Test
    fun sentence() {
        Transliterator.setLexicon(emptyMap())
        assertEquals("मैं आज घर जा रहा हूँ।", Transliterator.text("main aaj ghar ja raha hoon."))
        assertEquals("तुम कहाँ हो?", Transliterator.text("tum kahan ho?"))
    }

    @Test
    fun typingHindi() {
        Transliterator.setLexicon(emptyMap())
        val typed = TypingAssist.apply("mera", "mera ", 5, hindi = true, autocorrect = true, startOfFlow = true)!!
        assertEquals("मेरा ", typed.text)
        assertEquals(5, typed.cursor)
        val stop = TypingAssist.apply("मेरा घर", "मेरा घर.", 8, hindi = true, autocorrect = true, startOfFlow = true)!!
        assertEquals("मेरा घर।", stop.text)
    }

    @Test
    fun typingEnglish() {
        val first = TypingAssist.apply("hello", "hello ", 6, hindi = false, autocorrect = true, startOfFlow = true)!!
        assertEquals("Hello ", first.text)
        val contraction = TypingAssist.apply("Well i dont", "Well i dont ", 12, false, true, true)!!
        assertEquals("Well i don't ", contraction.text)
        val pronoun = TypingAssist.apply("Then i", "Then i ", 7, false, true, true)!!
        assertEquals("Then I ", pronoun.text)
        val article = TypingAssist.apply("It was a apple", "It was a apple ", 15, false, true, true)!!
        assertEquals("It was an apple ", article.text)
        assertEquals(16, article.cursor)
        val space = TypingAssist.apply("Hello ", "Hello ,", 7, false, true, true)!!
        assertEquals("Hello,", space.text)
        assertNull(TypingAssist.apply("Fine", "Fine ", 5, false, true, true))
        assertNull(TypingAssist.apply("hello", "hello ", 6, false, false, true))
    }

    @Test
    fun fixText() {
        val fixed = Autocorrect.fixText("i went to  the the market .it was a old shop,and i didnt buy anything", true)
        assertEquals("I went to the market. It was an old shop, and I didn't buy anything", fixed.text)
    }
}
