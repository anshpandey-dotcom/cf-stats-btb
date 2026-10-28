package com.clappingcat.ar.tracking

import kotlin.math.abs
import kotlin.math.sqrt

class LucasKanade(
    private val halfWindow: Int = 7,
    private val maxIterations: Int = 20,
    private val epsilon: Float = 0.02f,
    private val minEigenvalue: Float = 0.4f,
) {

    private val side = 2 * halfWindow + 1
    private val template = FloatArray(side * side)
    private val gradX = FloatArray(side * side)
    private val gradY = FloatArray(side * side)
    private val result = FloatArray(2)

    fun track(
        prev: GrayPyramid,
        next: GrayPyramid,
        points: FloatArray,
        count: Int,
        output: FloatArray,
        ok: BooleanArray,
    ) {
        for (i in 0 until count) {
            ok[i] = trackPoint(prev, next, points[2 * i], points[2 * i + 1])
            output[2 * i] = result[0]
            output[2 * i + 1] = result[1]
        }
    }

    fun trackWithCheck(
        prev: GrayPyramid,
        next: GrayPyramid,
        points: FloatArray,
        count: Int,
        output: FloatArray,
        ok: BooleanArray,
        maxBackError: Float,
    ) {
        track(prev, next, points, count, output, ok)
        for (i in 0 until count) {
            if (!ok[i]) continue
            val good = trackPoint(next, prev, output[2 * i], output[2 * i + 1])
            val dx = result[0] - points[2 * i]
            val dy = result[1] - points[2 * i + 1]
            ok[i] = good && sqrt(dx * dx + dy * dy) <= maxBackError
        }
    }

    private fun trackPoint(from: GrayPyramid, to: GrayPyramid, x: Float, y: Float): Boolean {
        var gx = 0f
        var gy = 0f
        for (level in from.levelCount - 1 downTo 0) {
            val scale = 1f / (1 shl level)
            val px = x * scale
            val py = y * scale
            val w = from.widths[level]
            val h = from.heights[level]
            val a = from.levels[level]
            val b = to.levels[level]

            var gxx = 0f
            var gxy = 0f
            var gyy = 0f
            var n = 0
            for (dy in -halfWindow..halfWindow) {
                for (dx in -halfWindow..halfWindow) {
                    val sx = px + dx
                    val sy = py + dy
                    val ix = (GrayPyramid.sample(a, w, h, sx + 1f, sy) - GrayPyramid.sample(a, w, h, sx - 1f, sy)) * 0.5f
                    val iy = (GrayPyramid.sample(a, w, h, sx, sy + 1f) - GrayPyramid.sample(a, w, h, sx, sy - 1f)) * 0.5f
                    template[n] = GrayPyramid.sample(a, w, h, sx, sy)
                    gradX[n] = ix
                    gradY[n] = iy
                    gxx += ix * ix
                    gxy += ix * iy
                    gyy += iy * iy
                    n++
                }
            }
            val det = gxx * gyy - gxy * gxy
            val half = (gxx + gyy) * 0.5f
            val root = sqrt(maxOf(0f, ((gxx - gyy) * 0.5f) * ((gxx - gyy) * 0.5f) + gxy * gxy))
            val minEig = (half - root) / (side * side)
            if (level == 0 && minEig < minEigenvalue) return false
            if (det < 1e-6f) {
                if (level > 0) {
                    gx *= 2f
                    gy *= 2f
                    continue
                }
                return false
            }
            val inv = 1f / det
            for (iteration in 0 until maxIterations) {
                var bx = 0f
                var by = 0f
                var k = 0
                for (dy in -halfWindow..halfWindow) {
                    for (dx in -halfWindow..halfWindow) {
                        val value = GrayPyramid.sample(b, w, h, px + gx + dx, py + gy + dy)
                        val error = template[k] - value
                        bx += gradX[k] * error
                        by += gradY[k] * error
                        k++
                    }
                }
                val stepX = (gyy * bx - gxy * by) * inv
                val stepY = (-gxy * bx + gxx * by) * inv
                gx += stepX
                gy += stepY
                if (abs(stepX) < epsilon && abs(stepY) < epsilon) break
            }
            if (level > 0) {
                gx *= 2f
                gy *= 2f
            }
        }
        val rx = x + gx
        val ry = y + gy
        result[0] = rx
        result[1] = ry
        val margin = halfWindow + 2
        return rx >= margin && ry >= margin && rx < from.widths[0] - margin && ry < from.heights[0] - margin
    }
}
