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
 *
 * Audio behavior applies to any lane that carries sound: [gain] is the
 * static linear level, [audioEnvelope] a keyframed automation that takes
 * precedence over [gain] when present, and [fadeIn]/[fadeOut] are linear
 * attenuation ramps measured from the clip's in/out point in microseconds.
 */
data class Clip(
    val id: ClipId,
    val media: MediaId,
    val sourceRange: TimeRange,
    val timelineIn: Micros,
    val playRate: Double = 1.0,
    val reverse: Boolean = false,
    val gain: Double = 1.0,
    val fadeIn: Micros = 0L,
    val fadeOut: Micros = 0L,
    val audioEnvelope: AudioEnvelope? = null,
) {
    init {
        require(playRate.isFinite() && playRate > 0.0) {
            "playRate must be finite and positive, was $playRate"
        }
        require(gain.isFinite() && gain >= 0.0) {
            "gain must be finite and non-negative, was $gain"
        }
        require(fadeIn >= 0L) { "fadeIn must be non-negative, was $fadeIn" }
        require(fadeOut >= 0L) { "fadeOut must be non-negative, was $fadeOut" }
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

    /**
     * Linear gain at [offsetInClip] microseconds into this clip (0 = its
     * [timelineIn] point on the timeline).
     *
     * The base level is the [audioEnvelope] automation when one is set,
     * otherwise the constant [gain]. [fadeIn] and [fadeOut] are multiplied on
     * top and only attenuate; the envelope may still boost above 1.0. When the
     * fades overlap the ramp is the minimum of the two, pulling below either
     * single ramp.
     */
    fun gainAt(offsetInClip: Micros): Double {
        require(offsetInClip in 0L until timelineDuration) {
            "offsetInClip $offsetInClip outside clip span [0, $timelineDuration)"
        }
        val base = audioEnvelope?.gainAt(offsetInClip) ?: gain
        return base * fadeMultiplier(offsetInClip)
    }

    private fun fadeMultiplier(offsetInClip: Micros): Double {
        val duration = timelineDuration
        var multiplier = 1.0
        if (fadeIn > 0L && offsetInClip < fadeIn) {
            multiplier = offsetInClip.toDouble() / fadeIn.toDouble()
        }
        val fadeOutStart = duration - fadeOut
        if (fadeOut > 0L && offsetInClip >= fadeOutStart) {
            val ramp = (duration.toDouble() - offsetInClip.toDouble()) / fadeOut.toDouble()
            if (ramp < multiplier) multiplier = ramp
        }
        return multiplier.coerceIn(0.0, 1.0)
    }
}