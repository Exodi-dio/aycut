package com.exodi.aycut.gl

import android.opengl.GLES11Ext
import android.opengl.GLES20
import android.opengl.GLES20.GL_COLOR_BUFFER_BIT
import android.opengl.GLES20.GL_FLOAT
import android.opengl.GLES20.GL_TRIANGLES
import com.exodi.aycut.core.composite.LayerSpec
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer

/**
 * Draws a full-canvas background and each [LayerSpec] as a textured quad.
 *
 * The vertex shader maps a unit-square clip rect ([LayerSpec.outputRect]) into
 * NDC, samples the texture through [LayerSpec.uvWindow], rotates sampling by
 * the asset's rotation metadata, and applies per-layer opacity. The approach
 * is first-principles GLES 2.0 (one quad, one texture, one program); no
 * third-party or GPL-derived shader code.
 */
class PreviewRenderer {

    private val program = ShaderProgram(VERTEX_SOURCE, FRAGMENT_SOURCE)

    // Interleaved [x, y, u, v] for two triangles covering [0,1]^2.
    private var quadBuffer: FloatBuffer? = null

    fun onSurfaceCreated() {
        program.create()
        val data = floatArrayOf(
            0f, 0f, 0f, 0f,
            1f, 0f, 1f, 0f,
            0f, 1f, 0f, 1f,
            1f, 0f, 1f, 0f,
            0f, 1f, 0f, 1f,
            1f, 1f, 1f, 1f,
        )
        val buffer = ByteBuffer.allocateDirect(data.size * 4)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()
        buffer.put(data).position(0)
        quadBuffer = buffer

        GLES20.glDisable(GLES20.GL_DEPTH_TEST)
        GLES20.glDisable(GLES20.GL_BLEND)
        GLES20.glClearColor(0f, 0f, 0f, 1f)
    }

    /** Clears the canvas (sequence background; hard black for now). */
    fun clearCanvas() {
        GLES20.glClear(GL_COLOR_BUFFER_BIT)
    }

    /**
     * Draws [layer] sampling [externalTextureId] (GL_TEXTURE_EXTERNAL_OES).
     * Must be called with a bound GL context (render thread).
     */
    fun drawLayer(layer: LayerSpec, externalTextureId: Int) {
        val buffer = quadBuffer ?: error("renderer not created")
        program.use()
        val pos = program.attrib("aPosition")
        val uv = program.attrib("aTexCoord")
        GLES20.glEnableVertexAttribArray(pos)
        GLES20.glEnableVertexAttribArray(uv)

        buffer.position(0)
        GLES20.glVertexAttribPointer(pos, 2, GL_FLOAT, false, 16, buffer)
        buffer.position(2)
        GLES20.glVertexAttribPointer(uv, 2, GL_FLOAT, false, 16, buffer)

        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, externalTextureId)
        GLES20.glUniform1i(program.uniform("uTexture"), 0)

        val out = layer.outputRect
        GLES20.glUniform4f(
            program.uniform("uOutputRect"),
            out.left,
            out.top,
            out.width,
            out.height,
        )
        val uvRect = layer.uvWindow
        GLES20.glUniform4f(
            program.uniform("uUvWindow"),
            uvRect.left,
            uvRect.top,
            uvRect.width,
            uvRect.height,
        )
        GLES20.glUniform1f(program.uniform("uRotation"), layer.rotationDegrees.toFloat())
        GLES20.glUniform1f(program.uniform("uOpacity"), layer.opacity)

        bufferedOnce(buffer) {
            GLES20.glDrawArrays(GL_TRIANGLES, 0, 6)
        }

        GLES20.glDisableVertexAttribArray(pos)
        GLES20.glDisableVertexAttribArray(uv)
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, 0)
    }

    fun onSurfaceDestroyed() {
        program.dispose()
        quadBuffer = null
    }

    /** No-op helper to keep the buffer position stable across draw calls. */
    private inline fun bufferedOnce(buffer: FloatBuffer, block: () -> Unit) {
        block()
        buffer.position(0)
    }

    companion object {
        val CLEAR_COLOR = floatArrayOf(0f, 0f, 0f, 1f)

        private val VERTEX_SOURCE = """
            attribute vec2 aPosition;
            attribute vec2 aTexCoord;
            uniform vec4 uOutputRect;   // x,y = left/top; z,w = width/height (unit canvas)
            uniform vec4 uUvWindow;     // x,y = left/top; z,w = width/height (unit texture)
            uniform float uRotation;    // degrees, clockwise, about texture center
            varying vec2 vTexCoord;
            void main() {
                vec2 uv = aTexCoord - 0.5;
                float rad = radians(uRotation);
                vec2 rotated = vec2(
                    uv.x * cos(rad) - uv.y * sin(rad),
                    uv.x * sin(rad) + uv.y * cos(rad)
                );
                vTexCoord = uUvWindow.xy + (rotated + 0.5) * uUvWindow.zw;
                vec2 ndc = uOutputRect.xy + aPosition * uOutputRect.zw;
                gl_Position = vec4(ndc * 2.0 - 1.0, 0.0, 1.0);
            }
        """.trimIndent()

        private val FRAGMENT_SOURCE = """
            #extension GL_OES_EGL_image_external : require
            precision mediump float;
            uniform samplerExternalOES uTexture;
            uniform float uOpacity;
            varying vec2 vTexCoord;
            void main() {
                vec4 texel = texture2D(uTexture, vTexCoord);
                gl_FragColor = vec4(texel.rgb, uOpacity);
            }
        """.trimIndent()
    }
}