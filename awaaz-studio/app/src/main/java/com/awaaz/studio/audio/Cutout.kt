package com.awaaz.studio.audio

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

data class CutoutSettings(
    val silence: Float = 0.7f,
    val noise: Float = 0.5f,
    val humanize: Float = 0.4f,
    val voiceEq: Boolean = true,
    val room: Boolean = false,
    val targetLufs: Float = -14f
)

data class CutoutReport(val before: Float, val after: Float, val lufsBefore: Float, val lufsAfter: Float)

private class Biquad(private val b0: Double, private val b1: Double, private val b2: Double, private val a1: Double, private val a2: Double) {
    private var x1 = 0.0
    private var x2 = 0.0
    private var y1 = 0.0
    private var y2 = 0.0

    fun run(x: Float): Float {
        val y = b0 * x + b1 * x1 + b2 * x2 - a1 * y1 - a2 * y2
        x2 = x1; x1 = x.toDouble(); y2 = y1; y1 = y
        return y.toFloat()
    }

    fun run(data: FloatArray) { for (i in data.indices) data[i] = run(data[i]) }

    companion object {
        private fun make(b0: Double, b1: Double, b2: Double, a0: Double, a1: Double, a2: Double) =
            Biquad(b0 / a0, b1 / a0, b2 / a0, a1 / a0, a2 / a0)

        fun highPass(rate: Int, f: Double, q: Double = 0.707): Biquad {
            val w = 2 * PI * f / rate
            val al = sin(w) / (2 * q)
            val c = cos(w)
            return make((1 + c) / 2, -(1 + c), (1 + c) / 2, 1 + al, -2 * c, 1 - al)
        }

        fun peak(rate: Int, f: Double, q: Double, db: Double): Biquad {
            val a = 10.0.pow(db / 40)
            val w = 2 * PI * f / rate
            val al = sin(w) / (2 * q)
            val c = cos(w)
            return make(1 + al * a, -2 * c, 1 - al * a, 1 + al / a, -2 * c, 1 - al / a)
        }

        fun highShelf(rate: Int, f: Double, db: Double): Biquad {
            val a = 10.0.pow(db / 40)
            val w = 2 * PI * f / rate
            val c = cos(w)
            val al = sin(w) / 2 * sqrt(2.0)
            val s = 2 * sqrt(a) * al
            return make(
                a * ((a + 1) + (a - 1) * c + s), -2 * a * ((a - 1) + (a + 1) * c), a * ((a + 1) + (a - 1) * c - s),
                (a + 1) - (a - 1) * c + s, 2 * ((a - 1) - (a + 1) * c), (a + 1) - (a - 1) * c - s
            )
        }
    }
}

object Cutout {

    fun process(input: Pcm, s: CutoutSettings): Pair<Pcm, CutoutReport> {
        val rate = input.rate
        val lufsIn = loudness(input.samples, rate)
        var x = input.samples.copyOf()
        Biquad.highPass(rate, 80.0).run(x)
        val frame = rate / 100
        val levels = frameDb(x, frame)
        val floor = percentile(levels, 0.1f)
        if (s.noise > 0f) gate(x, levels, frame, floor + 6f, s.noise * 18f)
        if (s.silence > 0f) x = trimSilence(x, levels, frame, rate, max(floor + 10f, -50f), s.silence)
        if (s.voiceEq) {
            Biquad.peak(rate, 200.0, 1.0, -2.0).run(x)
            Biquad.peak(rate, 3200.0, 1.2, 3.0).run(x)
            if (rate > 20000) Biquad.highShelf(rate, 9000.0, 1.5).run(x)
        }
        compress(x, rate, -20f, 3f)
        if (s.humanize > 0f) humanize(x, rate, s.humanize)
        if (s.room) x = room(x, rate, 0.09f)
        val gain = 10f.pow((s.targetLufs - loudness(x, rate)) / 20f)
        for (i in x.indices) x[i] *= gain
        limit(x, rate, 10f.pow(-1.5f / 20f))
        val out = Pcm(x, rate)
        return out to CutoutReport(input.seconds, out.seconds, lufsIn, loudness(x, rate))
    }

