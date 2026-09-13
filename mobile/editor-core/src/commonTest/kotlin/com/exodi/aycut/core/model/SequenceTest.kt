package com.exodi.aycut.core.model

import com.exodi.aycut.core.math.FrameRate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class SequenceTest {

    @Test
    fun `empty 1080p30 sequence has zero duration`() {
        val sequence = Sequence.createEmpty()
        assertEquals(1920, sequence.width)
        assertEquals(1080, sequence.height)
        assertEquals(30.0, sequence.frameRate)
        assertEquals(0L, sequence.durationMicros)
    }

    @Test
    fun `timebase derives from the decimal frame rate by default`() {
        assertEquals(FrameRate.P30, Sequence.createEmpty().timebase)
        assertEquals(FrameRate.NTSC_30, Sequence(1920, 1080, 29.97).timebase)
        assertEquals(FrameRate.P25, Sequence(1920, 1080, 25.0).timebase)
    }

    @Test
    fun `frame helpers use the exact timebase grid`() {
        val sequence = Sequence.createEmpty(
            tracks = listOf(
                Track.empty(TrackId("v1")).plusClip(
                    Clip(ClipId("c1"), MediaId("a.mp4"), TimeRange(0L, 1_000_000L), 0L),
                ),
            ),
        )
        assertEquals(0L, sequence.frameAt(0L))
        assertEquals(15L, sequence.frameAt(500_000L))
        assertEquals(31L, sequence.frameCount)
    }

    @Test
    fun `duration is the max across all tracks`() {
        val v1 = TrackId("v1")
        val a1 = TrackId("a1")
        val sequence = Sequence.createEmpty(
            tracks = listOf(
                Track.empty(v1).plusClip(
                    Clip(ClipId("c1"), MediaId("a.mp4"), TimeRange(0L, 10L), 0L),
                ),
                Track.empty(a1).plusClip(
                    Clip(ClipId("c2"), MediaId("b.m4a"), TimeRange(0L, 20L), 0L),
                ),
            ),
        )
        assertEquals(20L, sequence.durationMicros)
    }

    @Test
    fun `withTrack replaces by id or appends`() {
        val v1 = TrackId("v1")
        val v2 = TrackId("v2")
        val sequence = Sequence.createEmpty(tracks = listOf(Track.empty(v1)))

        val replaced = sequence.withTrack(Track.empty(v1))
        assertEquals(1, replaced.tracks.size)
        assertNotNull(replaced.track(v1))

        val appended = sequence.withTrack(Track.empty(v2))
        assertEquals(2, appended.tracks.size)
        assertEquals(listOf(v1, v2), appended.tracks.map { it.id })
    }
}