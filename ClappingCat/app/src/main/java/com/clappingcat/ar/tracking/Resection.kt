package com.clappingcat.ar.tracking

import java.util.Random
import kotlin.math.sqrt

class Resection(private val threshold: Double = 0.012, private val iterations: Int = 90, seed: Long = 7L) {

    class Result(val position: DoubleArray, val inliers: BooleanArray, val inlierCount: Int)

    private val random = Random(seed)

    fun solve(points: DoubleArray, directions: DoubleArray, count: Int): Result? {
        if (count < MIN_POINTS) return null
        var bestPosition: DoubleArray? = null
        var bestCount = 0
        val mask = BooleanArray(count)
        for (iteration in 0 until iterations) {
            val i = random.nextInt(count)
            var j = random.nextInt(count)
            if (j == i) j = (j + 1) % count
            if (angleBetween(directions, i, j) < MIN_BASELINE_ANGLE) continue
            val candidate = closestPoint(points, directions, intArrayOf(i, j), 2, null) ?: continue
            val found = countInliers(points, directions, count, candidate, mask)
            if (found > bestCount) {
                bestCount = found
                bestPosition = candidate
            }
        }
        var position = bestPosition ?: return null
        if (bestCount < MIN_INLIERS) return null
        countInliers(points, directions, count, position, mask)
        for (round in 0 until 2) {
            val indices = IntArray(bestCount)
            var n = 0
            for (k in 0 until count) if (mask[k]) indices[n++] = k
            val refined = closestPoint(points, directions, indices, n, position) ?: break
            position = refined
            bestCount = countInliers(points, directions, count, position, mask)
            if (bestCount < MIN_INLIERS) return null
        }
        return Result(position, mask.copyOf(), bestCount)
    }

    private fun countInliers(points: DoubleArray, directions: DoubleArray, count: Int, position: DoubleArray, mask: BooleanArray): Int {
        var found = 0
        for (k in 0 until count) {
            val vx = points[3 * k] - position[0]
            val vy = points[3 * k + 1] - position[1]
            val vz = points[3 * k + 2] - position[2]
            val dx = directions[3 * k]
            val dy = directions[3 * k + 1]
            val dz = directions[3 * k + 2]
            val length = sqrt(vx * vx + vy * vy + vz * vz)
            val inFront = vx * dx + vy * dy + vz * dz > 0.0
            val cx = vy * dz - vz * dy
            val cy = vz * dx - vx * dz
            val cz = vx * dy - vy * dx
            val sine = sqrt(cx * cx + cy * cy + cz * cz) / maxOf(length, 1e-6)
            val inlier = inFront && sine < threshold
            mask[k] = inlier
            if (inlier) found++
        }
        return found
    }

    private fun closestPoint(points: DoubleArray, directions: DoubleArray, indices: IntArray, n: Int, prior: DoubleArray?): DoubleArray? {
        val a = DoubleArray(9)
        val b = DoubleArray(3)
        for (m in 0 until n) {
            val k = indices[m]
            val dx = directions[3 * k]
            val dy = directions[3 * k + 1]
            val dz = directions[3 * k + 2]
            val p00 = 1 - dx * dx
            val p01 = -dx * dy
            val p02 = -dx * dz
            val p11 = 1 - dy * dy
            val p12 = -dy * dz
            val p22 = 1 - dz * dz
            a[0] += p00; a[1] += p01; a[2] += p02
            a[3] += p01; a[4] += p11; a[5] += p12
            a[6] += p02; a[7] += p12; a[8] += p22
            val x = points[3 * k]
            val y = points[3 * k + 1]
            val z = points[3 * k + 2]
            b[0] += p00 * x + p01 * y + p02 * z
            b[1] += p01 * x + p11 * y + p12 * z
            b[2] += p02 * x + p12 * y + p22 * z
        }
        val regularization = if (prior != null) 1e-3 else 1e-6
        a[0] += regularization; a[4] += regularization; a[8] += regularization
        if (prior != null) {
            b[0] += regularization * prior[0]
            b[1] += regularization * prior[1]
            b[2] += regularization * prior[2]
        }
        return solve3(a, b)
    }

    private fun angleBetween(directions: DoubleArray, i: Int, j: Int): Double {
        val dot = directions[3 * i] * directions[3 * j] + directions[3 * i + 1] * directions[3 * j + 1] + directions[3 * i + 2] * directions[3 * j + 2]
        return Math.acos(dot.coerceIn(-1.0, 1.0))
    }

    private fun solve3(a: DoubleArray, b: DoubleArray): DoubleArray? {
        val det = a[0] * (a[4] * a[8] - a[5] * a[7]) - a[1] * (a[3] * a[8] - a[5] * a[6]) + a[2] * (a[3] * a[7] - a[4] * a[6])
        if (Math.abs(det) < 1e-9) return null
        val inv = 1.0 / det
        val x = (b[0] * (a[4] * a[8] - a[5] * a[7]) - a[1] * (b[1] * a[8] - a[5] * b[2]) + a[2] * (b[1] * a[7] - a[4] * b[2])) * inv
        val y = (a[0] * (b[1] * a[8] - a[5] * b[2]) - b[0] * (a[3] * a[8] - a[5] * a[6]) + a[2] * (a[3] * b[2] - b[1] * a[6])) * inv
        val z = (a[0] * (a[4] * b[2] - b[1] * a[7]) - a[1] * (a[3] * b[2] - b[1] * a[6]) + b[0] * (a[3] * a[7] - a[4] * a[6])) * inv
        return doubleArrayOf(x, y, z)
    }

    private companion object {
        const val MIN_POINTS = 8
        const val MIN_INLIERS = 8
        const val MIN_BASELINE_ANGLE = 0.03
    }
}
