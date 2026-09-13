package com.exodi.aycut.core.project

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
import kotlin.test.assertFailsWith

private val V1 = TrackId("v1")
private val A1 = TrackId("a1")

private fun sampleSequence(): Sequence = Sequence.createEmpty(
    tracks = listOf(
        Track.empty(V1).plusClip(
            Clip(ClipId("c1"), MediaId("clip.mp4"), TimeRange(0L, 5L), 0L),
        ),
        Track.empty(A1).plusClip(
            Clip(ClipId("c2"), MediaId("music.m4a"), TimeRange(1L, 3L), 2L),
        ),
    ),
)

class ProjectCodecTest {

    @Test
    fun `encode and decode round trip is identity`() {
        val sequence = sampleSequence()
        val json = ProjectCodec.encode(sequence, "My Project")
        assertEquals(sequence, ProjectCodec.decodeToSequence(json))
    }

    @Test
    fun `snapshot preserves project metadata`() {
        val json = ProjectCodec.encode(sampleSequence(), "Holiday Cut")
        val snapshot = ProjectCodec.decodeSnapshot(json)
        assertEquals("Holiday Cut", snapshot.name)
        assertEquals(1920, snapshot.width)
        assertEquals(1080, snapshot.height)
        assertEquals(30.0, snapshot.frameRate)
        assertEquals(1, snapshot.version)
    }

    @Test
    fun `edited sequence survives a round trip`() {
        val sequence = sampleSequence()
        val trimmed = TrimStartCommand(V1, ClipId("c1"), 2L).apply(sequence)
        val json = ProjectCodec.encode(trimmed, "Trimmed")
        assertEquals(trimmed, ProjectCodec.decodeToSequence(json))
    }

    @Test
    fun `encoding is stable for the same input`() {
        val json1 = ProjectCodec.encode(sampleSequence(), "Stable")
        val json2 = ProjectCodec.encode(sampleSequence(), "Stable")
        assertEquals(json1, json2)
    }

    @Test
    fun `golden snapshot decodes to expected values`() {
        val golden = """
            {"version":1,"name":"Golden","width":640,"height":480,"frameRate":25.0,
             "tracks":[{"id":"v1","clips":[
               {"id":"c1","media":"a.mp4","sourceStart":10,"sourceDuration":4,"timelineIn":0}]}]}
        """.trimIndent()
        val snapshot = ProjectCodec.decodeSnapshot(golden)
        assertEquals("Golden", snapshot.name)
        assertEquals(640, snapshot.width)
        assertEquals(25.0, snapshot.frameRate)
        assertEquals("c1", snapshot.tracks.single().clips.single().id)
        val sequence = ProjectCodec.decodeToSequence(golden)
        assertEquals(4L, sequence.durationMicros)
    }

    @Test
    fun `unknown extra keys are ignored for forward compatibility`() {
        val json = """{"version":1,"name":"Fwd","width":100,"height":100,"frameRate":30.0,
            "tracks":[],"newFutureField":{"a":1}}"""
        assertEquals("Fwd", ProjectCodec.decodeSnapshot(json).name)
    }

    @Test
    fun `newer version is rejected`() {
        val json = """{"version":2,"name":"A","width":100,"height":100,"frameRate":30.0,"tracks":[]}"""
        assertFailsWith<ProjectFormatException> { ProjectCodec.decodeToSequence(json) }
    }

    @Test
    fun `malformed json is rejected`() {
        assertFailsWith<ProjectFormatException> { ProjectCodec.decodeSnapshot("{not json") }
    }

    @Test
    fun `zero or negative durations and starts are rejected`() {
        val zeroDuration = """{"version":1,"name":"A","width":100,"height":100,"frameRate":30.0,
            "tracks":[{"id":"v1","clips":[{"id":"c1","media":"a.mp4","sourceStart":0,"sourceDuration":0,"timelineIn":0}]}]}"""
        val negativeStart = """{"version":1,"name":"B","width":100,"height":100,"frameRate":30.0,
            "tracks":[{"id":"v1","clips":[{"id":"c1","media":"a.mp4","sourceStart":-5,"sourceDuration":4,"timelineIn":0}]}]}"""
        assertFailsWith<ProjectFormatException> { ProjectCodec.decodeToSequence(zeroDuration) }
        assertFailsWith<ProjectFormatException> { ProjectCodec.decodeToSequence(negativeStart) }
    }

    @Test
    fun `duplicate track ids are rejected`() {
        val json = """{"version":1,"name":"C","width":100,"height":100,"frameRate":30.0,
            "tracks":[{"id":"v1","clips":[]},{"id":"v1","clips":[]}]}"""
        assertFailsWith<ProjectFormatException> { ProjectCodec.decodeToSequence(json) }
    }

    @Test
    fun `overlapping clips are rejected at decode`() {
        val overlapping = """{"version":1,"name":"D","width":100,"height":100,"frameRate":30.0,
            "tracks":[{"id":"v1","clips":[
              {"id":"a","media":"a.mp4","sourceStart":0,"sourceDuration":10,"timelineIn":0},
              {"id":"b","media":"a.mp4","sourceStart":0,"sourceDuration":10,"timelineIn":5}]}]}"""
        assertFailsWith<ProjectFormatException> { ProjectCodec.decodeToSequence(overlapping) }
    }
}