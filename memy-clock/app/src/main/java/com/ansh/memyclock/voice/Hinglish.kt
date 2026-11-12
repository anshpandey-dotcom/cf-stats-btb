package com.ansh.memyclock.voice

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

object Hinglish {
    private val latin = Regex("[A-Za-z']+(?: +[A-Za-z']+)*")

    fun needsConversion(text: String) = text.any { it in 'a'..'z' || it in 'A'..'Z' }

    suspend fun toHindi(text: String): String = withContext(Dispatchers.IO) {
        if (!needsConversion(text)) return@withContext text
        val out = StringBuilder()
        var last = 0
        for (match in latin.findAll(text)) {
            out.append(text, last, match.range.first)
            out.append(convert(match.value) ?: match.value)
            last = match.range.last + 1
        }
        out.append(text, last, text.length)
        out.toString()
    }

    private fun convert(phrase: String): String? = runCatching {
        val query = URLEncoder.encode(phrase.lowercase(), "UTF-8")
        val url = "https://inputtools.google.com/request?text=$query&itc=hi-t-i0-und&num=1&cp=0&cs=1&ie=utf-8&oe=utf-8"
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 8_000
            readTimeout = 8_000
        }
        try {
            val body = connection.inputStream.bufferedReader().readText()
            val root = JSONArray(body)
            if (root.getString(0) != "SUCCESS") return@runCatching null
            root.getJSONArray(1).getJSONArray(0).getJSONArray(1).getString(0)
        } finally {
            connection.disconnect()
        }
    }.getOrNull()
}
