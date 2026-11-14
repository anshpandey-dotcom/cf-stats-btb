package com.fleetmc.kuchlikh.net

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

class Reply(val code: Int, val body: String, val retryAfter: Int)

object Net {

    private const val AGENT = "KuchLikh/1.1 (Android book writing app)"

    fun online(context: Context): Boolean {
        val manager = context.getSystemService(ConnectivityManager::class.java) ?: return false
        val network = manager.activeNetwork ?: return false
        val caps = manager.getNetworkCapabilities(network) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    private fun open(url: String, method: String): HttpURLConnection {
        var target = url
        repeat(5) {
            val connection = (URL(target).openConnection() as HttpURLConnection).apply {
                connectTimeout = 15000
                readTimeout = 60000
                instanceFollowRedirects = false
                requestMethod = method
                setRequestProperty("User-Agent", AGENT)
                setRequestProperty("Accept", "*/*")
            }
            if (method == "GET") {
                val code = connection.responseCode
                if (code in 301..308 && code != 304) {
                    val next = connection.getHeaderField("Location")
                    connection.disconnect()
                    if (next != null) {
                        target = URL(URL(target), next).toString()
                        return@repeat
                    }
                }
            }
            return connection
        }
        throw IOException("Too many redirects")
    }

    fun get(url: String, limit: Int = 25_000_000, attempt: Int = 0): ByteArray {
        val connection = open(url, "GET")
        try {
            val code = connection.responseCode
            if (code == 429 && attempt < 3) {
                val wait = connection.getHeaderField("retry-after")?.toIntOrNull() ?: 3
                connection.disconnect()
                Thread.sleep(wait.coerceIn(2, 8) * 1000L)
                return get(url, limit, attempt + 1)
            }
            if (code !in 200..299) throw IOException("HTTP $code")
            val out = ByteArrayOutputStream()
            val buffer = ByteArray(16384)
            connection.inputStream.use { input ->
                while (true) {
                    val read = input.read(buffer)
                    if (read < 0) break
                    out.write(buffer, 0, read)
                    if (out.size() > limit) throw IOException("File is too large")
                }
            }
            return out.toByteArray()
        } finally {
            connection.disconnect()
        }
    }

    fun text(url: String): String = get(url, 4_000_000).toString(Charsets.UTF_8)

    fun postJson(url: String, json: String, bearer: String): Reply {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15000
            readTimeout = 90000
            requestMethod = "POST"
            doOutput = true
            setRequestProperty("User-Agent", AGENT)
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("Authorization", "Bearer $bearer")
        }
        try {
            connection.outputStream.use { it.write(json.toByteArray()) }
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val body = stream?.use { it.readBytes().toString(Charsets.UTF_8) }.orEmpty()
            val wait = connection.getHeaderField("retry-after")?.toDoubleOrNull()?.toInt() ?: 0
            return Reply(code, body, wait)
        } finally {
            connection.disconnect()
        }
    }

    fun getJson(url: String, bearer: String): Reply {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15000
            readTimeout = 30000
            setRequestProperty("User-Agent", AGENT)
            setRequestProperty("Authorization", "Bearer $bearer")
        }
        try {
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            return Reply(code, stream?.use { it.readBytes().toString(Charsets.UTF_8) }.orEmpty(), 0)
        } finally {
            connection.disconnect()
        }
    }
}
