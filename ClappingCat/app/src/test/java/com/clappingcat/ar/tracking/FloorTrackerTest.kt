package com.clappingcat.ar.tracking

import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin
import kotlin.math.sqrt
import org.junit.Assert.assertTrue
import org.junit.Test

class FloorTrackerTest {

    private val width = 240
    private val height = 320
    private val focal = 0.7 * height

    private fun hash(x: Int, z: Int): Double {
        var h = x * 374761393 + z * 668265263
        h = (h xor (h ushr 13)) * 1274126177
        h = h xor (h ushr 16)
        return (h and 0xFFFF) / 65535.0
    }

    private fun noise(x: Double, z: Double): Double {
        val x0 = floor(x).toInt()
        val z0 = floor(z).toInt()
        val fx = x - x0
        val fz = z - z0
        val sx = fx * fx * (3 - 2 * fx)
        val sz = fz * fz * (3 - 2 * fz)
        val a = hash(x0, z0)
        val b = hash(x0 + 1, z0)
        val c = hash(x0, z0 + 1)
        val d = hash(x0 + 1, z0 + 1)
        return (a + (b - a) * sx) + ((c + (d - c) * sx) - (a + (b - a) * sx)) * sz
    }

    private fun texture(x: Double, z: Double): Double {
        var value = 0.0
        var amplitude = 1.0
        var scale = 2.0
        for (octave in 0 until 5) {
            value += amplitude * noise(x * scale, z * scale)
            amplitude *= 0.6
            scale *= 2.0
        }
        return value / 2.9
    }

    private fun rotation(yaw: Double, pitch: Double, roll: Double): DoubleArray {
        val ry = doubleArrayOf(cos(yaw), 0.0, sin(yaw), 0.0, 1.0, 0.0, -sin(yaw), 0.0, cos(yaw))
        val rx = doubleArrayOf(1.0, 0.0, 0.0, 0.0, cos(pitch), -sin(pitch), 0.0, sin(pitch), cos(pitch))
        val rz = doubleArrayOf(cos(roll), -sin(roll), 0.0, sin(roll), cos(roll), 0.0, 0.0, 0.0, 1.0)
        return multiply(multiply(ry, rx), rz)
    }

    private fun multiply(a: DoubleArray, b: DoubleArray): DoubleArray {
        val out = DoubleArray(9)
        for (i in 0 until 3) for (j in 0 until 3) {
            var sum = 0.0
            for (k in 0 until 3) sum += a[3 * i + k] * b[3 * k + j]
            out[3 * i + j] = sum
        }
        return out
    }

    private fun render(rotationDevice: DoubleArray, position: DoubleArray): ByteArray {
        val image = ByteArray(width * height)
        val r = rotationDevice
        for (v in 0 until height) {
            for (u in 0 until width) {
                val x = (u - width / 2.0) / focal
                val y = (v - height / 2.0) / focal
                val norm = sqrt(x * x + y * y + 1.0)
                val bx = x / norm
                val by = y / norm
                val bz = 1.0 / norm
                val dx = r[0] * bx - r[1] * by - r[2] * bz
                val dy = r[3] * bx - r[4] * by - r[5] * bz
                val dz = r[6] * bx - r[7] * by - r[8] * bz
                val value = if (dy < -0.02) {
                    val t = position[1] / -dy
                    texture(position[0] + t * dx, position[2] + t * dz) * 230.0 + 10.0
                } else {
                    120.0
                }
                image[v * width + u] = value.toInt().coerceIn(0, 255).toByte()
            }
        }
        return image
    }

    private fun run(timeOffsetFrames: Double, noiseDegrees: Double): Pair<Double, Double> {
        val tracker = FloorTracker(width, height, focal)
        val random = java.util.Random(3)
        var maxError = 0.0
        var finalError = 0.0
        val frames = 150
        for (frame in 0 until frames) {
            val t = frame / 30.0
            val truth = doubleArrayOf(0.5 * sin(0.5 * t), 1.3 + 0.02 * sin(3 * t), -0.45 * t)
            val yaw = Math.toRadians(15.0) * sin(0.7 * t)
            val pitch = Math.toRadians(-40.0 + 5.0 * sin(0.9 * t))
            val roll = Math.toRadians(3.0) * sin(1.3 * t)
            val image = render(rotation(yaw, pitch, roll), truth)
            val tm = t - timeOffsetFrames / 30.0
            val measured = rotation(
                Math.toRadians(15.0) * sin(0.7 * tm) + Math.toRadians(noiseDegrees) * random.nextGaussian(),
                Math.toRadians(-40.0 + 5.0 * sin(0.9 * tm)) + Math.toRadians(noiseDegrees) * random.nextGaussian(),
                Math.toRadians(3.0) * sin(1.3 * tm),
            )
            tracker.process(image, measured)
            val estimate = tracker.position
            val error = sqrt(
                (estimate[0] - truth[0]) * (estimate[0] - truth[0]) +
                    (estimate[1] - truth[1]) * (estimate[1] - truth[1]) +
                    (estimate[2] - truth[2]) * (estimate[2] - truth[2]),
            )
            if (frame > 5 && error > maxError) maxError = error
            finalError = error
            if (frame % 30 == 0) println("frame $frame error=$error inliers=${tracker.inlierCount} quality=${tracker.quality}")
        }
        println("max=$maxError final=$finalError")
        return Pair(maxError, finalError)
    }

    @Test
    fun staysLockedWhileWalking() {
        val (maxError, finalError) = run(0.0, 0.05)
        assertTrue("max error $maxError", maxError < 0.2)
        assertTrue("final error $finalError", finalError < 0.15)
    }

    @Test
    fun toleratesSensorLag() {
        val (maxError, finalError) = run(0.7, 0.1)
        assertTrue("max error $maxError", maxError < 0.3)
        assertTrue("final error $finalError", finalError < 0.25)
    }
}
