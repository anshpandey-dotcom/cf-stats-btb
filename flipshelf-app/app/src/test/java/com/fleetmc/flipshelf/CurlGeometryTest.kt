package com.fleetmc.flipshelf

import com.fleetmc.flipshelf.reader.CurlGeometry
import com.fleetmc.flipshelf.reader.Poly
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CurlGeometryTest {

    private fun geometry(bottom: Boolean): CurlGeometry {
        val g = CurlGeometry()
        g.setSize(600f, 900f)
        g.setCorner(bottom)
        return g
    }

    @Test
    fun cornerReflectsOntoTip() {
        for (bottom in listOf(true, false)) {
            val g = geometry(bottom)
            val ys = if (bottom) listOf(900f, 700f, 500f) else listOf(0f, 200f, 400f)
            for (x in listOf(560f, 420f, 300f, 100f, -200f, -500f)) {
                for (y in ys) {
                    g.update(x, y)
                    if (!g.valid) continue
                    val d = g.side(g.cornerX, g.cornerY)
                    val rx = g.cornerX - 2 * d * g.dirX
                    val ry = g.cornerY - 2 * d * g.dirY
                    assertEquals(g.tipX, rx, 0.01f)
                    assertEquals(g.tipY, ry, 0.01f)
                    assertTrue(g.folded.size >= 3)
                }
            }
        }
    }

    @Test
    fun fullyGoneLeavesNothingUnfolded() {
        val g = geometry(true)
        g.update(-600f, 900f)
        assertTrue(g.unfolded.size == 0 || g.unfolded.size < 3 || areaOf(g.unfolded) < 900f)
    }

    private fun areaOf(p: Poly): Float {
        var a = 0f
        for (i in 0 until p.size) {
            val j = (i + 1) % p.size
            a += p.xs[i] * p.ys[j] - p.xs[j] * p.ys[i]
        }
        return Math.abs(a) / 2f
    }

}
