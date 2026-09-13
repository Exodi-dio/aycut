package com.exodi.aycut.core.edit

import com.exodi.aycut.core.model.Clip
import com.exodi.aycut.core.model.ClipId
import com.exodi.aycut.core.model.Micros
import com.exodi.aycut.core.model.Sequence
import com.exodi.aycut.core.model.TimeRange
import com.exodi.aycut.core.model.Track
import com.exodi.aycut.core.model.TrackId

private fun Sequence.trackOrThrow(trackId: TrackId) =
    track(trackId) ?: error("no track ${trackId.raw}")

/**
 * Remove [clipId] and close the resulting gap: every later clip on the same
 * track (timelineIn >= the removed clip's timelineEnd) shifts left by the
 * removed clip's timeline span. The removed clip is captured on apply so the
 * inverse can re-insert it and shift those clips back to their exact
 * positions.
 */
data class RippleDeleteCommand(
    val trackId: TrackId,
    val clipId: ClipId,
) : EditCommand {

    private var removed: Clip? = null

    override fun apply(sequence: Sequence): Sequence {
        val track = sequence.trackOrThrow(trackId)
        val clip = track.clip(clipId) ?: error("clip ${clipId.raw} not on track")
        removed = clip
        val span = clip.timelineDuration
        val edited = track.clips.mapNotNull { c ->
            when {
                c.id == clipId -> null
                c.timelineIn >= clip.timelineEnd -> c.copy(timelineIn = c.timelineIn - span)
                else -> c
            }
        }
        return sequence.withTrack(Track(track.id, edited, track.type))
    }

    override fun invert(): EditCommand =
        RippleDeleteRestoreCommand(trackId, removed ?: error("RippleDeleteCommand never applied"))
}

/** Inverse of [RippleDeleteCommand]: shift the displaced clips back and re-insert [removed]. */
private class RippleDeleteRestoreCommand(
    private val trackId: TrackId,
    private val removed: Clip,
) : EditCommand {

    override fun apply(sequence: Sequence): Sequence {
        val track = sequence.trackOrThrow(trackId)
        val span = removed.timelineDuration
        val restored = track.clips
            .map { c ->
                if (c.timelineIn >= removed.timelineIn) c.copy(timelineIn = c.timelineIn + span) else c
            }
            .plus(removed)
            .sortedBy { it.timelineIn }
        return sequence.withTrack(Track(track.id, restored, track.type))
    }

    override fun invert(): EditCommand =
        RippleDeleteCommand(trackId, removed.id)
}

/**
 * Trim the source head of [clipId]: the new source range runs from
 * [newSourceStart] to the clip's existing source end, so the timeline
 * duration is recomputed and every later clip on the track ripples by that
 * delta (left when the clip shrank, right when it grew). The clip stays
 * anchored at its original timelineIn so the following material keeps its
 * relative spacing and the track never overlaps. Invert retrims back to the
 * original source start, which reverses the delta and restores every clip.
 */
data class RippleTrimCommand(
    val trackId: TrackId,
    val clipId: ClipId,
    val newSourceStart: Micros,
) : EditCommand {

    private var original: Clip? = null

    override fun apply(sequence: Sequence): Sequence {
        val track = sequence.trackOrThrow(trackId)
        val clip = track.clip(clipId) ?: error("clip ${clipId.raw} not on track")
        original = clip
        val remaining = clip.sourceRange.end - newSourceStart
        val replacement = clip.copy(sourceRange = TimeRange(newSourceStart, remaining))
        val delta = replacement.timelineDuration - clip.timelineDuration
        val edited = track.clips.map { c ->
            when {
                c.id == clipId -> replacement
                c.timelineIn >= clip.timelineEnd -> c.copy(timelineIn = c.timelineIn + delta)
                else -> c
            }
        }
        return sequence.withTrack(Track(track.id, edited, track.type))
    }

    override fun invert(): EditCommand =
        RippleTrimCommand(trackId, clipId, original?.sourceRange?.start ?: newSourceStart)
}

/**
 * Slip [clipId] over its source media without moving it on the timeline:
 * timelineIn and the timeline span stay fixed while only sourceRange.start
 * changes by [deltaMicros], clamped to >= 0 (the upper media boundary is
 * unknown to the engine, so only the lower clamp applies). Invert slips back
 * by the delta that was actually applied.
 */
data class SlipCommand(
    val trackId: TrackId,
    val clipId: ClipId,
    val deltaMicros: Micros,
) : EditCommand {

    private var slippedBy: Micros? = null

    override fun apply(sequence: Sequence): Sequence {
        val track = sequence.trackOrThrow(trackId)
        val clip = track.clip(clipId) ?: error("clip ${clipId.raw} not on track")
        val newStart = (clip.sourceRange.start + deltaMicros).coerceAtLeast(0L)
        slippedBy = newStart - clip.sourceRange.start
        val replacement = clip.copy(sourceRange = TimeRange(newStart, clip.sourceRange.durationMicros))
        return sequence.withTrack(track.replaceClip(clipId, replacement))
    }

    override fun invert(): EditCommand {
        val slippedBy = slippedBy ?: error("SlipCommand never applied")
        return SlipCommand(trackId, clipId, -slippedBy)
    }
}

/**
 * Insert [clip] at its timelineIn, removing every existing clip on the track
 * whose timeline span overlaps [clip.timelineIn, clip.timelineEnd) so the
 * lane stays sorted and non-overlapping; clips in a gap before or after the
 * insert span are kept. A clip id already present on the track is rejected.
 * The removed clips are captured on apply so the inverse can re-insert them
 * and drop the inserted clip.
 */
data class OverwriteInsertCommand(
    val trackId: TrackId,
    val clip: Clip,
) : EditCommand {

    private var removed: List<Clip>? = null

    override fun apply(sequence: Sequence): Sequence {
        val track = sequence.trackOrThrow(trackId)
        require(track.clip(clip.id) == null) { "clip ${clip.id.raw} already on track" }
        val range = clip.timelineRange
        removed = track.clips.filter { range.overlaps(it.timelineRange) }
        val kept = track.clips.filterNot { range.overlaps(it.timelineRange) }
        return sequence.withTrack(Track(track.id, (kept + clip).sortedBy { it.timelineIn }, track.type))
    }

    override fun invert(): EditCommand =
        OverwriteInsertRestoreCommand(trackId, clip, removed ?: error("OverwriteInsertCommand never applied"))
}

/** Inverse of [OverwriteInsertCommand]: drop the inserted clip and restore the removed ones. */
private class OverwriteInsertRestoreCommand(
    private val trackId: TrackId,
    private val inserted: Clip,
    private val removed: List<Clip>,
) : EditCommand {

    override fun apply(sequence: Sequence): Sequence {
        val track = sequence.trackOrThrow(trackId)
        val withoutInserted = track.clips.filterNot { it.id == inserted.id }
        val restored = (withoutInserted + removed).sortedBy { it.timelineIn }
        return sequence.withTrack(Track(track.id, restored, track.type))
    }

    override fun invert(): EditCommand =
        OverwriteInsertCommand(trackId, inserted)
}