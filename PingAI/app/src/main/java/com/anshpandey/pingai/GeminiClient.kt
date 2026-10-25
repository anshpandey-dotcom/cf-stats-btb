package com.anshpandey.pingai

import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

class GeminiException(message: String) : Exception(message)

object GeminiClient {

    private val fallbackModels = listOf("gemini-flash-latest", "gemini-2.5-flash", "gemini-2.0-flash")

    fun ask(apiKey: String, model: String, system: String, history: List<Pair<String, String>>): String {
        var failure: GeminiException? = null
        for (candidate in (listOf(model) + fallbackModels).distinct()) {
            try {
                return askModel(apiKey, candidate, system, history)
            } catch (e: GeminiException) {
                failure = e
                if (e.message?.startsWith("404") != true) throw e
            }
        }
        throw failure ?: GeminiException("No model available")
    }

    private fun askModel(apiKey: String, model: String, system: String, history: List<Pair<String, String>>): String {
        return try {
            call(apiKey, model, system, history, true)
        } catch (e: GeminiException) {
            if (e.message?.startsWith("400") == true) call(apiKey, model, system, history, false) else throw e
        }
    }

    private fun call(
        apiKey: String,
        model: String,
        system: String,
        history: List<Pair<String, String>>,
        withThinkingOff: Boolean
    ): String {
        val contents = JSONArray()
        var lastRole = ""
        history.forEach { (role, text) ->
            if (role == lastRole && contents.length() > 0) {
                val parts = contents.getJSONObject(contents.length() - 1).getJSONArray("parts")
                val old = parts.getJSONObject(0)
                old.put("text", old.getString("text") + "\n" + text)
            } else {
                contents.put(
                    JSONObject().put("role", role)
                        .put("parts", JSONArray().put(JSONObject().put("text", text)))
                )
                lastRole = role
            }
        }

        val config = JSONObject().put("temperature", 0.8).put("maxOutputTokens", 700)
        if (withThinkingOff && model.contains("flash")) {
            config.put("thinkingConfig", JSONObject().put("thinkingBudget", 0))
        }

        val body = JSONObject()
            .put("systemInstruction", JSONObject().put("parts", JSONArray().put(JSONObject().put("text", system))))
            .put("contents", contents)
            .put("generationConfig", config)

        val url = URL("https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent")
        val conn = url.openConnection() as HttpURLConnection
        try {
            conn.requestMethod = "POST"
            conn.connectTimeout = 15000
            conn.readTimeout = 30000
            conn.doOutput = true
            conn.setRequestProperty("Content-Type", "application/json")
            conn.setRequestProperty("x-goog-api-key", apiKey)
            conn.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }

            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val raw = stream?.bufferedReader()?.use { it.readText() } ?: ""
            if (code !in 200..299) {
                val msg = runCatching { JSONObject(raw).getJSONObject("error").getString("message") }.getOrDefault(raw)
                throw GeminiException("$code ${msg.take(160)}")
            }

            val parts = JSONObject(raw).getJSONArray("candidates").getJSONObject(0)
                .getJSONObject("content").getJSONArray("parts")
            val sb = StringBuilder()
            for (i in 0 until parts.length()) sb.append(parts.getJSONObject(i).optString("text"))
            val answer = sb.toString().trim()
            if (answer.isEmpty()) throw GeminiException("empty response")
            return answer
        } finally {
            conn.disconnect()
        }
    }
}
