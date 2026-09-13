package com.exodi.aycut.core.model

/** Piecewise param speed on a [Clip]: segments sorted by offset, strict, non-empty. */
data class RampSegment(
    val offsetInClip: Micros,
    val playRate: Double,
) {
    init {
        require(offsetInClip >= 0L) { "ramp offset must be non-negative" }
        require(playRate.isFinite() && playRate > 0.0) { "ramp playRate must be positive, was $playRate" }
    }
}

/**
 * A segmented speed ramp over a clip's timeline span (clip-local offsets).
 * [playRateAt] holds the boundary rate before/after the first/last segment;
 * between segments it jumps to the next (holds, like [AudioEnvelope]).
 */
class SpeedRamp(segments: List<RampSegment>) {

    val segments: List<RampSegment> = segments

    init {
        require(segments.isNotEmpty()) { "ramp must contain at least one segment" }
        require(segments.zipWithNext().all { it.first.offsetInClip < it.second.offsetInClip }) {
            "ramp segments must be strictly sorted by offset"
        }
    }

    val startRate: Double get() = segments.first().playRate
    val endRate: Double get() = segments.last().playRate

    fun playRateAt(offsetInClip: Micros): Double {
        var rate = segments.first().playRate
        for (segment in segments) {
            if (offsetInClip >= segment.offsetInClip) rate = segment.playRate else break
        }
        return rate
    }

    /**
     * Total source media consumed by time [offsetInClip] under this ramp
     * (relative to the clip's own playRate as handled by the caller —
     * this is the pure time-integral of the rate curve).
     */
    fun projectedSourceOffset(offsetInClip: Micros): Micros {
        if (offsetInClip <= 0L) return 0L
        var source = 0.0
        var cursor = 0L
        for (segment in segments) {
            if (offsetInClip <= segment.offsetInClip) break
            val dt = if (offsetInClip < segments.last().offsetInClip) {
                segment.offsetInClip - cursor
            } else {
                offsetInClip - cursor
            }
            source += dt * segment.playRate
            cursor += dt
        }
        return source.toLong()
    }
}