package com.exodi.aycut.core.math

import com.exodi.aycut.core.model.Micros

/** How a microsecond value maps onto the whole-frame grid. */
enum class FrameRounding {
    /** Largest frame index whose start is <= [micros]. */
    FLOOR,

    /** Smallest frame index whose start is >= [micros]; exact boundaries do not carry. */
    CEIL,

    /** Nearest frame index (halves round up). */
    ROUND,
}

/**
 * Integer frame-index math on a [FrameRate] grid. Frames are half-open spans
 * `[start, end)`; a boundary instant belongs to the later frame, so the
 * sequence duration in frames is `ceil(duration / frameDuration)`.
 */
object Frames {

    /**
     * Frame index for [micros]. On an exact frame boundary:
     * FLOOR -> the frame starting there, CEIL -> the following frame,
     * ROUND -> the floor index (the boundary is the frame's start).
     */
    fun frameIndexAt(
        micros: Micros,
        frameRate: FrameRate,
        rounding: FrameRounding = FrameRounding.FLOOR,
    ): Long {
        require(micros >= 0L) { "micros must be non-negative, was $micros" }
        val duration = frameRate.frameDurationMicros
        val quotient = micros / duration
        val remainder = micros % duration
        return when (rounding) {
            FrameRounding.FLOOR -> quotient
            FrameRounding.CEIL -> if (remainder == 0L) quotient else quotient + 1L
            FrameRounding.ROUND -> if (remainder * 2L >= duration) quotient + 1L else quotient
        }
    }

    fun frameStartMicros(index: Long, frameRate: FrameRate): Micros =
        index * frameRate.frameDurationMicros

    fun frameEndMicros(index: Long, frameRate: FrameRate): Micros =
        (index + 1L) * frameRate.frameDurationMicros

    /** Microseconds of the frame containing [micros] (FLOOR semantics). */
    fun frameSpanMicros(micros: Micros, frameRate: FrameRate): TimeRangeLite {
        val index = frameIndexAt(micros, frameRate, FrameRounding.FLOOR)
        return TimeRangeLite(frameStartMicros(index, frameRate), frameEndMicros(index, frameRate))
    }

    /** Number of whole frames covering `[0, micros)` (tagged by boundary). */
    fun framesForMicros(micros: Micros, frameRate: FrameRate): Long =
        frameIndexAt(micros, frameRate, FrameRounding.CEIL)

    /** Nearest frame boundary (start or end of the containing frame). */
    fun nearestBoundaryMicros(micros: Micros, frameRate: FrameRate): Micros {
        val span = frameSpanMicros(micros, frameRate)
        return if (micros - span.start < span.end - micros) span.start else span.end
    }
}

/** Minimal half-open microsecond span used by frame grid math. */
data class TimeRangeLite(
    val start: Micros,
    val end: Micros,
) {
    init {
        require(end > start) { "end $end must exceed start $start" }
    }

    val durationMicros: Micros
        get() = end - start
}