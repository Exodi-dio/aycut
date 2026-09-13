package com.exodi.aycut.core.model

/**
 * A single keyframe of an [AudioEnvelope].
 *
 * [offset] is microseconds from the owning clip's timeline start (0 = clip
 * in point); [gain] is a linear amplitude multiplier in [0, +inf) — 1.0 is
 * unity, values above 1.0 boost, values below attenuate.
 */
data class EnvelopePoint(
    val offset: Micros,
    val gain: Double,
) {
    init {
        require(offset >= 0L) { "offset must be non-negative, was $offset" }
        require(gain.isFinite() && gain >= 0.0) {
            "gain must be finite and non-negative, was $gain"
        }
    }
}

/**
 * Piecewise-linear, keyframed gain over a clip's timeline span.
 *
 * Points are strictly sorted by [EnvelopePoint.offset] (no duplicates).
 * [gainAt] interpolates linearly between neighbours and holds the boundary
 * value before the first and after the last point — so a constant-level
 * envelope is simply two identical points.
 *
 * The envelope is defined in clip-local microseconds; callers enter with the
 * offset within the clip (see [Clip.gainAt]).
 */
data class AudioEnvelope(
    val points: List<EnvelopePoint>,
) {
    init {
        require(points.isNotEmpty()) { "envelope must contain at least one point" }
        require(points.zipWithNext().all { it.first.offset < it.second.offset }) {
            "envelope points must be strictly sorted by offset, with no duplicates"
        }
    }

    /** Linear gain at [offset]; holds the boundary value outside the key range. */
    fun gainAt(offset: Micros): Double {
        require(offset >= 0L) { "offset must be non-negative, was $offset" }
        val fadeIn = points.first()
        val fadeOut = points.last()
        if (offset <= fadeIn.offset) return fadeIn.gain
        if (offset >= fadeOut.offset) return fadeOut.gain
        for (i in 0 until points.lastIndex) {
            val lo = points[i]
            val hi = points[i + 1]
            if (offset < hi.offset) {
                val fraction = (offset - lo.offset).toDouble() / (hi.offset - lo.offset).toDouble()
                return lo.gain + (hi.gain - lo.gain) * fraction
            }
        }
        error("unreachable: offset $offset within [${fadeIn.offset}, ${fadeOut.offset})")
    }

    companion object {
        /** Flat unity envelope; the default for clips without automation. */
        val UNITY: AudioEnvelope = AudioEnvelope(listOf(EnvelopePoint(0L, 1.0)))
    }
}