package com.exodi.aycut.core.model

/** Timeline clock unit. All editor timing is integer microseconds. */
typealias Micros = Long

const val MILLIS_PER_MICROS: Micros = 1_000L
const val SECONDS_PER_MICROS: Micros = 1_000_000L

/**
 * A half-open interval on the timeline: [start, end).
 *
 * Start is inclusive, end exclusive, duration is always positive.
 */
data class TimeRange(
    val start: Micros,
    val durationMicros: Micros,
) {
    init {
        require(start >= 0L) { "start must be non-negative, was $start" }
        require(durationMicros > 0L) { "duration must be positive, was $durationMicros" }
    }

    val end: Micros
        get() = start + durationMicros

    operator fun contains(time: Micros): Boolean = time >= start && time < end

    fun overlaps(other: TimeRange): Boolean = start < other.end && other.start < end

    override fun toString(): String = "TimeRange($start..$end)"
}