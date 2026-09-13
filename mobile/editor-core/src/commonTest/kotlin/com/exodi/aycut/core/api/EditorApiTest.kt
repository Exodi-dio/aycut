package com.exodi.aycut.core.api

import com.exodi.aycut.core.edit.InsertClipCommand
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
import kotlin.test.assertNull
import kotlin.test.assertTrue

private val TRACK = TrackId("v1")

private fun clip(id: String, duration: Micros = 1_000_000L, timelineIn: Micros = 0L) =
    Clip(ClipId(id), MediaId("$id.mp4"), TimeRange(0L, duration), timelineIn)

private fun seededEditor(): DefaultEditorApi =
    DefaultEditorApi(Sequence.createEmpty(tracks = listOf(Track.empty(TRACK))))

class EditorApiTest {

    @Test
    fun `default editor starts from an empty sequence`() {
        val api = DefaultEditorApi()
        assertEquals(Sequence.createEmpty(), api.sequence)
        assertEquals(0L, api.sequence.durationMicros)
        assertEquals(0L, api.playhead)
    }

    @Test
    fun `insertClip adds a one second clip at atMicros without touching the playhead`() {
        val api = DefaultEditorApi()
        api.insertClip("c1", "m1", atMicros = 5_000_000L)

        assertEquals(0L, api.playhead)
        val c1 = api.sequence.tracks.single().clip(ClipId("c1"))!!
        assertEquals(5_000_000L, c1.timelineIn)
        assertEquals(1_000_000L, c1.timelineDuration)
        assertEquals(1_000_000L, c1.sourceRange.durationMicros)
        assertEquals(MediaId("m1"), c1.media)
        assertEquals(1.0, c1.playRate)
        assertEquals(6_000_000L, api.sequence.durationMicros)

        // still untouched on a second insert until a seek lands
        api.playhead = 500_000L
        api.insertClip("c2", "m2", atMicros = 7_000_000L)
        assertEquals(500_000L, api.playhead)
        assertEquals(2, api.sequence.tracks.single().clips.size)
        assertEquals(8_000_000L, api.sequence.durationMicros)
    }

    @Test
    fun `perform undo redo move through the editor and restore exact state`() {
        val api = seededEditor()
        api.perform(InsertClipCommand(TRACK, clip("a", duration = 10_000_000L)))
        api.perform(InsertClipCommand(TRACK, clip("b", duration = 5_000_000L, timelineIn = 20_000_000L)))
        val twoClips = api.sequence

        assertTrue(api.undo())
        val oneClip = api.sequence
        assertTrue(api.undo())
        assertEquals(0, api.sequence.tracks.single().clips.size)
        assertFalse(api.undo())

        assertTrue(api.redo())
        assertEquals(oneClip, api.sequence)
        assertTrue(api.redo())
        assertEquals(twoClips, api.sequence)
        assertFalse(api.redo())
    }

    @Test
    fun `playhead seek clamps to the sequence duration`() {
        val api = seededEditor()
        api.perform(InsertClipCommand(TRACK, clip("a", duration = 1_000_000L)))
        api.playhead = 999_999L
        assertEquals(999_999L, api.playhead)
        api.playhead = 2_000_000L
        assertEquals(1_000_000L, api.playhead)
        api.playhead = -5L
        assertEquals(0L, api.playhead)
    }

    @Test
    fun `rippleDeleteAt removes the covering clip and shifts the rest left`() {
        val api = seededEditor()
        api.perform(InsertClipCommand(TRACK, clip("a", duration = 30L)))
        api.perform(InsertClipCommand(TRACK, clip("b", duration = 10L, timelineIn = 50L)))

        api.rippleDeleteAt(15L) // inside clip a

        val track = api.sequence.tracks.single()
        assertNull(track.clip(ClipId("a")))
        assertEquals(listOf("b"), track.clips.map { it.id.raw })
        assertEquals(20L, track.clip(ClipId("b"))?.timelineIn) // slid into a's former slot
        assertEquals(30L, api.sequence.durationMicros)
    }

    @Test
    fun `rippleDeleteAt over a gap or an empty sequence is a no-op`() {
        val api = seededEditor()
        api.perform(InsertClipCommand(TRACK, clip("a", duration = 30L)))
        api.rippleDeleteAt(40L) // between the clips / past the end
        assertEquals(1, api.sequence.tracks.single().clips.size)

        val empty = DefaultEditorApi()
        empty.rippleDeleteAt(0L) // no track at all
        assertEquals(Sequence.createEmpty(), empty.sequence)
    }
}