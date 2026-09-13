package com.exodi.aycut.core.timeline

import com.exodi.aycut.core.edit.SplitClipCommand
import com.exodi.aycut.core.edit.TrimEndCommand
import com.exodi.aycut.core.edit.TrimStartCommand
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
import kotlin.test.assertNull

private val TRACK = TrackId("v1")
private val CLIP = ClipId("c1")
private val MEDIA = MediaId("clip.mp4")

private fun sequenceWithClip(
    sourceStart: Micros = 0L,
    duration: Micros = 10L,
    timelineIn: Micros = 0L,
): Sequence =
    Sequence.createEmpty(
        tracks = listOf(
            Track.empty(TRACK).plusClip(
                Clip(CLIP, MEDIA, TimeRange(sourceStart, duration), timelineIn),
            ),
        ),
    )

class TimeMapperTest {

    @Test
    fun `maps interior timeline time to exact source time`() {
        val sequence = sequenceWithClip(sourceStart = 100L, duration = 10L, timelineIn = 5L)
        val position = TimeMapper.sourcePositionAt(sequence, TRACK, 7L)
        assertEquals(SourcePosition(MEDIA, 102L), position)
    }

    @Test
    fun `maps clip endpoints to the right source times`() {
        val sequence = sequenceWithClip(sourceStart = 0L, duration = 10L, timelineIn = 0L)
        assertEquals(0L, TimeMapper.sourcePositionAt(sequence, TRACK, 0L)?.sourceTime)
        assertEquals(9L, TimeMapper.sourcePositionAt(sequence, TRACK, 9L)?.sourceTime)
        assertNull(TimeMapper.sourcePositionAt(sequence, TRACK, 10L)) // past the clip
    }

    @Test
    fun `returns null inside a gap`() {
        val v1 = TrackId("v1")
        val sequence = Sequence.createEmpty(
            tracks = listOf(
                Track.empty(v1).plusClip(Clip(CLIP, MEDIA, TimeRange(0L, 5L), 0L)),
            ),
        )
        assertNull(TimeMapper.sourcePositionAt(sequence, v1, 6L))
    }

    @Test
    fun `unknown track and empty track map to null`() {
        val sequence = sequenceWithClip()
        assertNull(TimeMapper.sourcePositionAt(sequence, TrackId("missing"), 0L))
        val empty = Sequence.createEmpty(tracks = listOf(Track.empty(TRACK)))
        assertNull(TimeMapper.sourcePositionAt(empty, TRACK, 0L))
    }

    @Test
    fun `start trim keeps mapping for the surviving part and removes the head`() {
        val original = sequenceWithClip(sourceStart = 100L, duration = 10L, timelineIn = 0L)
        val trimmed = TrimStartCommand(TRACK, CLIP, newSourceStart = 103L).apply(original)
        // Surviving clip: timeline [3..10), source [103..110).
        for (timelineTime in 3L until 10L) {
            assertEquals(
                TimeMapper.sourcePositionAt(original, TRACK, timelineTime),
                TimeMapper.sourcePositionAt(trimmed, TRACK, timelineTime),
                "mapping diverged at $timelineTime after start trim",
            )
        }
        assertEquals(103L, TimeMapper.sourcePositionAt(trimmed, TRACK, 3L)?.sourceTime)
        assertNull(TimeMapper.sourcePositionAt(trimmed, TRACK, 2L)) // head removed
    }

    @Test
    fun `end trim keeps mapping unchanged for the surviving part`() {
        val trimmed = TrimEndCommand(TRACK, CLIP, newSourceEnd = 7L).apply(sequenceWithClip())
        val at = 4L
        assertEquals(
            TimeMapper.sourcePositionAt(sequenceWithClip(), TRACK, at),
            TimeMapper.sourcePositionAt(trimmed, TRACK, at),
        )
        assertNull(TimeMapper.sourcePositionAt(trimmed, TRACK, 7L)) // removed tail
    }

    @Test
    fun `split is identity on the source mapping`() {
        val original = sequenceWithClip(duration = 10L, timelineIn = 0L)
        val split = SplitClipCommand(TRACK, CLIP, at = 4L).apply(original)

        for (timelineTime in 0L until 10L) {
            assertEquals(
                TimeMapper.sourcePositionAt(original, TRACK, timelineTime),
                TimeMapper.sourcePositionAt(split, TRACK, timelineTime),
                "mapping diverged at $timelineTime after split",
            )
        }
    }

    @Test
    fun `split boundary belongs to the right half`() {
        val original = sequenceWithClip(duration = 10L, timelineIn = 0L)
        val split = SplitClipCommand(TRACK, CLIP, at = 4L).apply(original)
        val atBoundary = TimeMapper.sourcePositionAt(split, TRACK, 4L)
        assertEquals("c1#2", split.track(TRACK)?.clipAt(4L)?.id?.raw)
        assertEquals(4L, atBoundary?.sourceTime)
    }

    @Test
    fun `maps through a double speed clip`() {
        val sequence = Sequence.createEmpty(
            tracks = listOf(
                Track.empty(TRACK).plusClip(
                    Clip(CLIP, MEDIA, TimeRange(0L, 10L), 0L, playRate = 2.0),
                ),
            ),
        )
        assertEquals(SourcePosition(MEDIA, 4L), TimeMapper.sourcePositionAt(sequence, TRACK, 2L))
        assertNull(TimeMapper.sourcePositionAt(sequence, TRACK, 5L)) // clip ends at 5us
    }

    @Test
    fun `maps through a reversed clip`() {
        val sequence = Sequence.createEmpty(
            tracks = listOf(
                Track.empty(TRACK).plusClip(
                    Clip(CLIP, MEDIA, TimeRange(0L, 10L), 0L, reverse = true),
                ),
            ),
        )
        assertEquals(SourcePosition(MEDIA, 9L), TimeMapper.sourcePositionAt(sequence, TRACK, 0L))
        assertEquals(SourcePosition(MEDIA, 5L), TimeMapper.sourcePositionAt(sequence, TRACK, 4L))
        assertEquals(SourcePosition(MEDIA, 0L), TimeMapper.sourcePositionAt(sequence, TRACK, 9L))
    }
}