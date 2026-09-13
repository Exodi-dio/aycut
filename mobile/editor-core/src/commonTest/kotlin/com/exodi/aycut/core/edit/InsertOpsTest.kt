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

private val TRACK = TrackId("v1")

private fun clip(id: String, duration: Micros = 1_000_000L, timelineIn: Micros = 0L) =
    Clip(ClipId(id), MediaId("$id.mp4"), TimeRange(0L, duration), timelineIn)

private fun emptySequence(): Sequence = Sequence.createEmpty(tracks = listOf(Track.empty(TRACK)))

private fun Sequence.singleTrack(): Track = track(TRACK)!!

class InsertOpsTest {

    @Test
    fun `three point insert shifts later clips right and inverts exactly`() {
        val original = InsertClipCommand(
            TRACK, clip("a", duration = 10_000_000L, timelineIn = 5_000_000L),
        ).apply(emptySequence())
            .let { InsertClipCommand(TRACK, clip("b", duration = 10_000_000L, timelineIn = 40_000_000L)).apply(it) }

        val command = ThreePointInsertCommand(
            trackId = TRACK,
            clip = clip("new", duration = 10_000_000L, timelineIn = 999_999_999L),
            atMicros = 25_000_000L,
        )
        val inserted = command.apply(original)

        // the inserted clip lands at atMicros, its own timelineIn ignored
        val insertedClip = inserted.singleTrack().clip(ClipId("new"))!!
        assertEquals(25_000_000L, insertedClip.timelineIn)

        // every later clip shifted right by the inserted timeline duration
        val b = inserted.singleTrack().clip(ClipId("b"))!!
        assertEquals(50_000_000L, b.timelineIn) // 40M + 10M
        assertEquals(60_000_000L, b.timelineEnd)
        assertEquals(5_000_000L, inserted.singleTrack().clip(ClipId("a"))?.timelineIn)

        // sorted and non-overlapping
        assertEquals(
            listOf(5_000_000L, 25_000_000L, 50_000_000L),
            inserted.singleTrack().clips.map { it.timelineIn },
        )
        assertEquals(60_000_000L, inserted.durationMicros)

        assertEquals(original, command.invert().apply(inserted))
    }

    @Test
    fun `three point insert into the body of a clip is rejected`() {
        val seq = InsertClipCommand(TRACK, clip("a", duration = 10_000_000L)).apply(emptySequence())
        assertFailsWith<IllegalStateException> {
            ThreePointInsertCommand(TRACK, clip("new"), atMicros = 5_000_000L).apply(seq)
        }
        assertFailsWith<IllegalArgumentException> {
            ThreePointInsertCommand(TRACK, clip("new"), atMicros = -1L).apply(emptySequence())
        }
    }

    @Test
    fun `replace with source keeps the timeline window and inverts exactly`() {
        val original = InsertClipCommand(
            TRACK,
            Clip(ClipId("c1"), MediaId("old.mp4"), TimeRange(0L, 10_000_000L), timelineIn = 5_000_000L),
        ).apply(emptySequence())

        val command = ReplaceWithSourceCommand(
            trackId = TRACK,
            clipId = ClipId("c1"),
            replacement = Clip(
                id = ClipId("other"),
                media = MediaId("new.mp4"),
                sourceRange = TimeRange(1_000_000L, 5_000_000L),
                timelineIn = 0L,
                playRate = 2.0,
            ),
        )
        val replaced = command.apply(original)

        val after = replaced.singleTrack().clip(ClipId("c1"))!!
        assertEquals(MediaId("new.mp4"), after.media)
        assertEquals(2.0, after.playRate)
        // source range rescaled to oldDuration * newRate / oldRate, start preserved
        assertEquals(TimeRange(1_000_000L, 20_000_000L), after.sourceRange)
        assertEquals(5_000_000L, after.timelineIn) // original timeline window kept
        val old = original.singleTrack().clip(ClipId("c1"))!!
        assertEquals(old.timelineDuration, after.timelineDuration)
        assertEquals(old.timelineEnd, after.timelineEnd)

        assertEquals(original, command.invert().apply(replaced))
    }

    @Test
    fun `replace with source requires the clip to exist`() {
        assertFailsWith<IllegalStateException> {
            ReplaceWithSourceCommand(TRACK, ClipId("missing"), clip("other")).apply(emptySequence())
        }
    }
}