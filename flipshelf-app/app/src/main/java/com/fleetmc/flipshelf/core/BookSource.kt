package com.fleetmc.flipshelf.core

import android.graphics.Bitmap
import java.io.Closeable

interface BookSource : Closeable {
    val pageCount: Int
    val aspect: Float
    val reflowable: Boolean
    fun prepare(layoutWidth: Int, layoutHeight: Int, textScale: Float)
    fun render(index: Int, width: Int, height: Int): Bitmap
}
