package com.fleetmc.flipshelf.translate

import org.json.JSONArray
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

class WebTranslator(private val target: String = "hi") : FallbackTranslator.OnlineEngine {

    class Result(val text: String, val source: String?)

    @Volatile
    override var detectedSource: String? = null
        private set

    override fun translate(text: String): String = request(text).text

    override fun request(text: String): Result {
        var lastError: Exception? = null
        for (attempt in 0 until ATTEMPTS) {
            try {
                val result = post(text)
                if (detectedSource == null && result.source != null) detectedSource = result.source
                return result
            } catch (e: IOException) {
                lastError = e
                if (e is RateLimited || attempt < ATTEMPTS - 1) Thread.sleep(BACKOFF[attempt.coerceAtMost(BACKOFF.size - 1)])
            }
        }
        throw lastError ?: IOException("Translation failed")
    }

    private class RateLimited(message: String) : IOException(message)

    private fun post(text: String): Result {
        val url = URL("https://translate.googleapis.com/translate_a/single?client=gtx&sl=auto&tl=$target&dt=t&ie=UTF-8&oe=UTF-8")
        val c = url.openConnection() as HttpURLConnection
        try {
            c.requestMethod = "POST"
            c.connectTimeout = 12000
            c.readTimeout = 25000
            c.doOutput = true
            c.setRequestProperty("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8")
            c.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36")
            c.outputStream.use { it.write(("q=" + URLEncoder.encode(text, "UTF-8")).toByteArray(Charsets.UTF_8)) }
            val code = c.responseCode
            if (code == 429) throw RateLimited("Too many requests")
            if (code != 200) throw IOException("HTTP $code")
            val body = c.inputStream.use { String(it.readBytes(), Charsets.UTF_8) }
            return parse(body)
        } finally {
            c.disconnect()
        }
    }

    companion object {
        private const val ATTEMPTS = 4
        private val BACKOFF = longArrayOf(600, 1500, 3500, 6000)

        fun parse(json: String): Result {
            val root = try {
                JSONArray(json)
            } catch (e: Exception) {
                throw IOException("Bad response")
            }
            val parts = root.optJSONArray(0) ?: throw IOException("Empty response")
            val sb = StringBuilder()
            for (i in 0 until parts.length()) {
                val piece = parts.optJSONArray(i) ?: continue
                sb.append(piece.optString(0, ""))
            }
            if (sb.isEmpty()) throw IOException("Empty translation")
            val source = if (root.length() > 2 && !root.isNull(2)) root.optString(2, "").ifEmpty { null } else null
            return Result(sb.toString().trim(), source)
        }
    }
}
