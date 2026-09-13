package com.exodi.aycut.core.composite

import com.exodi.aycut.core.media.MediaAssetRegistry
import com.exodi.aycut.core.model.Micros
import com.exodi.aycut.core.model.Sequence
import com.exodi.aycut.core.timeline.TimeMapper

/** sRGB background color of the sequence canvas. */
data class ColorRgb(val r: Float, val g: Float, val b: Float)

/**
 * What the compositor must draw for one active clip at a playhead: which
 * texture sub-window to sample ([uvWindow]) and where on the canvas to draw
 * it ([outputRect]). Pure, platform-free math; the renderer turns this into
 * GLES state.
 */
data class LayerSpec(
    val mediaId: com.exodi.aycut.core.model.MediaId,
    val rotationDegrees: Int,
    val uvWindow: NormalizedRect,
    val outputRect: NormalizedRect,
    val opacity: Float,
)

/**
 * Resolves the rendered picture of a [Sequence] at any playhead. Bottom track
 * first, later tracks on top; gaps and missing media drop their layer so only
 * the background (or the clips below) show through.
 */
class CompositorGraph(
    private val sequence: Sequence,
    private val media: MediaAssetRegistry,
) {

    /** Display aspect of the sequence canvas. */
    fun aspectRatio(): Float = sequence.width.toFloat() / sequence.height.toFloat()

    fun background(): ColorRgb = ColorRgb(0f, 0f, 0f)

    /**
     * Layers active at [atMicros], bottom track first, ordered for drawing.
     */
    fun layers(atMicros: Micros): List<LayerSpec> {
        val targetAspect = aspectRatio()
        val result = mutableListOf<LayerSpec>()
        for (track in sequence.tracks) {
            val source = TimeMapper.sourcePositionAt(sequence, track.id, atMicros) ?: continue
            val asset = media.asset(source.media) ?: continue
            if (asset.displayedWidth == 0 || asset.displayedHeight == 0) continue // audio-only
            val displayedAspect =
                asset.displayedWidth.toFloat() / maxOf(asset.displayedHeight.toFloat(), 1f)
            result += LayerSpec(
                mediaId = asset.id,
                rotationDegrees = asset.rotationDegrees,
                uvWindow = RenderLayout.fillUvWindow(displayedAspect, targetAspect),
                outputRect = RenderLayout.fitRect(displayedAspect, targetAspect),
                opacity = 1f,
            )
        }
        return result
    }
}