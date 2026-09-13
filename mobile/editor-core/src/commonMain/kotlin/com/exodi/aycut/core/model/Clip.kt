package com.exodi.aycut.core.model

import com.exodi.aycut.core.math.SourceProjection

/** Opaque identity of a source media asset (the imported file). */
@JvmInline
value class MediaId(val raw: String)

/** Opaque identity of a clip on the timeline. */
@JvmInline
value class ClipId(val raw: String)

/**
 * One usage of a media asset on the timeline.
 *
 * [sourceRange] is the region of the source media that plays back and
 * [timelineIn] is where the clip starts on its track. [playRate] is the
 * playback speed (source microseconds per timeline microsecond; 1.0 is real
 * time) and [reverse] plays the source backwards, so the timeline span and
 * the source mapping are projected by [SourceProjection] rather than stored.
 */
data class Clip(
    val id: ClipId,
    val media: MediaId,
    val sourceRange: TimeRange,
    val timelineIn: Micros,
    val playRate: Double = 1.0,
    val reverse: Boolean = false,
) {
    init {
        require(playRate.isFinite() && playRate > 0.0) {
            "playRate must be finite and positive, was $playRate"
        }
    }

    /** Length of this clip on the timeline: source span divided by speed. */
    val timelineDuration: Micros
        get() = SourceProjection.timelineDurationOf(sourceRange.durationMicros, playRate)

    val timelineEnd: Micros
        get() = timelineIn + timelineDuration

    val timelineRange: TimeRange
        get() = TimeRange(timelineIn, timelineDuration)

    /** Source clock position for a timeline time inside this clip. */
    fun sourceTimeAt(timelineTime: Micros): Micros {
        require(timelineTime in timelineRange) {
            "timelineTime $timelineTime outside clip range $timelineRange"
        }
        return SourceProjection.forward(
            timelineTime = timelineTime,
            timelineIn = timelineIn,
            timelineEnd = timelineEnd,
            sourceRangeStart = sourceRange.start,
            sourceRangeEnd = sourceRange.end,
            playRate = playRate,
            reverse = reverse,
        )
    }
}