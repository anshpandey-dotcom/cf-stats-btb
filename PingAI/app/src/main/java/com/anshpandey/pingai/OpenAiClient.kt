package com.anshpandey.pingai

import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

object OpenAiClient {

    fun ask(
        url: String,
        key: String,
        model: String,
        fallbacks: List<String>,
        system: String,
        history: List<Pair<String, String>>
    ): String {
        var failure: GeminiException? = null
        for (candidate in (listOf(model) + fallbacks).distinct()) {
            try {
                return call(url, key, candidate, system, history)
            } catch (e: GeminiException) {
                failure = e
                val msg = e.message.orEmpty()
                val modelProblem = msg.startsWith("404") || (msg.startsWith("400") && msg.contains("model", true))
                if (!modelProblem) throw e
            }
        }
        throw failure ?: GeminiException("No model available")
    }

    private fun call(url: String, key: String, model: String, system: String, history: List<Pair<String, String>>): String {
        val messages = JSONArray()
        messages.put(JSONObject().put("role", "system").put("content", system))
        history.forEach { (role, text) ->
            messages.put(JSONObject().put("role", if (role == "model") "assistant" else "user").put("content", text))
        }
        val body = JSONObject().put("model", model).put("messages", messages)
            .put("temperature", 0.8).put("max_tokens", 300)

        val conn = URL(url).openConnection() as HttpURLConnection
        try {
            conn.requestMethod = "POST"
            conn.connectTimeout = 15000
            conn.readTimeout = 30000
            conn.doOutput = true
            conn.setRequestProperty("Content-Type", "application/json")
            conn.setRequestProperty("Authorization", "Bearer $key")
            conn.setRequestProperty("X-Title", "PING AI")
            conn.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }

            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val raw = stream?.bufferedReader()?.use { it.readText() } ?: ""
            if (code !in 200..299) {
                val msg = runCatching { JSONObject(raw).getJSONObject("error").getString("message") }.getOrDefault(raw)
                throw GeminiException("$code ${msg.take(160)}")
            }
            val answer = JSONObject(raw).getJSONArray("choices").getJSONObject(0)
                .getJSONObject("message").getString("content").trim()
            if (answer.isEmpty()) throw GeminiException("empty response")
            return answer
        } finally {
            conn.disconnect()
        }
    }
}
