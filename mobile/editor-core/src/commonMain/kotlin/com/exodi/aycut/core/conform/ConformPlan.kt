package com.exodi.aycut.core.conform

import com.exodi.aycut.core.model.Sequence

/** List of clips whose source rate/canvas differs from the sequence canvas. */
data class ConformTarget(
    val clipId: String,
    val srcWidth: Int,
    val srcHeight: Int,
    val srcFrameRate: Double,
    val dstFrameRate: Double,
) {
    val needsResample: Boolean get() = srcFrameRate != dstFrameRate
}

/** Planner that decides which assets need a conform pass. */
object ConformPlanner {

    fun plan(sequence: Sequence): List<ConformTarget> {
        // Placeholder: source metadata is supplied by a future probe; for now
        // every clip is treated as already conformed (nothing to do).
        return emptyList()
    }
}