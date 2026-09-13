package com.exodi.aycut.core.composite

/**
 * Axis-aligned rectangle in a unit space ([0,1] in both axes), used the same
 * way for output (the sequence canvas) and for the source (texture UVs).
 * Left/top origin, right/bottom exclusive-ish edges.
 */
data class NormalizedRect(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
) {
    init {
        require(left in 0f..1f && top in 0f..1f && right in 0f..1f && bottom in 0f..1f)
        require(left < right && top < bottom) { "rect must have positive area" }
    }

    val width: Float get() = right - left
    val height: Float get() = bottom - top

    /** Same shape, moved to fill the width/height bands implied by [aspect]. */
    fun centeredIn(boxWidth: Float, boxHeight: Float): NormalizedRect {
        require(boxWidth in 0f..1f && boxHeight in 0f..1f)
        val w = boxWidth * this.width
        val h = boxHeight * this.height
        val cx = (left + right) / 2f
        val cy = (top + bottom) / 2f
        return NormalizedRect(cx - w / 2f, cy - h / 2f, cx + w / 2f, cy + h / 2f)
    }

    companion object {
        val FULL = NormalizedRect(0f, 0f, 1f, 1f)
    }
}

/**
 * Axis-aligned rectangle in the same unit space but WITHOUT the constraint of
 * [NormalizedRect]'s `0..1` bounds: effect transforms may push a rectangle
 * off the canvas edge. Callers test [intersectsUnit] and clamp with
 * [clampedToUnit] before anything draws.
 */
data class CRect(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
) {
    init {
        require(left < right && top < bottom) { "rect must have positive area" }
    }

    val width: Float get() = right - left
    val height: Float get() = bottom - top

    /** True when at least a sliver of the rect lies inside the unit canvas. */
    fun intersectsUnit(): Boolean = left < 1f && top < 1f && right > 0f && bottom > 0f

    /** Clip the rect to the unit square (call only after [intersectsUnit]). */
    fun clampedToUnit(): NormalizedRect = NormalizedRect(
        left.coerceIn(0f, 1f),
        top.coerceIn(0f, 1f),
        right.coerceIn(0f, 1f),
        bottom.coerceIn(0f, 1f),
    )

    companion object {
        fun of(rect: NormalizedRect): CRect =
            CRect(rect.left, rect.top, rect.right, rect.bottom)
    }
}

/**
 * Clean-room fill/fit layout math for the compositor: where a source frame
 * (of a given aspect ratio) lands on the sequence canvas, and which source
 * sub-window is visible. Frames respect rotation metadata BEFORE this step:
 * pass the displayed (rotated) source aspect.
 */
object RenderLayout {

    /**
     * Letterbox result: the largest axis-aligned sub-rect of the unit canvas
     * that preserves [sourceAspect]. Bars are transparent/background.
     */
    fun fitRect(sourceAspect: Float, targetAspect: Float): NormalizedRect {
        require(sourceAspect > 0f && targetAspect > 0f)
        return if (sourceAspect >= targetAspect) {
            val h = targetAspect / sourceAspect
            NormalizedRect(0f, (1f - h) / 2f, 1f, (1f + h) / 2f)
        } else {
            val w = sourceAspect / targetAspect
            NormalizedRect((1f - w) / 2f, 0f, (1f + w) / 2f, 1f)
        }
    }

    /**
     * Center-crop result: the source sub-window that, sampled to fill the
     * whole unit canvas, preserves [targetAspect] without distortion.
     */
    fun fillUvWindow(sourceAspect: Float, targetAspect: Float): NormalizedRect {
        require(sourceAspect > 0f && targetAspect > 0f)
        return if (sourceAspect >= targetAspect) {
            val w = targetAspect / sourceAspect
            NormalizedRect((1f - w) / 2f, 0f, (1f + w) / 2f, 1f)
        } else {
            val h = sourceAspect / targetAspect
            NormalizedRect(0f, (1f - h) / 2f, 1f, (1f + h) / 2f)
        }
    }
}