package com.exodi.aycut.core.model

/** Opaque identity of a source media asset (the imported file). */
@JvmInline
value class MediaId(val raw: String)

/** Opaque identity of a clip on the timeline. */
@JvmInline
value class ClipId(val raw: String)

/**
 * One usage of a media asset on the timeline.
 *
 * [sourceRange] is the region of the source media that plays back;
 * [timelineIn] is where the clip starts on its track. With unit speed the
 * clip duration equals [sourceRange.durationMicros], so the timeline end is
 * derived rather than stored.
 */
data class Clip(
    val id: ClipId,
    val media: MediaId,
    val sourceRange: TimeRange,
    val timelineIn: Micros,
) {
    val timelineEnd: Micros
        get() = timelineIn + sourceRange.durationMicros

    val timelineRange: TimeRange
        get() = TimeRange(timelineIn, sourceRange.durationMicros)

    /** Source clock position for a timeline time inside this clip. */
    fun sourceTimeAt(timelineTime: Micros): Micros {
        require(timelineTime in timelineRange) {
            "timelineTime $timelineTime outside clip range $timelineRange"
        }
        return sourceRange.start + (timelineTime - timelineIn)
    }
}