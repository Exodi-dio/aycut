package com.exodi.aycut.playback

import android.graphics.SurfaceTexture
import android.opengl.GLES11Ext
import android.opengl.GLES20
import android.view.Surface
import com.exodi.aycut.core.composite.CompositorGraph
import com.exodi.aycut.core.composite.LayerSpec
import com.exodi.aycut.core.media.MediaAssetRegistry
import com.exodi.aycut.core.model.MediaId
import com.exodi.aycut.core.model.Sequence
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Drives the compositor + decoders for one video preview surface.
 *
 * All frame work happens on the GL thread (see [onFrame]); play/pause/seek
 * may be called from the UI thread while the GL loop reads the playhead.
 */
class PreviewSession(
    private val resolveFile: (MediaId) -> File,
) {

    @Volatile
    private var sequence: Sequence = Sequence.createEmpty(1920, 1080, 30.0)

    @Volatile
    private var registry: MediaAssetRegistry = MediaAssetRegistry.EMPTY

    private val renderer = com.exodi.aycut.gl.PreviewRenderer()
    private val graph get() = CompositorGraph(sequence, registry)
    private val decoders = mutableMapOf<MediaId, ActiveDecoder>()
    private val seekRequested = AtomicBoolean(false)

    @Volatile private var playing = false
    @Volatile private var playheadNanos: Long = 0L
    private var lastFrameNanos: Long = 0L

    fun setProject(sequence: Sequence, registry: MediaAssetRegistry, playheadMicros: Long = 0L) {
        this.sequence = sequence
        this.registry = registry
        this.playheadNanos = playheadMicros.coerceAtLeast(0L) * 1_000L
        requestSeek()
    }

    val isPlaying: Boolean get() = playing

    fun play() {
        if (!playing) {
            playing = true
            lastFrameNanos = 0L
            requestSeek()
        }
    }

    fun pause() {
        playing = false
    }

    fun seekTo(playheadMicros: Long) {
        playheadNanos = playheadMicros.coerceAtLeast(0L) * 1_000L
        requestSeek()
    }

    fun seekToFraction(fraction: Float) {
        seekTo((fraction * sequence.durationMicros).toLong())
    }

    fun playheadMicros(): Long = playheadNanos / 1_000L

    /** True once per caller between seeks; the preview view re-renders. */
    fun consumeSeekRequest(): Boolean = seekRequested.getAndSet(false)

    private fun requestSeek() {
        seekRequested.set(true)
    }

    internal fun onGlCreated() {
        // A prior context's decoders/user objects are stale after surface
        // recreation; running here guarantees we are on the GL thread.
        decoders.values.forEach { it.release() }
        decoders.clear()
        renderer.onSurfaceCreated()
    }

    internal fun onGlChanged(width: Int, height: Int) {
        GLES20.glViewport(0, 0, width, height)
    }

    /**
     * Advances the playhead (when playing), restarts decoders on seek, and
     * composites every active layer from the latest decoded frame.
     */
    internal fun onFrame(): List<LayerSpec> {
        val now = System.nanoTime()
        if (playing) {
            if (lastFrameNanos != 0L) playheadNanos += now - lastFrameNanos
            lastFrameNanos = now
        }
        val micros = (playheadNanos / 1_000L).coerceIn(0L, sequence.durationMicros)
        playheadNanos = micros * 1_000L

        if (seekRequested.getAndSet(false)) {
            restartDecoders(micros)
        }

        val layers = graph.layers(micros)
        ensureDecodersFor(layers)

        renderer.clearCanvas()
        for (layer in layers) {
            decoders[layer.mediaId]?.let { active ->
                val window = sourceWindow(layer.mediaId, micros)
                if (window != null && !active.started) {
                    active.start(window.first, window.second)
                }
                if (active.isRunning.get()) {
                    active.pulse()
                }
                if (active.frameAvailable.get()) {
                    active.surfaceTexture.updateTexImage()
                    active.frameAvailable.set(false)
                }
                renderer.drawLayer(layer, active.textureId)
            }
        }
        return layers
    }

    internal fun onGlDestroyed() {
        decoders.values.forEach { it.release() }
        decoders.clear()
        renderer.onSurfaceDestroyed()
    }

    private fun ensureDecodersFor(layers: List<LayerSpec>) {
        for (layer in layers) {
            if (decoders.containsKey(layer.mediaId)) continue
            val asset = registry.asset(layer.mediaId) ?: continue
            val file = resolveFile(layer.mediaId)
            if (!file.isFile) continue
            val mime = asset.mimeType
                ?: ClipDecoder.videoMimeOf(file)
                ?: continue
            val decoder = ClipDecoder(file, mime)
            val active = ActiveDecoder(decoder)
            decoders[layer.mediaId] = active
        }
    }

    private fun restartDecoders(micros: Long) {
        for (layer in graph.layers(micros)) {
            val window = sourceWindow(layer.mediaId, micros) ?: continue
            val active = decoders[layer.mediaId] ?: continue
            active.start(window.first, window.second)
        }
    }

    private fun sourceWindow(mediaId: MediaId, micros: Long): Pair<Long, Long>? {
        for (track in sequence.tracks) {
            val clip = track.clipAt(micros) ?: continue
            if (clip.media == mediaId) {
                return clip.sourceTimeAt(micros) to clip.sourceRange.end
            }
        }
        return null
    }

    /** One decoded clip: codec + its external texture + frame pacing state. */
    private class ActiveDecoder(
        val decoder: ClipDecoder,
    ) {
        val surfaceTexture: SurfaceTexture
        val textureId: Int
        val frameAvailable = AtomicBoolean(false)
        val isRunning = AtomicBoolean(true)

        @Volatile var started = false
            private set

        init {
            val ids = IntArray(1)
            GLES20.glGenTextures(1, ids, 0)
            textureId = ids[0]
            GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, textureId)
            GLES20.glTexParameteri(
                GLES11Ext.GL_TEXTURE_EXTERNAL_OES,
                GLES20.GL_TEXTURE_MIN_FILTER,
                GLES20.GL_LINEAR,
            )
            GLES20.glTexParameteri(
                GLES11Ext.GL_TEXTURE_EXTERNAL_OES,
                GLES20.GL_TEXTURE_MAG_FILTER,
                GLES20.GL_LINEAR,
            )
            GLES20.glTexParameteri(
                GLES11Ext.GL_TEXTURE_EXTERNAL_OES,
                GLES20.GL_TEXTURE_WRAP_S,
                GLES20.GL_CLAMP_TO_EDGE,
            )
            GLES20.glTexParameteri(
                GLES11Ext.GL_TEXTURE_EXTERNAL_OES,
                GLES20.GL_TEXTURE_WRAP_T,
                GLES20.GL_CLAMP_TO_EDGE,
            )
            GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, 0)
            surfaceTexture = SurfaceTexture(textureId).apply {
                setOnFrameAvailableListener { frameAvailable.set(true) }
            }
        }

        fun start(sourceStartUs: Long, sourceEndUs: Long) {
            isRunning.set(true)
            started = true
            decoder.start(sourceStartUs, sourceEndUs, Surface(surfaceTexture))
        }

        fun pulse() {
            if (!isRunning.get() || !started) return
            if (!decoder.pulse()) {
                decoder.stop()
                isRunning.set(false)
            }
        }

        fun release() {
            decoder.stop()
            surfaceTexture.release()
            GLES20.glDeleteTextures(1, intArrayOf(textureId), 0)
        }
    }
}