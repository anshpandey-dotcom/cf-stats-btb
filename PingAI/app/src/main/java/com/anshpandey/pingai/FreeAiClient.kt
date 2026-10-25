package com.anshpandey.pingai

import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

object FreeAiClient {

    fun ask(system: String, history: List<Pair<String, String>>): String {
        val messages = JSONArray()
        messages.put(JSONObject().put("role", "system").put("content", system))
        history.forEach { (role, text) ->
            val mapped = if (role == "model") "assistant" else "user"
            messages.put(JSONObject().put("role", mapped).put("content", text))
        }
        val body = JSONObject().put("model", "openai").put("messages", messages).put("private", true)

        val conn = URL("https://text.pollinations.ai/openai").openConnection() as HttpURLConnection
        try {
            conn.requestMethod = "POST"
            conn.connectTimeout = 15000
            conn.readTimeout = 45000
            conn.doOutput = true
            conn.setRequestProperty("Content-Type", "application/json")
            conn.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }

            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val raw = stream?.bufferedReader()?.use { it.readText() } ?: ""
            if (code !in 200..299) throw GeminiException("Free AI error $code ${raw.take(120)}")

            val answer = JSONObject(raw).getJSONArray("choices").getJSONObject(0)
                .getJSONObject("message").getString("content").trim()
            if (answer.isEmpty()) throw GeminiException("Free AI returned an empty reply")
            return answer
        } finally {
            conn.disconnect()
        }
    }
}
