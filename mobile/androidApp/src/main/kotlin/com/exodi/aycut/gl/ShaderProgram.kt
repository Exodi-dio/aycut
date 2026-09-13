package com.exodi.aycut.gl

import android.opengl.GLES20
import android.opengl.GLES20.GL_COMPILE_STATUS
import android.opengl.GLES20.GL_FRAGMENT_SHADER
import android.opengl.GLES20.GL_LINK_STATUS
import android.opengl.GLES20.GL_VERTEX_SHADER

/**
 * Minimal GLES 2.0 program wrapper: compiles vertex + fragment sources,
 * links, and hands out uniform locations. Written from first principles; no
 * third-party or GPL-derived code.
 */
class ShaderProgram(private val vertexSource: String, private val fragmentSource: String) {

    private var programId: Int = 0
    private val uniformLocations = mutableMapOf<String, Int>()

    val isReady: Boolean get() = programId != 0

    fun create() {
        require(programId == 0) { "program already created" }
        val vertex = compile(GL_VERTEX_SHADER, vertexSource)
        val fragment = compile(GL_FRAGMENT_SHADER, fragmentSource)
        programId = GLES20.glCreateProgram()
        check(programId != 0) { "glCreateProgram failed" }
        GLES20.glAttachShader(programId, vertex)
        GLES20.glAttachShader(programId, fragment)
        GLES20.glLinkProgram(programId)
        GLES20.glDeleteShader(vertex)
        GLES20.glDeleteShader(fragment)
        val status = IntArray(1)
        GLES20.glGetProgramiv(programId, GL_LINK_STATUS, status, 0)
        check(status[0] != 0) {
            "program link failed: ${GLES20.glGetProgramInfoLog(programId)}"
        }
    }

    fun use() {
        check(isReady) { "program not created" }
        GLES20.glUseProgram(programId)
    }

    fun attrib(name: String): Int {
        check(isReady) { "program not created" }
        return GLES20.glGetAttribLocation(programId, name)
    }

    fun uniform(name: String): Int {
        check(isReady) { "program not created" }
        return uniformLocations.getOrPut(name) {
            val location = GLES20.glGetUniformLocation(programId, name)
            if (location == -1) {
                // Uniforms optimized out are legal for a layered program: the
                // renderer treats missing ones as identity/no-op via -1 checks.
            }
            location
        }
    }

    fun dispose() {
        if (programId != 0) {
            GLES20.glDeleteProgram(programId)
            programId = 0
            uniformLocations.clear()
        }
    }

    private fun compile(type: Int, source: String): Int {
        val shader = GLES20.glCreateShader(type)
        check(shader != 0) { "glCreateShader failed for type $type" }
        GLES20.glShaderSource(shader, source)
        GLES20.glCompileShader(shader)
        val status = IntArray(1)
        GLES20.glGetShaderiv(shader, GL_COMPILE_STATUS, status, 0)
        if (status[0] == 0) {
            val log = GLES20.glGetShaderInfoLog(shader)
            GLES20.glDeleteShader(shader)
            error("shader compile failed: $log")
        }
        return shader
    }
}