    private fun frameDb(x: FloatArray, frame: Int): FloatArray {
        val n = x.size / frame
        return FloatArray(n) { f ->
            var acc = 0.0
            for (i in f * frame until (f + 1) * frame) acc += x[i] * x[i]
            (10 * log10(acc / frame + 1e-12)).toFloat()
        }
    }

    private fun percentile(v: FloatArray, p: Float): Float {
        if (v.isEmpty()) return -90f
        val s = v.sorted()
        return s[((s.size - 1) * p).toInt()]
    }

    private fun gate(x: FloatArray, levels: FloatArray, frame: Int, threshold: Float, depthDb: Float) {
        val low = 10f.pow(-depthDb / 20f)
        var g = 1f
        for (f in levels.indices) {
            val target = if (levels[f] < threshold) low else 1f
            for (i in f * frame until (f + 1) * frame) {
                g += (target - g) * (if (target > g) 0.02f else 0.002f)
                x[i] *= g
            }
        }
    }

    private fun trimSilence(x: FloatArray, levels: FloatArray, frame: Int, rate: Int, threshold: Float, amount: Float): FloatArray {
        val keep = ((0.9f - amount * 0.65f) * rate).toInt()
        val edge = (0.15f * rate).toInt()
        val fade = rate / 50
        val out = FloatList()
        val n = levels.size
        var first = 0
        while (first < n && levels[first] < threshold) first++
        var last = n - 1
        while (last > first && levels[last] < threshold) last--
        if (first >= n) return x
        val start = max(0, first * frame - edge)
        val end = min(x.size, (last + 1) * frame + edge)
        var i = start
        while (i < end) {
            val fi = i / frame
            if (fi < n && levels[fi] < threshold) {
                var j = fi
                while (j < n && levels[j] < threshold) j++
                val runStart = i
                val runEnd = min(j * frame, end)
                val len = runEnd - runStart
                if (len > keep) {
                    val half = keep / 2
                    val a = x.copyOfRange(runStart, runStart + half)
                    val b = x.copyOfRange(runEnd - half, runEnd)
                    for (k in 0 until min(fade, half)) {
                        a[half - 1 - k] *= k.toFloat() / fade
                        b[k] *= k.toFloat() / fade
                    }
                    out.add(a); out.add(b)
                } else {
                    out.add(x.copyOfRange(runStart, runEnd))
                }
                i = runEnd
            } else {
                val stop = min(end, (fi + 1) * frame)
                out.add(x.copyOfRange(i, stop))
                i = stop
            }
        }
        return out.toArray()
    }

    private fun compress(x: FloatArray, rate: Int, thresholdDb: Float, ratio: Float) {
        val att = exp(-1.0 / (0.008 * rate)).toFloat()
        val rel = exp(-1.0 / (0.12 * rate)).toFloat()
        var env = 0f
        val makeup = -thresholdDb * (1 - 1 / ratio) * 0.35f
        for (i in x.indices) {
            val a = abs(x[i])
            env = if (a > env) att * env + (1 - att) * a else rel * env + (1 - rel) * a
            val db = 20 * log10(env + 1e-9f)
            val over = db - thresholdDb
            val gr = if (over > 0) over * (1 - 1 / ratio) else 0f
            x[i] *= 10f.pow((makeup - gr) / 20f)
        }
    }

