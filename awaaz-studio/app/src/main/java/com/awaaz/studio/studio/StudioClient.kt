package com.awaaz.studio.studio

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.DataOutputStream
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID

data class RemoteVoice(val id: String, val name: String)

class StudioClient(private val base: () -> String) {

    private fun url(path: String) = URL(base().trimEnd('/') + path)

    suspend fun ping(): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val c = url("/health").openConnection() as HttpURLConnection
            c.connectTimeout = 3000
            c.readTimeout = 3000
            val ok = c.responseCode == 200
            c.disconnect()
            ok
        }.getOrDefault(false)
    }

    suspend fun voices(): List<RemoteVoice> = withContext(Dispatchers.IO) {
        val c = url("/voices").openConnection() as HttpURLConnection
        val arr = JSONArray(c.inputStream.bufferedReader().readText())
        c.disconnect()
        List(arr.length()) { arr.getJSONObject(it).let { o -> RemoteVoice(o.getString("id"), o.getString("name")) } }
    }

    suspend fun clone(name: String, sample: File): RemoteVoice = withContext(Dispatchers.IO) {
        val o = JSONObject(multipart("/clone", mapOf("name" to name), mapOf("audio" to sample)).readText())
        RemoteVoice(o.getString("id"), o.getString("name"))
    }

    suspend fun speak(text: String, lang: String, voice: String, out: File) = withContext(Dispatchers.IO) {
        multipart("/tts", mapOf("text" to text, "lang" to lang, "voice" to voice), emptyMap(), out)
    }

    suspend fun convert(input: File, voice: String, out: File) = withContext(Dispatchers.IO) {
        multipart("/vc", mapOf("voice" to voice), mapOf("audio" to input), out)
    }

    suspend fun dub(input: File, target: String, voice: String, out: File) = withContext(Dispatchers.IO) {
        multipart("/dub", mapOf("target" to target, "voice" to voice), mapOf("media" to input), out)
    }

    suspend fun music(prompt: String, seconds: Int, out: File) = withContext(Dispatchers.IO) {
        multipart("/music", mapOf("prompt" to prompt, "seconds" to seconds.toString()), emptyMap(), out)
    }

    private fun multipart(path: String, fields: Map<String, String>, files: Map<String, File>, out: File? = null): File {
        val boundary = "----awaaz" + UUID.randomUUID().toString().replace("-", "")
        val c = url(path).openConnection() as HttpURLConnection
        c.doOutput = true
        c.requestMethod = "POST"
        c.connectTimeout = 8000
        c.readTimeout = 30 * 60 * 1000
        c.setChunkedStreamingMode(1 shl 16)
        c.setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
        DataOutputStream(c.outputStream).use { o ->
            for ((k, v) in fields) {
                o.writeBytes("--$boundary\r\nContent-Disposition: form-data; name=\"$k\"\r\n\r\n")
                o.write(v.toByteArray())
                o.writeBytes("\r\n")
            }
            for ((k, f) in files) {
                o.writeBytes("--$boundary\r\nContent-Disposition: form-data; name=\"$k\"; filename=\"${f.name}\"\r\nContent-Type: application/octet-stream\r\n\r\n")
                f.inputStream().use { it.copyTo(o) }
                o.writeBytes("\r\n")
            }
            o.writeBytes("--$boundary--\r\n")
        }
        val code = c.responseCode
        if (code !in 200..299) {
            val msg = c.errorStream?.bufferedReader()?.readText().orEmpty()
            c.disconnect()
            throw IllegalStateException(runCatching { JSONObject(msg).getString("detail") }.getOrDefault("Studio PC error ($code)"))
        }
        val target = out ?: File.createTempFile("studio", ".json")
        c.inputStream.use { input -> target.outputStream().use { input.copyTo(it) } }
        c.disconnect()
        return target
    }
}
