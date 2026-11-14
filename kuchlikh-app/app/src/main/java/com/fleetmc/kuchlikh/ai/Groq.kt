package com.fleetmc.kuchlikh.ai

import android.content.Context
import com.fleetmc.kuchlikh.net.Net
import com.fleetmc.kuchlikh.text.Tidy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.io.IOException

enum class AiTask(val title: String, val detail: String, val temperature: Double) {
    Fix("Fix grammar and punctuation", "Spelling, tenses, capitals, commas and full stops. Your wording stays.", 0.2),
    Polish("Polish like a writer", "Smoother rhythm and sharper words. Same voice, same story.", 0.5),
    Hook("Make it gripping", "Stronger openings and vivid details so readers keep turning pages.", 0.8),
    ToEnglish("Hindi or Hinglish to English", "Natural English, not word for word.", 0.3),
    ToHindi("English or Hinglish to Hindi", "Clean Devanagari Hindi with proper spelling and punctuation.", 0.3),
}

class AiException(message: String) : Exception(message)

private class ModelUnavailable : Exception()

object Groq {

    private const val ENDPOINT = "https://api.groq.com/openai/v1/chat/completions"
    private const val CHUNK = 1800

    private val models = listOf("qwen/qwen3.8-27b" to "none", "openai/gpt-oss-120b" to "low")
    private val json = Json { ignoreUnknownKeys = true }

    private const val RULES = """You are a senior book editor and literary translator working on a manuscript by a real author.
Write like a skilled human author. Keep the author's voice, meaning, facts, names and the number of paragraphs.
Never add notes, titles, quotes or explanations.
Never use em dashes or en dashes. Use commas, full stops or colons instead.
Avoid words and habits that sound machine-written: delve, tapestry, testament, realm, symphony, beacon, "in a world where", "little did", "it is not just X, it is Y", empty adjectives and stacked adverbs.
No emojis, no markdown, no bullet points.
Punctuation must be natural and correct: commas, full stops, question marks, exclamation marks, colons, semicolons and quotation marks. In Hindi end sentences with the purna viram । and never with | or a plain full stop. Use standard Devanagari spelling with correct nukta, anusvara and chandrabindu.
The text may begin or end in the middle of a sentence. Never complete, invent or remove such fragments.
Reply with JSON only, in the form {"paragraphs": ["...", "..."]}, with exactly as many items as the input, in the same order."""

    private fun instruction(task: AiTask): String = when (task) {
        AiTask.Fix -> "Task: correct grammar, spelling, capitalisation, spacing and punctuation. Change as little as possible and keep the wording, the language and the script exactly as they are."
        AiTask.Polish -> "Task: lightly rewrite so it reads like polished published prose. Fix errors, tighten clumsy sentences, improve rhythm and word choice. Stay very close to the original, keep the same language and script, keep the length within 15 percent."
        AiTask.Hook -> "Task: make this gripping and easy to keep reading. Sharpen the opening line, use concrete sensory detail, vary sentence length, cut filler, keep dialogue natural. Keep every event, fact and character exactly the same, keep the same language and script, and keep the length within 25 percent. It must still sound like the same author, not like an AI."
        AiTask.ToEnglish -> "Task: translate into natural, fluent English the way a skilled human translator would, keeping tone, humour and the meaning of idioms. The input may be Hindi in Devanagari, Hinglish in Latin letters, or a mix."
        AiTask.ToHindi -> "Task: write this as natural, literary but easily readable Hindi in Devanagari script. The input may be English, Hinglish in Latin letters, or a mix. Keep names transliterated, avoid heavy Sanskrit words unless the original is formal."
    }

    fun key(context: Context): String = KeyVault(context).load()?.takeIf { it.isNotBlank() } ?: Secrets.builtIn()

    fun ready(context: Context): Boolean = key(context).isNotBlank()

    suspend fun run(
        context: Context,
        task: AiTask,
        paragraphs: List<String>,
        onProgress: (Int, Int) -> Unit = { _, _ -> },
    ): List<String> = withContext(Dispatchers.IO) {
        val key = key(context)
        if (key.isBlank()) throw AiException("No AI key is set up. Add one in Settings.")
        if (!Net.online(context)) throw AiException("You are offline.")
        val chunks = chunk(paragraphs)
        val out = ArrayList<String>(paragraphs.size)
        chunks.forEachIndexed { n, part ->
            currentCoroutineContext().ensureActive()
            onProgress(n, chunks.size)
            out += rewrite(key, task, part).map { Tidy.clean(it) }
        }
        onProgress(chunks.size, chunks.size)
        out
    }

