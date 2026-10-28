package com.clappingcat.ar.tracking

import kotlin.math.acos
import kotlin.math.sqrt

class FloorTracker(
    private val width: Int,
    private val height: Int,
    private val focal: Double,
    startHeight: Double = DEFAULT_CAMERA_HEIGHT,
) {

    enum class Quality { LOST, WEAK, GOOD }

    @Volatile
    var position: DoubleArray = doubleArrayOf(0.0, startHeight, 0.0)
        private set

    @Volatile
    var quality: Quality = Quality.LOST
        private set

    @Volatile
    var inlierCount = 0
        private set

    private val detector = CornerDetector()
    private val lucasKanade = LucasKanade()
    private val resection = Resection()

    private var previous: GrayPyramid? = null
    private var previousRotation: DoubleArray? = null
    private var points = FloatArray(2 * CAPACITY)
    private var worldPoints = DoubleArray(3 * CAPACITY)
    private var misses = IntArray(CAPACITY)
    private var count = 0
    private var failedFrames = 0

    private val nextPoints = FloatArray(2 * CAPACITY)
    private val statuses = BooleanArray(CAPACITY)
    private val directions = DoubleArray(3 * CAPACITY)

    @Volatile
    private var resetRequested = false

    fun requestReset() {
        resetRequested = true
    }

    fun process(gray: ByteArray, rotation: DoubleArray) {
        if (resetRequested) {
            resetRequested = false
            clear()
        }
        val current = GrayPyramid.build(gray, width, height, LEVELS)
        val camera = cameraToWorld(rotation)
        val rate = previousRotation?.let { angle(it, rotation) } ?: 0.0
        val fast = rate > MAX_ROTATION_STEP

        val before = previous
        if (before != null && count > 0) {
            lucasKanade.trackWithCheck(before, current, points, count, nextPoints, statuses, 1.0f)
            compact()
        }

        var solved = false
        if (count >= MIN_TRACKED && !fast) {
            for (i in 0 until count) rayInto(camera, points[2 * i], points[2 * i + 1], directions, i)
            val result = resection.solve(worldPoints, directions, count)
            if (result != null) {
                val old = position
                val moved = distance(old, result.position)
                if (moved < MAX_JUMP) {
                    val y = result.position[1].coerceIn(MIN_HEIGHT, MAX_HEIGHT)
                    position = doubleArrayOf(
                        old[0] + SMOOTHING * (result.position[0] - old[0]),
                        old[1] + SMOOTHING * (y - old[1]),
                        old[2] + SMOOTHING * (result.position[2] - old[2]),
                    )
                    solved = true
                    inlierCount = result.inlierCount
                    var write = 0
                    for (i in 0 until count) {
                        val miss = if (result.inliers[i]) 0 else misses[i] + 1
                        if (miss >= MAX_MISSES) continue
                        copyPoint(i, write)
                        misses[write] = miss
                        write++
                    }
                    count = write
                }
            }
        }

        if (solved) {
            failedFrames = 0
        } else if (!fast) {
            failedFrames++
            if (failedFrames >= RESET_AFTER && count > 0) {
                count = 0
                failedFrames = 0
            }
            if (count < MIN_TRACKED) inlierCount = 0
        }

        quality = when {
            inlierCount >= GOOD_INLIERS && failedFrames < 10 -> Quality.GOOD
            inlierCount >= 6 && failedFrames < 30 -> Quality.WEAK
            else -> Quality.LOST
        }

        if (count < MIN_POINTS && !fast && (solved || count < MIN_TRACKED)) {
            val fresh = detector.detect(current.levels[0], width, height, points, count, CAPACITY - count)
            val pos = position
            var added = 0
            for (i in 0 until fresh.size / 2) {
                if (count + added >= CAPACITY) break
                val u = fresh[2 * i]
                val v = fresh[2 * i + 1]
                val slot = count + added
                rayInto(camera, u, v, directions, slot)
                val dy = directions[3 * slot + 1]
                if (dy > -MIN_DOWNWARD) continue
                val lambda = pos[1] / -dy
                if (lambda > MAX_DEPTH) continue
                points[2 * slot] = u
                points[2 * slot + 1] = v
                worldPoints[3 * slot] = pos[0] + lambda * directions[3 * slot]
                worldPoints[3 * slot + 1] = 0.0
                worldPoints[3 * slot + 2] = pos[2] + lambda * directions[3 * slot + 2]
                misses[slot] = 0
                added++
            }
            count += added
        }

        previous = current
        previousRotation = rotation.copyOf()
    }

    private fun clear() {
        position = doubleArrayOf(0.0, DEFAULT_CAMERA_HEIGHT, 0.0)
        previous = null
        previousRotation = null
        count = 0
        failedFrames = 0
        inlierCount = 0
        quality = Quality.LOST
    }

    private fun compact() {
        var write = 0
        for (i in 0 until count) {
            if (!statuses[i]) continue
            points[2 * write] = nextPoints[2 * i]
            points[2 * write + 1] = nextPoints[2 * i + 1]
            worldPoints[3 * write] = worldPoints[3 * i]
            worldPoints[3 * write + 1] = worldPoints[3 * i + 1]
            worldPoints[3 * write + 2] = worldPoints[3 * i + 2]
            misses[write] = misses[i]
            write++
        }
        count = write
    }

    private fun copyPoint(from: Int, to: Int) {
        if (from == to) return
        points[2 * to] = points[2 * from]
        points[2 * to + 1] = points[2 * from + 1]
        worldPoints[3 * to] = worldPoints[3 * from]
        worldPoints[3 * to + 1] = worldPoints[3 * from + 1]
        worldPoints[3 * to + 2] = worldPoints[3 * from + 2]
    }

    private fun rayInto(camera: DoubleArray, u: Float, v: Float, out: DoubleArray, slot: Int) {
        val x = (u - width / 2.0) / focal
        val y = (v - height / 2.0) / focal
        val norm = sqrt(x * x + y * y + 1.0)
        val bx = x / norm
        val by = y / norm
        val bz = 1.0 / norm
        out[3 * slot] = camera[0] * bx + camera[1] * by + camera[2] * bz
        out[3 * slot + 1] = camera[3] * bx + camera[4] * by + camera[5] * bz
        out[3 * slot + 2] = camera[6] * bx + camera[7] * by + camera[8] * bz
    }

    private fun cameraToWorld(r: DoubleArray): DoubleArray = doubleArrayOf(
        r[0], -r[1], -r[2],
        r[3], -r[4], -r[5],
        r[6], -r[7], -r[8],
    )

    private fun angle(a: DoubleArray, b: DoubleArray): Double {
        var trace = 0.0
        for (i in 0 until 3) for (j in 0 until 3) trace += a[3 * i + j] * b[3 * i + j]
        return acos(((trace - 1.0) * 0.5).coerceIn(-1.0, 1.0))
    }

    private fun distance(a: DoubleArray, b: DoubleArray): Double {
        val dx = a[0] - b[0]
        val dy = a[1] - b[1]
        val dz = a[2] - b[2]
        return sqrt(dx * dx + dy * dy + dz * dz)
    }

    companion object {
        const val DEFAULT_CAMERA_HEIGHT = 1.3
        private const val CAPACITY = 160
        private const val LEVELS = 3
        private const val MIN_POINTS = 70
        private const val MIN_TRACKED = 10
        private const val MAX_MISSES = 3
        private const val RESET_AFTER = 45
        private const val GOOD_INLIERS = 14
        private const val MAX_ROTATION_STEP = 0.07
        private const val MAX_JUMP = 1.0
        private const val SMOOTHING = 0.6
        private const val MIN_HEIGHT = 0.5
        private const val MAX_HEIGHT = 2.5
        private const val MIN_DOWNWARD = 0.12
        private const val MAX_DEPTH = 6.0
    }
}
