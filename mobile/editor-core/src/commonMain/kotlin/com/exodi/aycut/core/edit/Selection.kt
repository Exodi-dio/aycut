package com.exodi.aycut.core.edit

import com.exodi.aycut.core.model.ClipId
import com.exodi.aycut.core.model.Micros
import com.exodi.aycut.core.model.Sequence
import com.exodi.aycut.core.model.TrackId
import com.exodi.aycut.core.model.TrackType

/**
 * The active UI selection. Pure state the timeline layer reads/writes; the
 * edit commands themselves only know about concrete sequences.
 */
data class TimelineSelection(
    val selectedClipIds: Set<ClipId> = emptySet(),
    val selectedTrackIds: Set<TrackId> = emptySet(),
    val anchorClipId: ClipId? = null,
) {
    fun clipIds(): Set<ClipId> = selectedClipIds

    fun toggle(clipId: ClipId): TimelineSelection = copy(
        selectedClipIds = if (clipId in selectedClipIds) selectedClipIds - clipId
        else selectedClipIds + clipId,
    )

    fun clear(): TimelineSelection = copy(selectedClipIds = emptySet(), anchorClipId = null)
}

/** Which kinds of timeline objects [TimelineSelection] should include. */
enum class SelectionScope { CLIPS, TRACKS, MIXED }

/** Default selection policy helpers. */
object SelectionPolicy {

    /** Select every clip (across all tracks) clip whose head lies at/after [from]. */
    fun selectFrom(sequence: Sequence, fromMicros: Micros, scope: SelectionScope): TimelineSelection {
        val clipIds = if (scope == SelectionScope.TRACKS) emptySet() else
            sequence.tracks.flatMap { track ->
                track.clips.filter { it.timelineIn >= fromMicros }.map { it.id }
            }.toSet()
        return TimelineSelection(
            selectedClipIds = clipIds,
            selectedTrackIds = if (scope == SelectionScope.TRACKS)
                sequence.tracks.filter { it.type == TrackType.TITLE }.map { it.id }.toSet() else emptySet(),
        )
    }
}