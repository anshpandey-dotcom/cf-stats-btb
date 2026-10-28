package com.clappingcat.ar.tracking

import kotlin.math.max
import kotlin.math.sqrt

class CornerDetector(private val quality: Float = 0.02f, private val minDistance: Int = 12, private val margin: Int = 12) {

    fun detect(image: FloatArray, width: Int, height: Int, existing: FloatArray, existingCount: Int, maxNew: Int): FloatArray {
        if (maxNew <= 0) return FloatArray(0)
        val score = FloatArray(width * height)
        val ixx = DoubleArray((width + 1) * (height + 1))
        val ixy = DoubleArray((width + 1) * (height + 1))
        val iyy = DoubleArray((width + 1) * (height + 1))
        val stride = width + 1
        for (y in 0 until height) {
            var rowXx = 0.0
            var rowXy = 0.0
            var rowYy = 0.0
            for (x in 0 until width) {
                var gx = 0f
                var gy = 0f
                if (x > 0 && y > 0 && x < width - 1 && y < height - 1) {
                    val i = y * width + x
                    gx = (image[i + 1 - width] + 2f * image[i + 1] + image[i + 1 + width] -
                        image[i - 1 - width] - 2f * image[i - 1] - image[i - 1 + width]) * 0.125f
                    gy = (image[i + width - 1] + 2f * image[i + width] + image[i + width + 1] -
                        image[i - width - 1] - 2f * image[i - width] - image[i - width + 1]) * 0.125f
                }
                rowXx += gx * gx
                rowXy += gx * gy
                rowYy += gy * gy
                val k = (y + 1) * stride + x + 1
                ixx[k] = ixx[k - stride] + rowXx
                ixy[k] = ixy[k - stride] + rowXy
                iyy[k] = iyy[k - stride] + rowYy
            }
        }
        val radius = 2
        var best = 0f
        for (y in margin until height - margin) {
            for (x in margin until width - margin) {
                val a = box(ixx, stride, x - radius, y - radius, x + radius + 1, y + radius + 1)
                val b = box(ixy, stride, x - radius, y - radius, x + radius + 1, y + radius + 1)
                val c = box(iyy, stride, x - radius, y - radius, x + radius + 1, y + radius + 1)
                val half = (a + c) * 0.5
                val root = sqrt(max(0.0, ((a - c) * 0.5) * ((a - c) * 0.5) + b * b))
                val value = (half - root).toFloat()
                score[y * width + x] = value
                if (value > best) best = value
            }
        }
        if (best <= 0f) return FloatArray(0)
        val threshold = best * quality
        val candidates = ArrayList<Int>()
        for (y in margin until height - margin) {
            for (x in margin until width - margin) {
                val i = y * width + x
                val v = score[i]
                if (v < threshold) continue
                if (v >= score[i - 1] && v >= score[i + 1] && v >= score[i - width] && v >= score[i + width] &&
                    v >= score[i - width - 1] && v >= score[i - width + 1] && v >= score[i + width - 1] && v >= score[i + width + 1]
                ) {
                    candidates.add(i)
                }
            }
        }
        candidates.sortByDescending { score[it] }

        val cell = minDistance / 1.4142f
        val gridW = (width / cell).toInt() + 3
        val gridH = (height / cell).toInt() + 3
        val grid = arrayOfNulls<FloatArray>(gridW * gridH)
        for (i in 0 until existingCount) {
            val px = existing[2 * i]
            val py = existing[2 * i + 1]
            grid[(py / cell).toInt() * gridW + (px / cell).toInt()] = floatArrayOf(px, py)
        }
        val result = ArrayList<Float>()
        val minSq = minDistance.toFloat() * minDistance
        for (index in candidates) {
            if (result.size / 2 >= maxNew) break
            val px = (index % width).toFloat()
            val py = (index / width).toFloat()
            val gx = (px / cell).toInt()
            val gy = (py / cell).toInt()
            var free = true
            loop@ for (oy in -2..2) {
                for (ox in -2..2) {
                    val nx = gx + ox
                    val ny = gy + oy
                    if (nx < 0 || ny < 0 || nx >= gridW || ny >= gridH) continue
                    val other = grid[ny * gridW + nx] ?: continue
                    val dx = other[0] - px
                    val dy = other[1] - py
                    if (dx * dx + dy * dy < minSq) {
                        free = false
                        break@loop
                    }
                }
            }
            if (free) {
                grid[gy * gridW + gx] = floatArrayOf(px, py)
                result.add(px)
                result.add(py)
            }
        }
        return result.toFloatArray()
    }

    private fun box(sum: DoubleArray, stride: Int, x0: Int, y0: Int, x1: Int, y1: Int): Double =
        sum[y1 * stride + x1] - sum[y0 * stride + x1] - sum[y1 * stride + x0] + sum[y0 * stride + x0]
}
