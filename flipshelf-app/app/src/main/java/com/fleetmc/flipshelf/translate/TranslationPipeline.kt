package com.fleetmc.flipshelf.translate

import java.util.concurrent.Callable
import java.util.concurrent.ExecutionException
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.atomic.AtomicLong

object TranslationPipeline {

    private val letters = Regex("\\p{L}")
    private val sentenceSplit = Regex("(?<=[.!?।])\\s+")
    private const val SEGMENT = 1500

    class Cancelled : Exception()

    fun segments(text: String): List<String> {
        if (text.length <= SEGMENT + 300) return listOf(text)
        val out = ArrayList<String>()
        val current = StringBuilder()
        for (sentence in text.split(sentenceSplit)) {
            if (current.isNotEmpty() && current.length + sentence.length + 1 > SEGMENT) {
                out.add(current.toString())
                current.setLength(0)
            }
            if (sentence.length > SEGMENT) {
                var from = 0
                while (from < sentence.length) {
                    val to = minOf(sentence.length, from + SEGMENT)
                    out.add(sentence.substring(from, to))
                    from = to
                }
            } else {
                if (current.isNotEmpty()) current.append(' ')
                current.append(sentence)
            }
        }
        if (current.isNotEmpty()) out.add(current.toString())
        return out
    }

    fun run(
        chapters: List<Chapter>,
        translate: (String) -> String,
        onProgress: (Int) -> Unit,
        isCancelled: () -> Boolean,
        parallelism: Int = 4
    ): List<Chapter> {
        val total = chapters.sumOf { c -> c.blocks.sumOf { it.text.length } + (c.heading?.length ?: 0) }.coerceAtLeast(1)
        val done = AtomicLong(0)
        val lastReported = AtomicLong(-1)
        val pool = Executors.newFixedThreadPool(parallelism.coerceAtLeast(1))

        fun one(text: String): String {
            if (isCancelled()) throw Cancelled()
            val result = if (letters.containsMatchIn(text)) segments(text).joinToString(" ") { translate(it) } else text
            val percent = (done.addAndGet(text.length.toLong()) * 100L / total).toInt().coerceIn(0, 100)
            val previous = lastReported.getAndSet(percent.toLong())
            if (previous != percent.toLong()) onProgress(percent)
            return result
        }

        try {
            val headings = chapters.map { c -> c.heading?.let { h -> pool.submit(Callable { one(h) }) } }
            val blocks = chapters.map { c -> c.blocks.map { b -> pool.submit(Callable { one(b.text) }) } }
            fun <T> get(f: Future<T>): T = try {
                f.get()
            } catch (e: ExecutionException) {
                throw e.cause ?: e
            }
            return chapters.mapIndexed { i, c ->
                Chapter(headings[i]?.let { get(it) }, c.blocks.mapIndexed { j, b -> Block(get(blocks[i][j]), b.level) })
            }
        } finally {
            pool.shutdownNow()
        }
    }
}
