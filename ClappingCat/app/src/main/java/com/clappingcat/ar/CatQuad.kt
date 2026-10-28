package com.clappingcat.ar

import android.opengl.GLES11Ext
import android.opengl.GLES20

class CatQuad {

    private var program = 0
    private var unitLocation = 0
    private var mvpLocation = 0
    private var rectLocation = 0
    private var textureMatrixLocation = 0
    private var samplerLocation = 0
    private var opacityLocation = 0

    private val unitQuad = ShaderUtil.floatBuffer(floatArrayOf(0f, 0f, 0f, 1f, 1f, 0f, 1f, 1f))

    fun create() {
        program = ShaderUtil.program(VERTEX, FRAGMENT)
        unitLocation = GLES20.glGetAttribLocation(program, "aUnit")
        mvpLocation = GLES20.glGetUniformLocation(program, "uMvp")
        rectLocation = GLES20.glGetUniformLocation(program, "uRect")
        textureMatrixLocation = GLES20.glGetUniformLocation(program, "uTexMatrix")
        samplerLocation = GLES20.glGetUniformLocation(program, "uVideo")
        opacityLocation = GLES20.glGetUniformLocation(program, "uOpacity")
    }

    fun draw(mvp: FloatArray, left: Float, bottom: Float, width: Float, height: Float, source: VideoSource, opacity: Float) {
        GLES20.glUseProgram(program)
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, source.textureId)
        GLES20.glUniform1i(samplerLocation, 0)
        GLES20.glUniformMatrix4fv(mvpLocation, 1, false, mvp, 0)
        GLES20.glUniformMatrix4fv(textureMatrixLocation, 1, false, source.transform, 0)
        GLES20.glUniform4f(rectLocation, left, bottom, width, height)
        GLES20.glUniform1f(opacityLocation, opacity)
        GLES20.glVertexAttribPointer(unitLocation, 2, GLES20.GL_FLOAT, false, 0, unitQuad)
        GLES20.glEnableVertexAttribArray(unitLocation)
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
        GLES20.glDisableVertexAttribArray(unitLocation)
    }

    private companion object {
        const val VERTEX = """
            attribute vec2 aUnit;
            uniform mat4 uMvp;
            uniform vec4 uRect;
            uniform mat4 uTexMatrix;
            varying vec2 vColor;
            varying vec2 vMatte;
            void main() {
                vec3 local = vec3(uRect.x + aUnit.x * uRect.z, uRect.y + aUnit.y * uRect.w, 0.0);
                gl_Position = uMvp * vec4(local, 1.0);
                vColor = (uTexMatrix * vec4(aUnit.x * 0.5, aUnit.y, 0.0, 1.0)).xy;
                vMatte = (uTexMatrix * vec4(0.5 + aUnit.x * 0.5, aUnit.y, 0.0, 1.0)).xy;
            }
        """

        const val FRAGMENT = """
            #extension GL_OES_EGL_image_external : require
            precision mediump float;
            varying vec2 vColor;
            varying vec2 vMatte;
            uniform samplerExternalOES uVideo;
            uniform float uOpacity;
            void main() {
                float alpha = texture2D(uVideo, vMatte).r;
                vec3 color = min(texture2D(uVideo, vColor).rgb, vec3(alpha));
                gl_FragColor = vec4(color, alpha) * uOpacity;
            }
        """
    }
}
