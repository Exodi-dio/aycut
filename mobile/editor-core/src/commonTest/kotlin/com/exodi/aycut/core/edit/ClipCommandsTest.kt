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
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

private val TRACK = TrackId("v1")

private fun clip(id: String, sourceStart: Micros = 0L, duration: Micros = 10L, timelineIn: Micros = 0L) =
    Clip(ClipId(id), MediaId("$id.mp4"), TimeRange(sourceStart, duration), timelineIn)

private fun emptySequence(): Sequence = Sequence.createEmpty(tracks = listOf(Track.empty(TRACK)))

private fun Sequence.singleTrack(): Track = track(TRACK)!!

class ClipCommandsTest {

    @Test
    fun `insert then remove restores the exact original sequence`() {
        val original = emptySequence()
        val withClip = InsertClipCommand(TRACK, clip("c1")).apply(original)
        assertEquals(1, withClip.singleTrack().clips.size)

        val restored = InsertClipCommand(TRACK, clip("c1")).invert().apply(withClip)
        assertEquals(original, restored)
    }

    @Test
    fun `remove captures the clip so its inverse reinserts it exactly`() {
        val inserted = InsertClipCommand(TRACK, clip("c1", duration = 25L)).apply(emptySequence())
        val remove = RemoveClipCommand(TRACK, ClipId("c1"))
        val removed = remove.apply(inserted)
        assertEquals(0, removed.singleTrack().clips.size)

        val restored = remove.invert().apply(removed)
        assertEquals(inserted, restored)
    }

    @Test
    fun `trim start and undo restore the original clip`() {
        val inserted = InsertClipCommand(TRACK, clip("c1", sourceStart = 100L, duration = 10L)).apply(emptySequence())
        val command = TrimStartCommand(TRACK, ClipId("c1"), newSourceStart = 103L)
        val trimmed = command.apply(inserted)

        val trimmedClip = trimmed.singleTrack().clip(ClipId("c1"))!!
        assertEquals(3L, trimmedClip.timelineIn)
        assertEquals(7L, trimmedClip.sourceRange.durationMicros)
        assertEquals(10L, trimmedClip.timelineEnd) // end stayed fixed

        assertEquals(inserted, command.invert().apply(trimmed))
    }

    @Test
    fun `trim end keeps the timeline in-point fixed`() {
        val inserted = InsertClipCommand(TRACK, clip("c1", sourceStart = 100L, duration = 10L)).apply(emptySequence())
        val command = TrimEndCommand(TRACK, ClipId("c1"), newSourceEnd = 105L)
        val trimmed = command.apply(inserted)

        val trimmedClip = trimmed.singleTrack().clip(ClipId("c1"))!!
        assertEquals(0L, trimmedClip.timelineIn)
        assertEquals(5L, trimmedClip.timelineEnd)
        assertEquals(100L, trimmedClip.sourceRange.start)
        assertEquals(inserted, command.invert().apply(trimmed))
    }

    @Test
    fun `trims into an empty or negative clip are rejected`() {
        val inserted = InsertClipCommand(TRACK, clip("c1", sourceStart = 100L, duration = 10L)).apply(emptySequence())
        assertFailsWith<IllegalArgumentException> { TrimStartCommand(TRACK, ClipId("c1"), 110L).apply(inserted) } // empty
        assertFailsWith<IllegalArgumentException> { TrimStartCommand(TRACK, ClipId("c1"), 120L).apply(inserted) } // negative
        assertFailsWith<IllegalArgumentException> { TrimEndCommand(TRACK, ClipId("c1"), 100L).apply(inserted) } // empty
        assertFailsWith<IllegalArgumentException> { TrimEndCommand(TRACK, ClipId("c1"), 50L).apply(inserted) } // negative
        assertFailsWith<IllegalArgumentException> { TrimStartCommand(TRACK, ClipId("c1"), -5L).apply(inserted) } // start < 0
    }

    @Test
    fun `trim can be extended back to the original range (undo path)`() {
        val original = clip("c1", sourceStart = 100L, duration = 10L)
        val inserted = InsertClipCommand(TRACK, original).apply(emptySequence())
        val trimmed = TrimStartCommand(TRACK, ClipId("c1"), 103L).apply(inserted)
        assertEquals(3L, trimmed.singleTrack().clip(ClipId("c1"))?.timelineIn)

        val extended = TrimStartCommand(TRACK, ClipId("c1"), 100L).apply(trimmed)
        assertEquals(inserted, extended)
    }

    @Test
    fun `split produces two clips that merge back into the exact original`() {
        val originalClip = clip("c1", sourceStart = 40L, duration = 10L, timelineIn = 5L)
        val inserted = InsertClipCommand(TRACK, originalClip).apply(emptySequence())
        val command = SplitClipCommand(TRACK, ClipId("c1"), at = 8L)
        val split = command.apply(inserted)

        val clips = split.singleTrack().clips
        assertEquals(2, clips.size)
        val left = clips[0]
        val right = clips[1]
        assertEquals("c1", left.id.raw)
        assertEquals("c1#2", right.id.raw)
        assertEquals(listOf(5L, 8L), listOf(left.timelineIn, right.timelineIn))
        assertEquals(40L + 3L, right.sourceRange.start)
        assertEquals(originalClip.timelineEnd, right.timelineEnd)

        val merged = command.invert().apply(split)
        assertEquals(inserted, merged)
    }

