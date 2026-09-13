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
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

private val TRACK = TrackId("v1")

private fun clip(
    id: String,
    sourceStart: Micros = 0L,
    duration: Micros = 10L,
    timelineIn: Micros = 0L,
): Clip = Clip(ClipId(id), MediaId("$id.mp4"), TimeRange(sourceStart, duration), timelineIn)

private fun sequenceWithOneTrack(vararg clips: Clip): Sequence =
    Sequence.createEmpty(tracks = listOf(Track(TRACK, clips.toList())))

class ProOpsTest {

    @Test
    fun `ripple delete shifts later clips left by the removed span and stays sorted`() {
        val original = sequenceWithOneTrack(
            clip("c1", duration = 10L, timelineIn = 0L),
            clip("c2", duration = 5L, timelineIn = 15L),
            clip("c3", duration = 5L, timelineIn = 30L),
        )
        val after = RippleDeleteCommand(TRACK, ClipId("c1")).apply(original)
        val clips = after.track(TRACK)!!.clips
        assertEquals(listOf("c2", "c3"), clips.map { it.id.raw })
        assertEquals(listOf(5L, 20L), clips.map { it.timelineIn })
        assertEquals(clips.map { it.timelineIn }.sorted(), clips.map { it.timelineIn })
    }

    @Test
    fun `ripple delete undo restores the exact original sequence`() {
        val original = sequenceWithOneTrack(
            clip("c1", duration = 10L, timelineIn = 0L),
            clip("c2", duration = 5L, timelineIn = 15L),
            clip("c3", duration = 5L, timelineIn = 30L),
        )
        val editor = SequenceEditor(original)
        editor.perform(RippleDeleteCommand(TRACK, ClipId("c1")))
        assertEquals(listOf(5L, 20L), editor.sequence.track(TRACK)!!.clips.map { it.timelineIn })

        assertTrue(editor.undo())
        assertEquals(original, editor.sequence)
        assertFalse(editor.undo())
    }

    @Test
    fun `ripple trim shorter shifts later clips left by the delta`() {
        val original = sequenceWithOneTrack(
            clip("c1", sourceStart = 100L, duration = 10L, timelineIn = 0L),
            clip("c2", duration = 5L, timelineIn = 15L),
            clip("c3", duration = 5L, timelineIn = 30L),
        )
        val command = RippleTrimCommand(TRACK, ClipId("c1"), newSourceStart = 104L)
        val after = command.apply(original)
        val clips = after.track(TRACK)!!.clips
        assertEquals(6L, clips.first { it.id == ClipId("c1") }.timelineDuration)
        assertEquals(listOf(0L, 11L, 26L), clips.map { it.timelineIn })

        assertEquals(original, command.invert().apply(after))
    }

    @Test
    fun `ripple trim longer shifts later clips right by the delta`() {
        val original = sequenceWithOneTrack(
            clip("c1", sourceStart = 100L, duration = 10L, timelineIn = 0L),
            clip("c2", duration = 5L, timelineIn = 15L),
            clip("c3", duration = 5L, timelineIn = 30L),
        )
        val command = RippleTrimCommand(TRACK, ClipId("c1"), newSourceStart = 96L)
        val after = command.apply(original)
        val clips = after.track(TRACK)!!.clips
        assertEquals(14L, clips.first { it.id == ClipId("c1") }.timelineDuration)
        assertEquals(listOf(0L, 19L, 34L), clips.map { it.timelineIn })

        assertEquals(original, command.invert().apply(after))
    }

    @Test
    fun `ripple trim undo restores the exact original sequence`() {
        val original = sequenceWithOneTrack(
            clip("c1", sourceStart = 100L, duration = 10L, timelineIn = 0L),
            clip("c2", duration = 5L, timelineIn = 15L),
        )
        val editor = SequenceEditor(original)
        editor.perform(RippleTrimCommand(TRACK, ClipId("c1"), newSourceStart = 104L))
        assertEquals(listOf(11L), editor.sequence.track(TRACK)!!.clips.drop(1).map { it.timelineIn })

        assertTrue(editor.undo())
        assertEquals(original, editor.sequence)
    }

