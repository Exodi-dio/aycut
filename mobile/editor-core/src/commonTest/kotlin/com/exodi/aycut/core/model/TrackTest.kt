package com.exodi.aycut.core.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TrackTest {

    private val media = MediaId("clip.mp4")

    private fun clip(id: String, start: Micros, duration: Micros) =
        Clip(ClipId(id), media, TimeRange(start, duration), timelineIn = start)

    @Test
    fun `insert keeps clips sorted by timeline start`() {
        var track = Track.empty(TrackId("v1"))
        track = track.plusClip(clip("b", 10L, 5L))
        track = track.plusClip(clip("a", 0L, 5L))
        track = track.plusClip(clip("c", 20L, 5L))
        assertEquals(listOf("a", "b", "c"), track.clips.map { it.id.raw })
    }

    @Test
    fun `overlapping clips are rejected on construction`() {
        val a = clip("a", 0L, 10L)
        val b = clip("b", 5L, 10L)
        assertFailsWith<IllegalStateException> { Track(TrackId("v1"), listOf(a, b)) }
        assertFailsWith<IllegalStateException> {
            Track.empty(TrackId("v1")).plusClip(a).plusClip(b)
        }
    }

    @Test
    fun `unsorted construction is rejected`() {
        val b = clip("b", 10L, 5L)
        val a = clip("a", 0L, 5L)
        assertFailsWith<IllegalStateException> { Track(TrackId("v1"), listOf(b, a)) }
    }

    @Test
    fun `gaps are allowed and clipAt returns null inside a gap`() {
        val track = Track.empty(TrackId("v1"))
            .plusClip(clip("a", 0L, 10L))
            .plusClip(clip("b", 20L, 10L))
        assertNull(track.clipAt(15L))
        assertEquals("a", track.clipAt(0L)?.id?.raw)
        assertEquals("a", track.clipAt(9L)?.id?.raw)
        assertEquals("b", track.clipAt(20L)?.id?.raw)
    }

    @Test
    fun `clipAt boundary belongs to the later clip`() {
        val track = Track.empty(TrackId("v1"))
            .plusClip(clip("a", 0L, 10L))
            .plusClip(clip("b", 10L, 10L))
        assertEquals("b", track.clipAt(10L)?.id?.raw)
    }

    @Test
    fun `remove and replace maintain invariants`() {
        var track = Track.empty(TrackId("v1"))
            .plusClip(clip("a", 0L, 10L))
            .plusClip(clip("b", 20L, 5L))
        track = track.replaceClip(ClipId("a"), clip("a", 2L, 10L))
        assertEquals(2L, track.clip(ClipId("a"))?.timelineIn)
        track = track.removeClip(ClipId("a"))
        assertNull(track.clip(ClipId("a")))
        assertEquals(1, track.clips.size)
    }

    @Test
    fun `duration is the farthest clip end`() {
        val track = Track.empty(TrackId("v1"))
            .plusClip(clip("a", 0L, 10L))
            .plusClip(clip("b", 40L, 5L))
        assertEquals(45L, track.duration)
    }

    @Test
    fun `lane type defaults to video`() {
        assertEquals(TrackType.VIDEO, Track.empty(TrackId("v1")).type)
        assertEquals(TrackType.VIDEO, Track(TrackId("v1"), emptyList()).type)
    }

    @Test
    fun `lane type is preserved through insert, remove and replace`() {
        val clipA = clip("a", 0L, 10L)
        val clipB = clip("b", 20L, 5L)
        var track = Track.empty(TrackId("a1"), TrackType.AUDIO)
        track = track.plusClip(clipB)
        track = track.plusClip(clipA)
        assertEquals(TrackType.AUDIO, track.type)
        assertEquals(TrackType.AUDIO, track.replaceClip(ClipId("a"), clipA).type)
        assertEquals(TrackType.AUDIO, track.removeClip(ClipId("a")).type)
    }

    @Test
    fun `withType copies the lane under a new kind`() {
        val track = Track.empty(TrackId("t1"), TrackType.VIDEO)
        assertEquals(TrackType.TITLE, track.withType(TrackType.TITLE).type)
        assertEquals(TrackType.VIDEO, track.type)
    }

    @Test
    fun `audio lanes answer clipAt like any lane`() {
        val track = Track.empty(TrackId("a1"), TrackType.AUDIO)
            .plusClip(clip("a", 0L, 10L))
        assertEquals("a", track.clipAt(5L)?.id?.raw)
        assertNull(track.clipAt(11L)?.id?.raw)
    }
}