package com.exodi.aycut.core.edit

import com.exodi.aycut.core.model.Micros
import com.exodi.aycut.core.model.Sequence
import kotlin.math.abs

/** A timeline position worth snapping to (clip edge, playhead, marker, ...). */
data class SnapPoint(val label: String, val at: Micros)

/** All default snap candidates for a sequence. */
object SnapCandidates {

    fun point(label: String, at: Micros): SnapPoint = SnapPoint(label, at)

    fun of(sequence: Sequence, edgeMicros: List<Micros> = emptyList()): List<SnapPoint> {
        val points = mutableListOf<SnapPoint>()
        for (track in sequence.tracks) {
            for (clip in track.clips) {
                points += SnapPoint("in:${clip.id.raw}", clip.timelineIn)
                points += SnapPoint("out:${clip.id.raw}", clip.timelineEnd)
            }
        }
        for (edge in edgeMicros) points += SnapPoint("edge", edge)
        return points + sequence.markers.map { SnapPoint("marker:${it.label}", it.at) }
    }
}

/**
 * Snap [raw] to the nearest candidate within [thresholdMicros], if any.
 * Returns null when nothing is close enough. Exact matches snap first; ties
 * prefer the larger position to keep drags feeling responsive.
 */
fun snapPosition(
    raw: Micros,
    candidates: List<SnapPoint>,
    thresholdMicros: Micros,
): SnapPoint? {
    if (candidates.isEmpty()) return null
    var best: SnapPoint? = null
    var bestDelta = Long.MAX_VALUE
    for (candidate in candidates) {
        val delta = abs(raw - candidate.at)
        if (delta <= thresholdMicros) {
            if (best == null || delta < bestDelta || (delta == bestDelta && candidate.at > best!!.at)) {
                best = candidate
                bestDelta = delta
            }
        }
    }
    return best
}