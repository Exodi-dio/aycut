package com.exodi.aycut.core.edit

import com.exodi.aycut.core.model.Clip
import com.exodi.aycut.core.model.ClipId
import com.exodi.aycut.core.model.Micros
import com.exodi.aycut.core.model.Sequence
import com.exodi.aycut.core.model.TimeRange
import com.exodi.aycut.core.model.Track
import com.exodi.aycut.core.model.TrackId
import kotlin.math.roundToLong

private fun Sequence.trackOrThrow(trackId: TrackId) =
    track(trackId) ?: error("no track ${trackId.raw}")

/**
 * Three-point insert: place [clip] on the track so its timeline in-point lands
 * exactly on [atMicros] and every clip that currently starts at or after that
 * point is shifted right by the inserted clip's [Clip.timelineDuration]
 * (nothing is overwritten, gaps are preserved). The clip's own
 * [Clip.timelineIn] is ignored — [atMicros] is authoritative.
 *
 * The result stays sorted and non-overlapping: clips starting strictly before
 * [atMicros] are untouched, so [atMicros] must lie in a gap or on a clip
 * boundary; inserting into the body of an existing clip is rejected.
 *
 * The inverse removes the inserted clip and slides the shifted clips back
 * exactly, restoring the pre-edit sequence deterministic from the command
 * fields (no apply-state capture needed).
 */
data class ThreePointInsertCommand(
    val trackId: TrackId,
    val clip: Clip,
    val atMicros: Micros,
) : EditCommand {

    override fun apply(sequence: Sequence): Sequence {
        val track = sequence.trackOrThrow(trackId)
        require(atMicros >= 0L) { "atMicros must be non-negative, was $atMicros" }
        val placed = clip.copy(timelineIn = atMicros)
        val duration = placed.timelineDuration
        val predecessor = track.clips.lastOrNull { it.timelineIn < atMicros }
        check(predecessor == null || predecessor.timelineEnd <= atMicros) {
            "insert point $atMicros lies inside clip ${predecessor?.id?.raw}"
        }
        val shifted = track.clips.map { c ->
            if (c.timelineIn >= atMicros) c.copy(timelineIn = c.timelineIn + duration) else c
        }
        val updated = Track(trackId, (shifted + placed).sortedBy { it.timelineIn }, track.type)
        return sequence.withTrack(updated)
    }

    override fun invert(): EditCommand = object : EditCommand {
        private val duration: Micros = clip.timelineDuration

        override fun apply(sequence: Sequence): Sequence {
            val track = sequence.trackOrThrow(trackId)
            val restored = track.removeClip(clip.id).clips.map { c ->
                if (c.timelineIn > atMicros) c.copy(timelineIn = c.timelineIn - duration) else c
            }
            return sequence.withTrack(Track(trackId, restored, track.type))
        }

        override fun invert(): EditCommand = this@ThreePointInsertCommand
    }
}

/**
 * Replace the clip [clipId] on the track with [replacement] while keeping the
 * original identity and timeline window: the replacement inherits the old
 * clip's id and [Clip.timelineIn] and, when its [Clip.playRate] differs from
 * the old clip's, its source range is rescaled so its [Clip.timelineDuration]
 * equals the old clip's. A replacement at the same rate keeps its own source
 * range (and thus may legitimately lengthen or shorten the timeline slot).
 *
 * The source-range duration is computed with overflow-guarded Long math as
 * `oldDuration * newRate / oldRate` and rounded to the nearest microsecond;
 * the replacement's source start is preserved. The inverse restores the exact
 * pre-replace clip instance.
 */
data class ReplaceWithSourceCommand(
    val trackId: TrackId,
    val clipId: ClipId,
    val replacement: Clip,
) : EditCommand {

    private var original: Clip? = null

    override fun apply(sequence: Sequence): Sequence {
        val track = sequence.trackOrThrow(trackId)
        val old = track.clip(clipId) ?: error("clip ${clipId.raw} not on track")
        original = old

        val rescaled = if (replacement.playRate == old.playRate) {
            replacement
        } else {
            replacement.copy(
                sourceRange = TimeRange(
                    replacement.sourceRange.start,
                    rescaledSourceDuration(old.sourceRange.durationMicros, old.playRate, replacement.playRate),
                ),
            )
        }
        return sequence.withTrack(track.replaceClip(clipId, rescaled.copy(id = old.id, timelineIn = old.timelineIn)))
    }

    override fun invert(): EditCommand =
        ReplaceWithSourceCommand(trackId, clipId, original ?: error("ReplaceWithSourceCommand never applied"))

    private fun rescaledSourceDuration(
        oldDurationMicros: Micros,
        oldRate: Double,
        newRate: Double,
    ): Micros {
        val scaled = oldDurationMicros.toDouble() * (newRate / oldRate)
        require(scaled < Long.MAX_VALUE.toDouble()) { "source range rescale overflow" }
        return scaled.roundToLong().coerceAtLeast(1L)
    }
}