    suspend fun test(context: Context): String = withContext(Dispatchers.IO) {
        val key = key(context)
        if (key.isBlank()) return@withContext "No key is set up."
        if (!Net.online(context)) return@withContext "You are offline."
        val reply = runCatching { Net.getJson("https://api.groq.com/openai/v1/models", key) }.getOrNull()
            ?: return@withContext "Couldn't reach the AI service."
        when (reply.code) {
            200 -> "Connected. The AI is ready."
            401, 403 -> "The key was rejected."
            else -> "The AI service answered with an error (${reply.code})."
        }
    }

    fun chunk(paragraphs: List<String>): List<List<String>> {
        val out = ArrayList<List<String>>()
        var current = ArrayList<String>()
        var size = 0
        for (paragraph in paragraphs) {
            if (current.isNotEmpty() && size + paragraph.length > CHUNK) {
                out += current
                current = ArrayList()
                size = 0
            }
            current += paragraph
            size += paragraph.length
        }
        if (current.isNotEmpty()) out += current
        return out
    }

    private fun rewrite(key: String, task: AiTask, part: List<String>): List<String> {
        var last: List<String> = emptyList()
        repeat(2) {
            last = request(key, task, part)
            if (last.size == part.size) return last
        }
        if (part.size == 1) return listOf(last.joinToString(" "))
        return part.flatMap { rewrite(key, task, listOf(it)) }
    }

    private fun request(key: String, task: AiTask, part: List<String>): List<String> {
        var failure: AiException? = null
        for ((model, effort) in models) {
            try {
                return call(key, model, effort, task, part)
            } catch (e: ModelUnavailable) {
                failure = AiException("The AI model is not available right now.")
            }
        }
        throw failure ?: AiException("The AI is not available right now.")
    }

    private fun call(key: String, model: String, effort: String, task: AiTask, part: List<String>): List<String> {
        val input = buildJsonObject {
            put("paragraphs", buildJsonArray { part.forEach { add(JsonPrimitive(it)) } })
        }
        val body = buildJsonObject {
            put("model", model)
            put("temperature", task.temperature)
            put("reasoning_effort", effort)
            put("response_format", buildJsonObject { put("type", "json_object") })
            put("messages", buildJsonArray {
                add(buildJsonObject { put("role", "system"); put("content", RULES) })
                add(buildJsonObject { put("role", "user"); put("content", instruction(task) + "\n\n" + input.toString()) })
            })
        }.toString()
        var attempt = 0
        while (true) {
            val reply = try {
                Net.postJson(ENDPOINT, body, key)
            } catch (e: IOException) {
                throw AiException("Couldn't reach the AI. Check your connection.")
            }
            when {
                reply.code in 200..299 -> return parse(reply.body)
                reply.code == 401 || reply.code == 403 -> throw AiException("The AI key was rejected. Check it in Settings.")
                reply.code == 429 && attempt < 3 -> {
                    Thread.sleep(reply.retryAfter.coerceIn(2, 20) * 1000L)
                    attempt++
                }
                reply.code == 429 -> throw AiException("The AI is busy right now. Try again in a minute.")
                reply.code == 404 || (reply.code == 400 && reply.body.contains("model", ignoreCase = true)) -> throw ModelUnavailable()
                else -> throw AiException("The AI returned an error (${reply.code}).")
            }
        }
    }

    fun parse(body: String): List<String> {
        val content = runCatching {
            json.parseToJsonElement(body).jsonObject["choices"]!!.jsonArray[0].jsonObject["message"]!!
                .jsonObject["content"]!!.jsonPrimitive.content
        }.getOrNull() ?: throw AiException("The AI reply could not be read.")
        val trimmed = content.trim().removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
        val root = runCatching { json.parseToJsonElement(trimmed) }.getOrNull() as? JsonObject
            ?: throw AiException("The AI reply could not be read.")
        val list = root["paragraphs"] as? JsonArray ?: throw AiException("The AI reply could not be read.")
        return list.map { (it as? JsonPrimitive)?.contentOrNull ?: it.toString() }
    }
}
