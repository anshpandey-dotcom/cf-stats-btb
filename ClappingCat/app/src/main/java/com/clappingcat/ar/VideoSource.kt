package com.clappingcat.ar

import android.graphics.SurfaceTexture
import android.opengl.GLES11Ext
import android.opengl.GLES20
import android.view.Surface
import java.util.concurrent.atomic.AtomicBoolean

class VideoSource {

    val textureId: Int
    val surface: Surface
    val transform = FloatArray(16)

    var hasFrame = false
        private set

    private val surfaceTexture: SurfaceTexture
    private val frameAvailable = AtomicBoolean(false)

    init {
        val textures = IntArray(1)
        GLES20.glGenTextures(1, textures, 0)
        textureId = textures[0]
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, textureId)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
        surfaceTexture = SurfaceTexture(textureId)
        surfaceTexture.setOnFrameAvailableListener { frameAvailable.set(true) }
        surface = Surface(surfaceTexture)
        android.opengl.Matrix.setIdentityM(transform, 0)
    }

    fun update() {
        if (frameAvailable.getAndSet(false)) {
            surfaceTexture.updateTexImage()
            surfaceTexture.getTransformMatrix(transform)
            hasFrame = true
        }
    }

    fun release() {
        surface.release()
        surfaceTexture.release()
    }
}
