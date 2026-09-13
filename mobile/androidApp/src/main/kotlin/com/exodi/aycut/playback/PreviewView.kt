package com.exodi.aycut.playback

import android.content.Context
import android.opengl.GLSurfaceView
import android.view.Choreographer
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

/**
 * GLES 2.0 surface that hosts a [PreviewSession]. Renders on vsync pulses
 * (Choreographer + RENDERMODE_WHEN_DIRTY) so the decode/composite loop only
 * wakes while the playhead is moving.
 */
class PreviewView(
    context: Context,
    private val session: PreviewSession,
) : GLSurfaceView(context) {

    init {
        setEGLContextClientVersion(2)
        setRenderer(PreviewRenderer(session))
        renderMode = RENDERMODE_WHEN_DIRTY
        Choreographer.getInstance().postFrameCallback(::onVsync)
    }

    private fun onVsync(frameTimeNanos: Long) {
        if (session.isPlaying || session.consumeSeekRequest()) {
            requestRender()
        }
        Choreographer.getInstance().postFrameCallback(::onVsync)
    }

    private class PreviewRenderer(
        private val session: PreviewSession,
    ) : GLSurfaceView.Renderer {

        override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
            session.onGlCreated()
        }

        override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
            session.onGlChanged(width, height)
        }

        override fun onDrawFrame(gl: GL10?) {
            session.onFrame()
        }
    }
}