package com.ansh.recalm.tts

object NumberWords {

    private val hindiBelowHundred = listOf(
        "शून्य", "एक", "दो", "तीन", "चार", "पाँच", "छह", "सात", "आठ", "नौ",
        "दस", "ग्यारह", "बारह", "तेरह", "चौदह", "पंद्रह", "सोलह", "सत्रह", "अठारह", "उन्नीस",
        "बीस", "इक्कीस", "बाईस", "तेईस", "चौबीस", "पच्चीस", "छब्बीस", "सत्ताईस", "अट्ठाईस", "उनतीस",
        "तीस", "इकतीस", "बत्तीस", "तैंतीस", "चौंतीस", "पैंतीस", "छत्तीस", "सैंतीस", "अड़तीस", "उनतालीस",
        "चालीस", "इकतालीस", "बयालीस", "तैंतालीस", "चौवालीस", "पैंतालीस", "छियालीस", "सैंतालीस", "अड़तालीस", "उनचास",
        "पचास", "इक्यावन", "बावन", "तिरपन", "चौवन", "पचपन", "छप्पन", "सत्तावन", "अट्ठावन", "उनसठ",
        "साठ", "इकसठ", "बासठ", "तिरसठ", "चौंसठ", "पैंसठ", "छियासठ", "सड़सठ", "अड़सठ", "उनहत्तर",
        "सत्तर", "इकहत्तर", "बहत्तर", "तिहत्तर", "चौहत्तर", "पचहत्तर", "छिहत्तर", "सतहत्तर", "अठहत्तर", "उन्यासी",
        "अस्सी", "इक्यासी", "बयासी", "तिरासी", "चौरासी", "पचासी", "छियासी", "सत्तासी", "अट्ठासी", "नवासी",
        "नब्बे", "इक्यानबे", "बानबे", "तिरानबे", "चौरानबे", "पंचानबे", "छियानबे", "सत्तानबे", "अट्ठानबे", "निन्यानबे"
    )

    private val ones = listOf(
        "zero", "one", "two", "three", "four", "five", "six", "seven", "eight", "nine",
        "ten", "eleven", "twelve", "thirteen", "fourteen", "fifteen", "sixteen", "seventeen", "eighteen", "nineteen"
    )
    private val tens = listOf("", "", "twenty", "thirty", "forty", "fifty", "sixty", "seventy", "eighty", "ninety")

    fun hindi(value: Long): String {
        if (value < 0) return "माइनस " + hindi(-value)
        if (value < 100) return hindiBelowHundred[value.toInt()]
        val parts = mutableListOf<String>()
        var n = value
        val crore = n / 10_000_000
        n %= 10_000_000
        val lakh = n / 100_000
        n %= 100_000
        val thousand = n / 1000
        n %= 1000
        val hundred = n / 100
        n %= 100
        if (crore > 0) parts += hindi(crore) + " करोड़"
        if (lakh > 0) parts += hindiBelowHundred[lakh.toInt()] + " लाख"
        if (thousand > 0) parts += hindiBelowHundred[thousand.toInt()] + " हज़ार"
        if (hundred > 0) parts += hindiBelowHundred[hundred.toInt()] + " सौ"
        if (n > 0) parts += hindiBelowHundred[n.toInt()]
        return parts.joinToString(" ")
    }

    fun english(value: Long): String {
        if (value < 0) return "minus " + english(-value)
        if (value < 20) return ones[value.toInt()]
        if (value < 100) {
            val t = tens[(value / 10).toInt()]
            return if (value % 10 == 0L) t else t + " " + ones[(value % 10).toInt()]
        }
        if (value < 1000) {
            val rest = value % 100
            return ones[(value / 100).toInt()] + " hundred" + if (rest > 0) " " + english(rest) else ""
        }
        val scales = listOf(1_000_000_000L to "billion", 1_000_000L to "million", 1000L to "thousand")
        for ((size, name) in scales) {
            if (value >= size) {
                val rest = value % size
                return english(value / size) + " " + name + if (rest > 0) " " + english(rest) else ""
            }
        }
        return value.toString()
    }

    fun englishYear(value: Long): String {
        if (value in 1100..1999 || value in 2010..2099) {
            val high = value / 100
            val low = value % 100
            return when {
                low == 0L -> english(high) + " hundred"
                low < 10 -> english(high) + " oh " + english(low)
                else -> english(high) + " " + english(low)
            }
        }
        return english(value)
    }

    fun digitsHindi(text: String): String = text.map { if (it in '०'..'९') '0' + (it - '०') else it }.joinToString("")

    fun spell(number: String, hindi: Boolean): String {
        val clean = number.replace(",", "")
        val parts = clean.split('.')
        val whole = parts[0].toLongOrNull() ?: return number
        val head = if (hindi) hindi(whole) else if (parts.size == 1 && clean.length == 4) englishYear(whole) else english(whole)
        if (parts.size < 2 || parts[1].isEmpty()) return head
        val point = if (hindi) " दशमलव " else " point "
        val tail = parts[1].map { d ->
            val v = (d - '0').toLong()
            if (hindi) hindi(v) else english(v)
        }.joinToString(" ")
        return head + point + tail
    }
}
