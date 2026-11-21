package com.fleetmc.flipshelf.reader

import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

class Poly {
    val xs = FloatArray(8)
    val ys = FloatArray(8)
    var size = 0

    fun clear() {
        size = 0
    }

    fun add(x: Float, y: Float) {
        xs[size] = x
        ys[size] = y
        size++
    }
}

class CurlGeometry {
    var width = 0f
        private set
    var height = 0f
        private set
    var cornerBottom = true
        private set
    var cornerX = 0f
        private set
    var cornerY = 0f
        private set
    var tipX = 0f
        private set
    var tipY = 0f
        private set
    var midX = 0f
        private set
    var midY = 0f
        private set
    var dirX = -1f
        private set
    var dirY = 0f
        private set
    var valid = false
        private set
    var flapDepth = 0f
        private set

    val unfolded = Poly()
    val folded = Poly()
    val flap = Poly()

    private val scratch = Poly()

    fun setSize(w: Float, h: Float) {
        width = w
        height = h
        cornerX = w
    }

    fun setCorner(bottom: Boolean) {
        cornerBottom = bottom
        cornerY = if (bottom) height else 0f
        cornerX = width
    }

    fun restX() = width

    fun goneX() = -width

    fun clampTip(px: Float, py: Float, out: FloatArray) {
        var x = px
        var y = py
        if (cornerBottom) {
            y = min(y, height)
            y = max(y, height * 0.3f)
        } else {
            y = max(y, 0f)
            y = min(y, height * 0.7f)
        }
        x = min(x, width)
        val ay = if (cornerBottom) height else 0f
        val dx = x
        val dy = y - ay
        val dist = hypot(dx, dy)
        val limit = width * 0.9995f
        if (dist > limit) {
            val k = limit / dist
            x = dx * k
            y = ay + dy * k
        }
        out[0] = x
        out[1] = y
    }

    private val tmp = FloatArray(2)

    fun update(px: Float, py: Float) {
        clampTip(px, py, tmp)
        tipX = tmp[0]
        tipY = tmp[1]
        val vx = tipX - cornerX
        val vy = tipY - cornerY
        val len = hypot(vx, vy)
        unfolded.clear()
        folded.clear()
        flap.clear()
        if (len < 0.75f) {
            valid = false
            return
        }
        valid = true
        dirX = vx / len
        dirY = vy / len
        midX = (tipX + cornerX) * 0.5f
        midY = (tipY + cornerY) * 0.5f
        clip(unfolded, 1f)
        clip(folded, -1f)
        var depth = 0f
        for (i in 0 until folded.size) {
            val d = side(folded.xs[i], folded.ys[i])
            val rx = folded.xs[i] - 2f * d * dirX
            val ry = folded.ys[i] - 2f * d * dirY
            flap.add(rx, ry)
            depth = max(depth, -d)
        }
        flapDepth = depth
    }

    fun side(x: Float, y: Float): Float = (x - midX) * dirX + (y - midY) * dirY

    private fun clip(out: Poly, sign: Float) {
        scratch.clear()
        scratch.add(0f, 0f)
        scratch.add(width, 0f)
        scratch.add(width, height)
        scratch.add(0f, height)
        val n = scratch.size
        for (i in 0 until n) {
            val j = (i + 1) % n
            val ax = scratch.xs[i]
            val ay = scratch.ys[i]
            val bx = scratch.xs[j]
            val by = scratch.ys[j]
            val da = side(ax, ay) * sign
            val db = side(bx, by) * sign
            if (da >= 0f) out.add(ax, ay)
            if ((da > 0f && db < 0f) || (da < 0f && db > 0f)) {
                val t = da / (da - db)
                out.add(ax + (bx - ax) * t, ay + (by - ay) * t)
            }
        }
    }
}