    private fun humanize(x: FloatArray, rate: Int, h: Float) {
        val rnd = Random(7)
        var drift = 0f
        var target = 0f
        val step = rate / 5
        val maxDb = 0.8f * h
        val copy = x.copyOf()
        val depth = 14f * h * rate / 24000f
        var phase = 0.0
        val wobble = 2 * PI * 0.27 / rate
        var b0 = 0f; var b1 = 0f; var b2 = 0f
        val tone = 10f.pow(-64f / 20f) * h
        for (i in x.indices) {
            if (i % step == 0) target = (rnd.nextFloat() * 2 - 1) * maxDb
            drift += (target - drift) * 0.0004f
            phase += wobble
            val d = 2 + depth * (1 + sin(phase).toFloat())
            val p = i - d
            val j = p.toInt()
            val v = if (j >= 0 && j + 1 < copy.size) copy[j] + (copy[j + 1] - copy[j]) * (p - j) else copy[i]
            val w = rnd.nextFloat() * 2 - 1
            b0 = 0.99765f * b0 + w * 0.099046f
            b1 = 0.963f * b1 + w * 0.2965164f
            b2 = 0.57f * b2 + w * 1.0526913f
            x[i] = v * 10f.pow(drift / 20f) + (b0 + b1 + b2 + w * 0.1848f) * tone * 0.2f
        }
    }

    private fun room(x: FloatArray, rate: Int, mix: Float): FloatArray {
        val combs = intArrayOf(1116, 1188, 1277, 1356).map { it * rate / 44100 }
        val aps = intArrayOf(556, 441).map { it * rate / 44100 }
        val wet = FloatArray(x.size)
        for (d in combs) {
            val buf = FloatArray(d)
            var idx = 0
            var lp = 0f
            for (i in x.indices) {
                val y = buf[idx]
                lp = y * 0.6f + lp * 0.4f
                buf[idx] = x[i] + lp * 0.72f
                idx = (idx + 1) % d
                wet[i] += y * 0.25f
            }
        }
        for (d in aps) {
            val buf = FloatArray(d)
            var idx = 0
            for (i in wet.indices) {
                val b = buf[idx]
                val y = -wet[i] + b
                buf[idx] = wet[i] + b * 0.5f
                idx = (idx + 1) % d
                wet[i] = y
            }
        }
        return FloatArray(x.size) { x[it] * (1 - mix * 0.5f) + wet[it] * mix }
    }

    private fun limit(x: FloatArray, rate: Int, ceiling: Float) {
        val look = rate / 200
        val n = x.size
        val rel = exp(-1.0 / (0.06 * rate)).toFloat()
        val need = FloatArray(n) { val a = abs(x[it]); if (a > ceiling) ceiling / a else 1f }
        val hold = FloatArray(n) { 1f }
        val dq = ArrayDeque<Int>()
        for (j in 0 until n + look) {
            if (j < n) {
                while (dq.isNotEmpty() && need[dq.last()] >= need[j]) dq.removeLast()
                dq.addLast(j)
            }
            val i = j - look
            if (i >= 0) {
                while (dq.first() < i) dq.removeFirst()
                hold[i] = need[dq.first()]
            }
        }
        var g = 1f
        for (i in 0 until n) {
            g = if (hold[i] < g) hold[i] else rel * g + (1 - rel) * hold[i]
            x[i] = (x[i] * g).coerceIn(-ceiling, ceiling)
        }
    }

    fun loudness(x: FloatArray, rate: Int): Float {
        if (x.isEmpty()) return -70f
        val y = x.copyOf()
        Biquad.highShelf(rate, 1681.0, 4.0).run(y)
        Biquad.highPass(rate, 38.0, 0.5).run(y)
        val block = (0.4 * rate).toInt()
        val hop = block / 4
        val powers = ArrayList<Double>()
        var s = 0
        while (s + block <= y.size) {
            var acc = 0.0
            for (i in s until s + block) acc += y[i] * y[i]
            powers += acc / block
            s += hop
        }
        if (powers.isEmpty()) {
            var acc = 0.0
            for (v in y) acc += v * v
            powers += acc / y.size
        }
        fun lufs(p: Double) = -0.691 + 10 * log10(p + 1e-12)
        val abs = powers.filter { lufs(it) > -70 }
        if (abs.isEmpty()) return -70f
        val rel = lufs(abs.average()) - 10
        val gated = abs.filter { lufs(it) > rel }
        return lufs((if (gated.isEmpty()) abs else gated).average()).toFloat()
    }
}
