package com.exodi.aycut.core.edit

import com.exodi.aycut.core.math.SourceProjection
import com.exodi.aycut.core.model.Clip
import com.exodi.aycut.core.model.ClipId
import com.exodi.aycut.core.model.Micros
import com.exodi.aycut.core.model.Sequence
import com.exodi.aycut.core.model.TimeRange
import com.exodi.aycut.core.model.TrackId

private fun Sequence.trackOrThrow(trackId: TrackId) =
    track(trackId) ?: error("no track ${trackId.raw}")

/**
 * Place [clip] on the track at its [Clip.timelineIn], sorted, no overlap.
 */
class InsertClipCommand(
    private val trackId: TrackId,
    val clip: Clip,
) : EditCommand {

    override fun apply(sequence: Sequence): Sequence =
        sequence.withTrack(sequence.trackOrThrow(trackId).plusClip(clip))

    override fun invert(): EditCommand = RemoveClipCommand(trackId, clip.id)
}

/**
 * Remove a clip from the track. Captures the removed clip on apply so the
 * inverse can reinsert the exact instance.
 */
class RemoveClipCommand(
    private val trackId: TrackId,
    val clipId: ClipId,
) : EditCommand {

    private var removed: Clip? = null

    override fun apply(sequence: Sequence): Sequence {
        val track = sequence.trackOrThrow(trackId)
        removed = track.clip(clipId) ?: error("clip ${clipId.raw} not on track")
        return sequence.withTrack(track.removeClip(clipId))
    }

    override fun invert(): EditCommand =
        InsertClipCommand(trackId, removed ?: error("RemoveClipCommand never applied"))
}

/**
 * Trim the start of a clip: the timeline end stays fixed and the cut is
 * taken from the beginning of the source media.
 *
 * The engine only rejects empty or negative results; "may not extend beyond
 * the current media" is a UI/motion policy enforced later by the media
 * registry, so undo can freely restore the pre-trim range.
 */
class TrimStartCommand(
    private val trackId: TrackId,
    val clipId: ClipId,
    private val newSourceStart: Micros,
) : EditCommand {

    private var original: Clip? = null

    override fun apply(sequence: Sequence): Sequence {
        val track = sequence.trackOrThrow(trackId)
        val clip = track.clip(clipId) ?: error("clip ${clipId.raw} not on track")
        original = clip

        val remaining = clip.sourceRange.end - newSourceStart
        val replacement = clip.copy(
            sourceRange = TimeRange(newSourceStart, remaining),
        )
        val timelineIn = clip.timelineEnd - replacement.timelineDuration
        return sequence.withTrack(
            track.replaceClip(clipId, replacement.copy(timelineIn = timelineIn)),
        )
    }

    override fun invert(): EditCommand =
        TrimStartCommand(trackId, clipId, original?.sourceRange?.start ?: newSourceStart)
}

/**
 * Trim the end of a clip: the timeline in-point stays fixed and the cut is
 * taken from the end of the source media.
 */
class TrimEndCommand(
    private val trackId: TrackId,
    val clipId: ClipId,
    private val newSourceEnd: Micros,
) : EditCommand {

    private var original: Clip? = null

    override fun apply(sequence: Sequence): Sequence {
        val track = sequence.trackOrThrow(trackId)
        val clip = track.clip(clipId) ?: error("clip ${clipId.raw} not on track")
        original = clip

        val replacement = clip.copy(
            sourceRange = TimeRange(clip.sourceRange.start, newSourceEnd - clip.sourceRange.start),
        )
        return sequence.withTrack(track.replaceClip(clipId, replacement))
    }

    override fun invert(): EditCommand =
        TrimEndCommand(trackId, clipId, original?.sourceRange?.end ?: newSourceEnd)
}

/**
 * Split a clip at a timeline time strictly inside it. The left half keeps the
 * parent id, the right half gets a derived id; both retain the clip's
 * [playRate] and [reverse]. The halves partition the original source span
 * contiguously (a reversed clip keeps the parent half at the tail of the
 * source). Undo merges both halves back into the exact original clip
 * instance.
 *
 * At fractional play rates the split boundary may shift by less than a
 * microsecond so both halves stay source-contiguous.
 */
