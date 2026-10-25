package com.anshpandey.pingai

import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

class AiException(message: String) : Exception(message)

class AiMeta(val limit: String?, val remaining: String?, val tokens: Int, val model: String)

object OpenAiClient {

    fun listModels(url: String, key: String): List<String> {
        val conn = URL(url).openConnection() as HttpURLConnection
        try {
            conn.connectTimeout = 15000
            conn.readTimeout = 20000
            conn.setRequestProperty("Authorization", "Bearer $key")
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val raw = stream?.bufferedReader()?.use { it.readText() } ?: ""
            if (code !in 200..299) throw AiException("$code ${raw.take(120)}")
            val data = JSONObject(raw).getJSONArray("data")
            val out = mutableListOf<String>()
            for (i in 0 until data.length()) {
                val o = data.getJSONObject(i)
                if (o.optBoolean("active", true)) out.add(o.getString("id"))
            }
            return out
        } finally {
            conn.disconnect()
        }
    }

    fun ask(
        url: String,
        key: String,
        models: List<String>,
        system: String,
        history: List<Pair<String, String>>,
        maxTokens: Int,
        meta: ((AiMeta) -> Unit)?
    ): String {
        var failure: AiException? = null
        for (candidate in models.distinct()) {
            try {
                return call(url, key, candidate, system, history, maxTokens, meta)
            } catch (e: AiException) {
                failure = e
                val code = e.message.orEmpty().take(3)
                if (code == "401" || code == "403") throw e
            } catch (e: java.io.IOException) {
                failure = AiException("network error: ${e.message}")
            }
        }
        throw failure ?: AiException("No model available")
    }

    private fun call(
        url: String,
        key: String,
        model: String,
        system: String,
        history: List<Pair<String, String>>,
        maxTokens: Int,
        meta: ((AiMeta) -> Unit)?
    ): String {
        val messages = JSONArray()
        messages.put(JSONObject().put("role", "system").put("content", system))
        history.forEach { (role, text) ->
            messages.put(JSONObject().put("role", if (role == "model") "assistant" else "user").put("content", text))
        }
        val reasoning = model.contains("gpt-oss")
        val body = JSONObject().put("model", model).put("messages", messages)
            .put("temperature", 0.8).put("max_tokens", if (reasoning) maxTokens + 250 else maxTokens)
        if (reasoning) body.put("reasoning_effort", "low")

        val conn = URL(url).openConnection() as HttpURLConnection
        try {
            conn.requestMethod = "POST"
            conn.connectTimeout = 15000
            conn.readTimeout = 30000
            conn.doOutput = true
            conn.setRequestProperty("Content-Type", "application/json")
            conn.setRequestProperty("Authorization", "Bearer $key")
            conn.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }

            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val raw = stream?.bufferedReader()?.use { it.readText() } ?: ""
            val tokens = runCatching { JSONObject(raw).getJSONObject("usage").getInt("total_tokens") }.getOrDefault(0)
            meta?.invoke(
                AiMeta(
                    conn.getHeaderField("x-ratelimit-limit-requests"),
                    conn.getHeaderField("x-ratelimit-remaining-requests"),
                    tokens,
                    model
                )
            )
            if (code !in 200..299) {
                val msg = runCatching { JSONObject(raw).getJSONObject("error").getString("message") }.getOrDefault(raw)
                throw AiException("$code ${msg.take(160)}")
            }
            val answer = JSONObject(raw).getJSONArray("choices").getJSONObject(0)
                .getJSONObject("message").optString("content", "").trim()
            if (answer.isEmpty()) throw AiException("empty response")
            return answer
        } finally {
            conn.disconnect()
        }
    }
}
