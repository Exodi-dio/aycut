package com.exodi.aycut.core.effect

import com.exodi.aycut.core.composite.CRect
import com.exodi.aycut.core.composite.NormalizedRect
import com.exodi.aycut.core.model.Clip
import com.exodi.aycut.core.model.Micros

/**
 * Samples the built-in core effects — opacity and transform — off a clip's
 * [Effect] list at a clip-local instant. Pure math; the renderer feeds the
 * results to the GLES state.
 *
 * Keyframe times are clip-local microseconds (0 = the clip's in point),
 * matching [com.exodi.aycut.core.model.Clip.gainAt].
 */
object EffectSampler {

    private const val PARAM_VALUE = "value"
    private const val PARAM_POSITION = "position"
    private const val PARAM_SCALE = "scale"
    private const val PARAM_ROTATION = "rotation"

    /** Floor for the scale multiplier so a rect keeps positive area. */
    private const val MIN_SCALE = 1e-4f

    private val DEFAULT_POSITION = ParameterValue.Point(0.5f, 0.5f)

    /**
     * Opacity of the clip's picture at clip-local [offsetInClip], in `0..1`.
     * The default is fully opaque; the "opacity" effect overrides it, and its
     * value is clamped into the unit range.
     */
    fun effectiveOpacity(clip: Clip, offsetInClip: Micros): Float {
        val effect = clip.effect(CoreEffectIds.OPACITY_ID) ?: return 1f
        val value = effect.valueAt(PARAM_VALUE, offsetInClip)
            ?.asNumber(1.0)
            ?.coerceIn(0.0, 1.0)
            ?: 1.0
        return value.toFloat()
    }

    /**
     * Picture rectangle after the "transform" effect: the effect's normalized
     * canvas "position" (default 0.5,0.5 = center) and "scale" multiplier
     * (default 1.0) recompute [base]. The result may extend past the canvas;
     * the caller drops or clamps via [CRect].
     */
    fun transformRect(base: NormalizedRect, clip: Clip, offsetInClip: Micros): CRect {
        val effect = clip.effect(CoreEffectIds.TRANSFORM_ID) ?: return CRect.of(base)
        val position = effect.valueAt(PARAM_POSITION, offsetInClip, DEFAULT_POSITION).asPoint()
        val scale = (effect.valueAt(PARAM_SCALE, offsetInClip)
            ?.asNumber(1.0)
            ?.coerceAtLeast(0.0) ?: 1.0).toFloat().coerceAtLeast(MIN_SCALE)
        val halfWidth = base.width * scale / 2f
        val halfHeight = base.height * scale / 2f
        val cx = position.x.coerceIn(0f, 1f)
        val cy = position.y.coerceIn(0f, 1f)
        return CRect(cx - halfWidth, cy - halfHeight, cx + halfWidth, cy + halfHeight)
    }

    /**
     * "transform" rotation degrees applied on top of the asset's orientation
     * metadata, measured clockwise on screen. The caller sums it with the
     * asset rotation into the layer's effective rotation.
     */
    fun rotationDegrees(clip: Clip, offsetInClip: Micros): Float {
        val effect = clip.effect(CoreEffectIds.TRANSFORM_ID) ?: return 0f
        return (effect.valueAt(PARAM_ROTATION, offsetInClip)?.asNumber(0.0) ?: 0.0).toFloat()
    }
}