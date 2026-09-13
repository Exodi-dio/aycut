package com.exodi.aycut.core.edit

import com.exodi.aycut.core.model.Clip
import com.exodi.aycut.core.model.ClipId
import com.exodi.aycut.core.model.MediaId
import com.exodi.aycut.core.model.Micros
import com.exodi.aycut.core.model.Sequence
import com.exodi.aycut.core.model.TimeRange
import com.exodi.aycut.core.model.Track
import com.exodi.aycut.core.model.TrackId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

private val TRACK = TrackId("v1")

private fun clip(id: String, duration: Micros = 10L, timelineIn: Micros = 0L) =
    Clip(ClipId(id), MediaId("$id.mp4"), TimeRange(0L, duration), timelineIn)

class SequenceEditorTest {

    private fun editor(): SequenceEditor =
        SequenceEditor(Sequence.createEmpty(tracks = listOf(Track.empty(TRACK))))

    @Test
    fun `perform insert makes the clip visible`() {
        val editor = editor()
        editor.perform(InsertClipCommand(TRACK, clip("c1")))
        assertEquals(1, editor.sequence.track(TRACK)?.clips?.size)
    }

    @Test
    fun `undo and redo restore exact state`() {
        val editor = editor()
        editor.perform(InsertClipCommand(TRACK, clip("c1", duration = 10L)))
        editor.perform(SplitClipCommand(TRACK, ClipId("c1"), at = 4L))
        val final = editor.sequence
        assertEquals(2, final.track(TRACK)?.clips?.size)

        assertTrue(editor.undo()) // merge halves
        assertEquals(1, editor.sequence.track(TRACK)?.clips?.size)

        assertTrue(editor.undo()) // remove clip
        assertEquals(0, editor.sequence.track(TRACK)?.clips?.size)
        assertFalse(editor.undo())

        assertTrue(editor.redo())
        assertEquals(1, editor.sequence.track(TRACK)?.clips?.size)
        assertTrue(editor.redo())
        assertEquals(final, editor.sequence)
        assertFalse(editor.redo())
    }

    @Test
    fun `full edit chain roundtrips with undo redo`() {
        val editor = editor()
        val clipA = clip("a", duration = 20L, timelineIn = 0L)
        val clipB = clip("b", duration = 10L, timelineIn = 30L)
        editor.perform(InsertClipCommand(TRACK, clipA))
        editor.perform(InsertClipCommand(TRACK, clipB))
        editor.perform(TrimStartCommand(TRACK, ClipId("a"), newSourceStart = 5L))
        editor.perform(SplitClipCommand(TRACK, ClipId("b"), at = 35L))
        editor.perform(MoveClipCommand(TRACK, ClipId("b#2"), newTimelineIn = 40L))

        val final = editor.sequence

        repeat(5) { editor.undo() }
        assertEquals(0, editor.sequence.track(TRACK)?.clips?.size)

        repeat(5) { editor.redo() }
        assertEquals(final, editor.sequence)
    }

    @Test
    fun `new edit clears the redo stack`() {
        val editor = editor()
        editor.perform(InsertClipCommand(TRACK, clip("a")))
        editor.undo()
        assertTrue(editor.redo())
        editor.perform(RemoveClipCommand(TRACK, ClipId("a")))
        assertFalse(editor.redo())
    }

    @Test
    fun `playhead clamps when the sequence shrinks`() {
        val editor = editor()
        editor.perform(InsertClipCommand(TRACK, clip("a", duration = 100L)))
        editor.seek(80L)
        assertEquals(80L, editor.playhead)
        editor.undo() // clip removed, duration -> 0
        assertEquals(0L, editor.playhead)
    }

    @Test
    fun `seek clamps to sequence duration`() {
        val editor = editor()
        editor.perform(InsertClipCommand(TRACK, clip("a", duration = 100L)))
        editor.seek(5_000L)
        assertEquals(100L, editor.playhead)
        editor.seek(-50L)
        assertEquals(0L, editor.playhead)
    }
}