    @Test
    fun `slip keeps timeline span and in point while moving the source window`() {
        val original = sequenceWithOneTrack(clip("c1", sourceStart = 50L, duration = 10L, timelineIn = 5L))
        val command = SlipCommand(TRACK, ClipId("c1"), deltaMicros = 4L)
        val after = command.apply(original)
        val slipped = after.track(TRACK)!!.clip(ClipId("c1"))!!
        assertEquals(54L, slipped.sourceRange.start)
        assertEquals(5L, slipped.timelineIn)
        assertEquals(10L, slipped.timelineDuration)
        assertEquals(15L, slipped.timelineEnd)

        assertEquals(original, command.invert().apply(after))
    }

    @Test
    fun `slip clamps the source start at zero for a negative delta`() {
        val original = sequenceWithOneTrack(clip("c1", sourceStart = 3L, duration = 10L, timelineIn = 5L))
        val command = SlipCommand(TRACK, ClipId("c1"), deltaMicros = -10L)
        val after = command.apply(original)
        assertEquals(0L, after.track(TRACK)!!.clip(ClipId("c1"))!!.sourceRange.start)

        assertEquals(original, command.invert().apply(after))
    }

    @Test
    fun `overwrite insert removes only the clips that overlap the insert span`() {
        val original = sequenceWithOneTrack(
            clip("c1", duration = 10L, timelineIn = 0L),
            clip("c2", duration = 10L, timelineIn = 15L),
            clip("c3", duration = 10L, timelineIn = 40L),
        )
        val after = OverwriteInsertCommand(TRACK, clip("x", duration = 6L, timelineIn = 12L)).apply(original)
        val clips = after.track(TRACK)!!.clips
        assertEquals(listOf("c1", "x", "c3"), clips.map { it.id.raw })
        assertEquals(listOf(0L, 12L, 40L), clips.map { it.timelineIn })
    }

    @Test
    fun `overwrite insert into a gap keeps existing clips`() {
        val original = sequenceWithOneTrack(
            clip("c1", duration = 10L, timelineIn = 0L),
            clip("c2", duration = 10L, timelineIn = 30L),
        )
        val after = OverwriteInsertCommand(TRACK, clip("x", duration = 5L, timelineIn = 15L)).apply(original)
        assertEquals(listOf("c1", "x", "c2"), after.track(TRACK)!!.clips.map { it.id.raw })
    }

    @Test
    fun `overwrite insert undo restores the removed clips`() {
        val original = sequenceWithOneTrack(
            clip("c1", duration = 10L, timelineIn = 0L),
            clip("c2", duration = 10L, timelineIn = 15L),
        )
        val editor = SequenceEditor(original)
        editor.perform(OverwriteInsertCommand(TRACK, clip("x", duration = 6L, timelineIn = 12L)))
        assertEquals(listOf("c1", "x"), editor.sequence.track(TRACK)!!.clips.map { it.id.raw })

        assertTrue(editor.undo())
        assertEquals(original, editor.sequence)
    }

    @Test
    fun `overwrite insert rejects a clip id already on the track`() {
        val original = sequenceWithOneTrack(clip("c1", duration = 10L, timelineIn = 0L))
        assertFailsWith<IllegalArgumentException> {
            OverwriteInsertCommand(TRACK, clip("c1", duration = 5L, timelineIn = 20L)).apply(original)
        }
    }

    @Test
    fun `bounded undo keeps only the last two commands`() {
        val editor = SequenceEditor(sequenceWithOneTrack(), undoLimit = 2)
        editor.perform(InsertClipCommand(TRACK, clip("a", duration = 10L)))
        editor.perform(InsertClipCommand(TRACK, clip("b", duration = 10L, timelineIn = 20L)))
        editor.perform(InsertClipCommand(TRACK, clip("c", duration = 10L, timelineIn = 40L)))

        assertTrue(editor.canUndo)
        assertEquals(2, editor.undoDepth)

        assertTrue(editor.undo())
        assertTrue(editor.undo())
        assertEquals(listOf("a"), editor.sequence.track(TRACK)!!.clips.map { it.id.raw })
        assertFalse(editor.canUndo)
        assertFalse(editor.undo())
    }
}