    @Test
    fun `split outside or at the clip boundary is rejected`() {
        val inserted = InsertClipCommand(TRACK, clip("c1", duration = 10L)).apply(emptySequence())
        assertFailsWith<IllegalStateException> { SplitClipCommand(TRACK, ClipId("c1"), 0L).apply(inserted) }
        assertFailsWith<IllegalStateException> { SplitClipCommand(TRACK, ClipId("c1"), 10L).apply(inserted) }
        assertFailsWith<IllegalStateException> { SplitClipCommand(TRACK, ClipId("c1"), 12L).apply(inserted) }
    }

    @Test
    fun `split undo restores the original clip id`() {
        val inserted = InsertClipCommand(TRACK, clip("c1", duration = 10L)).apply(emptySequence())
        val split = SplitClipCommand(TRACK, ClipId("c1"), at = 4L)
        val splitSequence = split.apply(inserted)
        assertNotNull(splitSequence.singleTrack().clip(ClipId("c1#2")))

        val undone = split.invert().apply(splitSequence)
        assertNull(undone.singleTrack().clip(ClipId("c1#2")))
        assertEquals(inserted, undone)
    }

    @Test
    fun `move repositions the clip and its inverse restores position`() {
        val inserted = InsertClipCommand(TRACK, clip("c1", duration = 10L)).apply(emptySequence())
        val command = MoveClipCommand(TRACK, ClipId("c1"), newTimelineIn = 30L)
        val moved = command.apply(inserted)
        assertEquals(30L, moved.singleTrack().clip(ClipId("c1"))?.timelineIn)
        assertEquals(40L, moved.singleTrack().clip(ClipId("c1"))?.timelineEnd)
        assertEquals(inserted, command.invert().apply(moved))
    }

    @Test
    fun `move into a neighbor is rejected`() {
        val inserted = InsertClipCommand(TRACK, clip("c1", duration = 10L))
            .apply(emptySequence())
            .let { InsertClipCommand(TRACK, clip("c2", duration = 10L, timelineIn = 20L)).apply(it) }
        assertFailsWith<IllegalStateException> {
            MoveClipCommand(TRACK, ClipId("c2"), newTimelineIn = 5L).apply(inserted)
        }
        assertFailsWith<IllegalArgumentException> {
            MoveClipCommand(TRACK, ClipId("c2"), newTimelineIn = -1L).apply(inserted)
        }
    }

    @Test
    fun `trim start at double speed keeps the timeline end fixed`() {
        val inserted = InsertClipCommand(
            TRACK,
            clip("c1", sourceStart = 0L, duration = 10L).copy(playRate = 2.0),
        ).apply(emptySequence())
        val trimmed = TrimStartCommand(TRACK, ClipId("c1"), newSourceStart = 2L).apply(inserted)

        val trimmedClip = trimmed.singleTrack().clip(ClipId("c1"))!!
        assertEquals(2L, trimmedClip.sourceRange.start)
        assertEquals(8L, trimmedClip.sourceRange.durationMicros)
        assertEquals(1L, trimmedClip.timelineIn)
        assertEquals(5L, trimmedClip.timelineEnd) // end stayed fixed
        assertEquals(2.0, trimmedClip.playRate)
        val trim = TrimStartCommand(TRACK, ClipId("c1"), newSourceStart = 2L)
        trim.apply(inserted)
        assertEquals(inserted, trim.invert().apply(trimmed))
    }

    @Test
    fun `split at double speed partitions source and preserves speed`() {
        val inserted = InsertClipCommand(
            TRACK,
            clip("c1", sourceStart = 0L, duration = 10L).copy(playRate = 2.0),
        ).apply(emptySequence())
        val split = SplitClipCommand(TRACK, ClipId("c1"), at = 2L).apply(inserted)

        val clips = split.singleTrack().clips
        val left = clips[0]
        val right = clips[1]
        assertEquals(TimeRange(0L, 4L), left.sourceRange)
        assertEquals(TimeRange(4L, 6L), right.sourceRange)
        assertEquals(2.0, left.playRate)
        assertEquals(2.0, right.playRate)
        assertEquals(2L, left.timelineEnd)
        assertEquals(2L, right.timelineIn)
        assertEquals(5L, right.timelineEnd)

        val splitCommand = SplitClipCommand(TRACK, ClipId("c1"), at = 2L)
        splitCommand.apply(inserted)
        assertEquals(inserted, splitCommand.invert().apply(split))
    }

    @Test
    fun `split reversed clip partitions source in reverse`() {
        val inserted = InsertClipCommand(
            TRACK,
            clip("c1", sourceStart = 0L, duration = 10L).copy(reverse = true),
        ).apply(emptySequence())
        val split = SplitClipCommand(TRACK, ClipId("c1"), at = 4L).apply(inserted)

        val clips = split.singleTrack().clips
        val left = clips[0]
        val right = clips[1]
        assertEquals(TimeRange(6L, 4L), left.sourceRange)
        assertEquals(TimeRange(0L, 6L), right.sourceRange)
        assertTrue(left.reverse)
        assertTrue(right.reverse)
        assertEquals(4L, left.timelineEnd)
        assertEquals(4L, right.timelineIn)
        assertEquals(10L, right.timelineEnd)

        val splitCommand = SplitClipCommand(TRACK, ClipId("c1"), at = 4L)
        splitCommand.apply(inserted)
        assertEquals(inserted, splitCommand.invert().apply(split))
    }

    @Test
    fun `split of a media too short to partition is rejected`() {
        val inserted = InsertClipCommand(
            TRACK,
            clip("c1", sourceStart = 0L, duration = 1L).copy(playRate = 0.4),
        ).apply(emptySequence())
        assertFailsWith<IllegalArgumentException> {
            SplitClipCommand(TRACK, ClipId("c1"), at = 1L).apply(inserted)
        }
    }
}