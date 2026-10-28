package com.clappingcat.ar

import android.opengl.GLES20

class DiscRenderer {

    private var program = 0
    private var unitLocation = 0
    private var viewProjectionLocation = 0
    private var modelLocation = 0
    private var radiusLocation = 0
    private var kindLocation = 0

    private val unitQuad = ShaderUtil.floatBuffer(floatArrayOf(-1f, -1f, -1f, 1f, 1f, -1f, 1f, 1f))

    fun create() {
        program = ShaderUtil.program(VERTEX, FRAGMENT)
        unitLocation = GLES20.glGetAttribLocation(program, "aUnit")
        viewProjectionLocation = GLES20.glGetUniformLocation(program, "uViewProjection")
        modelLocation = GLES20.glGetUniformLocation(program, "uModel")
        radiusLocation = GLES20.glGetUniformLocation(program, "uRadius")
        kindLocation = GLES20.glGetUniformLocation(program, "uKind")
    }

    fun draw(viewProjection: FloatArray, model: FloatArray, radius: Float, shadow: Boolean) {
        GLES20.glUseProgram(program)
        GLES20.glUniformMatrix4fv(viewProjectionLocation, 1, false, viewProjection, 0)
        GLES20.glUniformMatrix4fv(modelLocation, 1, false, model, 0)
        GLES20.glUniform1f(radiusLocation, radius)
        GLES20.glUniform1f(kindLocation, if (shadow) 1f else 0f)
        GLES20.glVertexAttribPointer(unitLocation, 2, GLES20.GL_FLOAT, false, 0, unitQuad)
        GLES20.glEnableVertexAttribArray(unitLocation)
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
        GLES20.glDisableVertexAttribArray(unitLocation)
    }

    private companion object {
        const val VERTEX = """
            attribute vec2 aUnit;
            uniform mat4 uViewProjection;
            uniform mat4 uModel;
            uniform float uRadius;
            varying vec2 vUnit;
            void main() {
                vUnit = aUnit;
                gl_Position = uViewProjection * uModel * vec4(aUnit.x * uRadius, 0.0, aUnit.y * uRadius, 1.0);
            }
        """

        const val FRAGMENT = """
            precision mediump float;
            varying vec2 vUnit;
            uniform float uKind;
            void main() {
                float r = length(vUnit);
                float alpha;
                vec3 color;
                if (uKind < 0.5) {
                    float ring = smoothstep(0.78, 0.82, r) * (1.0 - smoothstep(0.94, 1.0, r));
                    float dot = 1.0 - smoothstep(0.08, 0.12, r);
                    alpha = max(ring, dot) * 0.95;
                    color = vec3(1.0);
                } else {
                    alpha = 0.5 * (1.0 - smoothstep(0.15, 1.0, r));
                    color = vec3(0.0);
                }
                gl_FragColor = vec4(color * alpha, alpha);
            }
        """
    }
}
