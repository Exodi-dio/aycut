package com.exodi.aycut.core.math

import com.exodi.aycut.core.model.Micros
import kotlin.math.roundToLong

/**
 * Projection between the timeline clock and the source clock of a clip,
 * accounting for playback speed ([playRate]) and direction ([reverse]).
 *
 * All editor timing is integer microseconds. A timeline offset is converted
 * to a source offset by nearest-microsecond scaling (ties round up), then
 * clamped into the clip's source span so the half-open source range
 * `[start, end)` is never left and the mapping is onto the inhabitable
 * source microseconds `[start, end - 1]`.
 *
 * The math is overflow-guarded: rates or offsets whose product would escape
 * the `Long` microsecond grid are rejected instead of silently wrapping.
 */
object SourceProjection {

    private const val PRODUCTION_RATE = "playRate must be finite and positive"

    /**
     * Timeline span of a source span of [sourceDurationMicros] played at
     * [playRate] (source microseconds per timeline microsecond).
     *
     * Rounded to the nearest microsecond and clamped to at least 1 so a clip
     * always has an inhabited timeline span (an extreme slow-mo of a single
     * source microsecond still occupies one timeline microsecond).
     */
    fun timelineDurationOf(sourceDurationMicros: Micros, playRate: Double): Micros {
        require(sourceDurationMicros > 0L) {
            "sourceDurationMicros must be positive, was $sourceDurationMicros"
        }
        requireValidRate(playRate)
        val duration = sourceDurationMicros.toDouble() / playRate
        require(duration < Long.MAX_VALUE.toDouble()) {
            "timeline duration overflow at playRate $playRate"
        }
        return if (duration < 1.0) 1L else duration.roundToLong()
    }

    /**
     * Source span consumed by [timelineDurationMicros] of playback at
     * [playRate]; the inverse of [timelineDurationOf] at the rounding level.
     */
    fun sourceSpanForTimeline(timelineDurationMicros: Micros, playRate: Double): Micros {
        require(timelineDurationMicros >= 0L) {
            "timelineDurationMicros must be non-negative, was $timelineDurationMicros"
        }
        requireValidRate(playRate)
        return scaled(timelineDurationMicros, playRate)
    }

    /**
     * Source clock time for a timeline time inside the clip span
     * `[timelineIn, timelineEnd)`. Always returns a value in
     * `[sourceRangeStart, sourceRangeEnd)`; with [reverse] the playback
     * advances backwards through the source.
     */
    fun forward(
        timelineTime: Micros,
        timelineIn: Micros,
        timelineEnd: Micros,
        sourceRangeStart: Micros,
        sourceRangeEnd: Micros,
        playRate: Double,
        reverse: Boolean,
    ): Micros {
        require(timelineTime >= timelineIn && timelineTime < timelineEnd) {
            "timelineTime $timelineTime outside clip span [$timelineIn, $timelineEnd)"
        }
        requireValidRate(playRate)
        val sourceSpan = sourceRangeEnd - sourceRangeStart
        val timelineSpan = timelineEnd - timelineIn
        val offset = timelineTime - timelineIn
        val index = sourceIndexAt(offset, timelineSpan, sourceSpan, playRate, reverse)
        return sourceRangeStart + index
    }

    /**
     * Timeline time whose forward projection lands at (or nearest to)
     * [sourceTime]. Exact for unit speed; approximate under fractional rates.
     * The result is always within `[timelineIn, timelineEnd)`.
     */
    fun inverse(
        sourceTime: Micros,
        timelineIn: Micros,
        timelineEnd: Micros,
        sourceRangeStart: Micros,
        sourceRangeEnd: Micros,
        playRate: Double,
        reverse: Boolean,
    ): Micros {
        require(sourceTime >= sourceRangeStart && sourceTime < sourceRangeEnd) {
            "sourceTime $sourceTime outside source span [$sourceRangeStart, $sourceRangeEnd)"
        }
        requireValidRate(playRate)
        val offset = sourceTime - sourceRangeStart
        val timelineSpan = timelineEnd - timelineIn
        val k = scaled(offset, 1.0 / playRate).coerceIn(0L, timelineSpan - 1L)
        val local = if (reverse) (timelineSpan - 1L) - k else k
        return timelineIn + local
    }

    /**
     * Source index (relative to the source range start) for a timeline
     * [offset] within the clip.
     *
     * Forward plays source 0..S-1; reverse starts at the last source
     * microsecond and walks back to 0, so the forward and reverse paths are
     * mirrored around the span midpoint.
     */
    private fun sourceIndexAt(
        offset: Micros,
        timelineSpan: Micros,
        sourceSpan: Micros,
        playRate: Double,
        reverse: Boolean,
    ): Micros {
        val k = if (reverse) (timelineSpan - 1L) - offset else offset
        val index = scaled(k, playRate)
        return index.coerceIn(0L, sourceSpan - 1L)
    }

    /** Round [k] scaled by [rate] to the nearest microsecond, overflow-guarded. */
    private fun scaled(k: Micros, rate: Double): Micros {
        val scaled = k.toDouble() * rate
        require(scaled < Long.MAX_VALUE.toDouble()) {
            "source projection overflow: $k x $rate"
        }
        return scaled.roundToLong()
    }

    private fun requireValidRate(playRate: Double) {
        require(playRate.isFinite() && playRate > 0.0) {
            "$PRODUCTION_RATE, was $playRate"
        }
    }
}