class SplitClipCommand(
    private val trackId: TrackId,
    val clipId: ClipId,
    private val at: Micros,
) : EditCommand {

    private var original: Clip? = null
    private var rightId: ClipId? = null

    override fun apply(sequence: Sequence): Sequence {
        val track = sequence.trackOrThrow(trackId)
        val clip = track.clip(clipId) ?: error("clip ${clipId.raw} not on track")
        check(at > clip.timelineIn && at < clip.timelineEnd) {
            "split point must be strictly inside the clip"
        }
        original = clip

        val sourceSpan = clip.sourceRange.durationMicros
        require(sourceSpan >= 2L) { "clip media is too short to split" }
        val splitDelta = SourceProjection
            .sourceSpanForTimeline(at - clip.timelineIn, clip.playRate)
            .coerceIn(1L, sourceSpan - 1L)
        val rightId = rightId ?: ClipId("${clip.id.raw}#2")
        this.rightId = rightId

        val left = if (!clip.reverse) {
            clip.copy(sourceRange = TimeRange(clip.sourceRange.start, splitDelta))
        } else {
            clip.copy(
                sourceRange = TimeRange(
                    clip.sourceRange.start + sourceSpan - splitDelta,
                    splitDelta,
                ),
            )
        }
        val right = clip.copy(
            id = rightId,
            sourceRange = if (!clip.reverse) {
                TimeRange(clip.sourceRange.start + splitDelta, sourceSpan - splitDelta)
            } else {
                TimeRange(clip.sourceRange.start, sourceSpan - splitDelta)
            },
            timelineIn = left.timelineEnd,
        )
        return sequence.withTrack(track.replaceClip(clipId, left).plusClip(right))
    }

    override fun invert(): EditCommand =
        MergeClipsCommand(trackId, original ?: error("SplitClipCommand never applied"), rightId ?: error("SplitClipCommand never applied"), at)
}

/**
 * Merge two adjacent halves of a clip back into [original], restoring the
 * original id and ranges. Requires the halves to be adjacent on the timeline
 * and to partition the original source span (in either playback direction).
 */
class MergeClipsCommand(
    private val trackId: TrackId,
    private val original: Clip,
    private val rightId: ClipId,
    private val boundary: Micros,
) : EditCommand {

    override fun apply(sequence: Sequence): Sequence {
        val track = sequence.trackOrThrow(trackId)
        val left = track.clip(original.id) ?: error("left half ${original.id.raw} missing")
        val right = track.clip(rightId) ?: error("right half ${rightId.raw} missing")
        check(right.timelineIn == left.timelineEnd) { "halves are not adjacent on the timeline" }
        check(right.media == left.media) { "halves use different media" }
        val partitions = with(original.sourceRange) {
            start == minOf(left.sourceRange.start, right.sourceRange.start) &&
                end == maxOf(left.sourceRange.end, right.sourceRange.end) &&
                durationMicros == left.sourceRange.durationMicros + right.sourceRange.durationMicros
        }
        check(partitions) { "halves do not partition the original source span" }
        val merged = track.removeClip(rightId).replaceClip(left.id, original)
        return sequence.withTrack(merged)
    }

    override fun invert(): EditCommand =
        SplitClipCommand(trackId, original.id, boundary)
}

/**
 * Move a clip to a new start position on the same track, shift-constrained
 * to keep its duration (overlap with neighbors is rejected).
 */
class MoveClipCommand(
    private val trackId: TrackId,
    val clipId: ClipId,
    private val newTimelineIn: Micros,
) : EditCommand {

    private var original: Clip? = null

    override fun apply(sequence: Sequence): Sequence {
        val track = sequence.trackOrThrow(trackId)
        val clip = track.clip(clipId) ?: error("clip ${clipId.raw} not on track")
        require(newTimelineIn >= 0L) { "timelineIn must be non-negative" }
        original = clip
        return sequence.withTrack(track.replaceClip(clipId, clip.copy(timelineIn = newTimelineIn)))
    }

    override fun invert(): EditCommand =
        MoveClipCommand(trackId, clipId, original?.timelineIn ?: newTimelineIn)
}