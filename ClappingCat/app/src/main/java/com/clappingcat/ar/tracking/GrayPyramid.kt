package com.clappingcat.ar.tracking

class GrayPyramid(val levels: Array<FloatArray>, val widths: IntArray, val heights: IntArray) {

    val levelCount: Int get() = levels.size

    companion object {
        fun build(gray: ByteArray, width: Int, height: Int, levelCount: Int): GrayPyramid {
            val levels = ArrayList<FloatArray>()
            val widths = IntArray(levelCount)
            val heights = IntArray(levelCount)
            val base = FloatArray(width * height)
            for (i in base.indices) base[i] = (gray[i].toInt() and 0xFF).toFloat()
            levels.add(base)
            widths[0] = width
            heights[0] = height
            for (level in 1 until levelCount) {
                val pw = widths[level - 1]
                val ph = heights[level - 1]
                val w = pw / 2
                val h = ph / 2
                val src = levels[level - 1]
                val dst = FloatArray(w * h)
                for (y in 0 until h) {
                    for (x in 0 until w) {
                        val i = (2 * y) * pw + 2 * x
                        dst[y * w + x] = (src[i] + src[i + 1] + src[i + pw] + src[i + pw + 1]) * 0.25f
                    }
                }
                levels.add(dst)
                widths[level] = w
                heights[level] = h
            }
            return GrayPyramid(levels.toTypedArray(), widths, heights)
        }

        fun sample(image: FloatArray, width: Int, height: Int, x: Float, y: Float): Float {
            val cx = if (x < 0f) 0f else if (x > width - 1.001f) width - 1.001f else x
            val cy = if (y < 0f) 0f else if (y > height - 1.001f) height - 1.001f else y
            val x0 = cx.toInt()
            val y0 = cy.toInt()
            val fx = cx - x0
            val fy = cy - y0
            val i = y0 * width + x0
            val top = image[i] + (image[i + 1] - image[i]) * fx
            val bottom = image[i + width] + (image[i + width + 1] - image[i + width]) * fx
            return top + (bottom - top) * fy
        }
    }
}
