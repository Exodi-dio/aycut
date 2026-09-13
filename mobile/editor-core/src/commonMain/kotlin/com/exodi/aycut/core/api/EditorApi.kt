package com.exodi.aycut.core.api

import com.exodi.aycut.core.edit.EditCommand
import com.exodi.aycut.core.edit.RippleDeleteCommand
import com.exodi.aycut.core.edit.SequenceEditor
import com.exodi.aycut.core.edit.ThreePointInsertCommand
import com.exodi.aycut.core.model.Clip
import com.exodi.aycut.core.model.ClipId
import com.exodi.aycut.core.model.MediaId
import com.exodi.aycut.core.model.Micros
import com.exodi.aycut.core.model.Sequence
import com.exodi.aycut.core.model.TimeRange
import com.exodi.aycut.core.model.Track
import com.exodi.aycut.core.model.TrackId

private val FIRST_TRACK_ID = TrackId("v1")

/**
 * The engine's public facade. Keeps the timeline model and edit machinery
 * behind one surface so the Compose UI (and tests) always talk to the same
 * entry point. The facade is stateful: it wraps a [SequenceEditor] plus the
 * playhead and exposes only intent-level operations.
 */
interface EditorApi {

    val sequence: Sequence
    var playhead: Micros

    fun perform(command: EditCommand)
    fun undo(): Boolean
    fun redo(): Boolean

    fun insertClip(clipId: String, mediaId: String, atMicros: Micros)
    fun rippleDeleteAt(micros: Micros)
}

/** In-memory implementation of the facade. */
class DefaultEditorApi(initial: Sequence = Sequence.createEmpty()) : EditorApi {

    private val editor = SequenceEditor(initial)

    override val sequence: Sequence get() = editor.sequence

    override var playhead: Micros
        get() = editor.playhead
        set(value) = editor.seek(value)

    override fun perform(command: EditCommand) = editor.perform(command)
    override fun undo() = editor.undo()
    override fun redo() = editor.redo()

    override fun insertClip(clipId: String, mediaId: String, atMicros: Micros) {
        val trackId = editor.sequence.tracks.firstOrNull()?.id ?: ensureFirstTrack()
        val clip = Clip(
            id = ClipId(clipId),
            media = MediaId(mediaId),
            sourceRange = TimeRange(0L, 1_000_000L),
            timelineIn = atMicros,
        )
        editor.perform(ThreePointInsertCommand(trackId, clip, atMicros))
    }

    override fun rippleDeleteAt(micros: Micros) {
        val track = editor.sequence.tracks.firstOrNull() ?: return
        val clip = track.clipAt(micros) ?: return
        editor.perform(RippleDeleteCommand(track.id, clip.id))
    }

    /** Create the default video lane when the sequence has no tracks yet. */
    private fun ensureFirstTrack(): TrackId {
        editor.perform(EnsureFirstTrackCommand(FIRST_TRACK_ID))
        return FIRST_TRACK_ID
    }
}

/** Add the default first lane absent a track; the inverse drops the empty lane. */
private class EnsureFirstTrackCommand(private val trackId: TrackId) : EditCommand {
    override fun apply(sequence: Sequence): Sequence = sequence.withTrack(Track.empty(trackId))
    override fun invert(): EditCommand = DropFirstTrackCommand(trackId)
}

/** Remove the freshly created empty lane; refuses a lane that already carries clips. */
private class DropFirstTrackCommand(private val trackId: TrackId) : EditCommand {
    override fun apply(sequence: Sequence): Sequence {
        val track = sequence.track(trackId) ?: error("no track ${trackId.raw}")
        check(track.clips.isEmpty()) { "track ${trackId.raw} is not empty" }
        return sequence.copy(tracks = sequence.tracks.filterNot { it.id == trackId })
    }

    override fun invert(): EditCommand = EnsureFirstTrackCommand(trackId)
}