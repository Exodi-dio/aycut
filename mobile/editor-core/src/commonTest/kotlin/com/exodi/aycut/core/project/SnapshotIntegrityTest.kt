package com.exodi.aycut.core.project

import com.exodi.aycut.core.model.Clip
import com.exodi.aycut.core.model.ClipId
import com.exodi.aycut.core.model.MediaId
import com.exodi.aycut.core.model.Sequence
import com.exodi.aycut.core.model.TimeRange
import com.exodi.aycut.core.model.Track
import com.exodi.aycut.core.model.TrackId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class SnapshotIntegrityTest {

    private fun snapshotOf(sequence: Sequence): ProjectSnapshot =
        ProjectCodec.decodeSnapshot(ProjectCodec.encode(sequence, "Integrity"))

    private fun minimalSequence(width: Int = 100, height: Int = 100, frameRate: Double = 30.0): Sequence =
        Sequence.createEmpty(width = width, height = height, frameRate = frameRate)

    @Test
    fun `valid v2 snapshot is clean and tracks pass`() {
        val sequence = Sequence.createEmpty(
            tracks = listOf(
                Track.empty(TrackId("v1")).plusClip(
                    Clip(ClipId("c1"), MediaId("a.mp4"), TimeRange(0L, 10L), 0L),
                ),
                Track.empty(TrackId("a1")).plusClip(
                    Clip(ClipId("c2"), MediaId("m.m4a"), TimeRange(1L, 5L), 2L),
                ),
            ),
        )
        val report = SnapshotIntegrity.validate(snapshotOf(sequence))
        assertTrue(report.isClean)
        assertIs<IntegrityCheck.Pass<Int>>(report.version)
        assertIs<IntegrityCheck.Pass<List<Track>>>(report.tracks)
    }

    @Test
    fun `zero width fails integrity`() {
        val report = SnapshotIntegrity.validate(snapshotOf(minimalSequence(width = 0)))
        assertFalse(report.isClean)
        assertEquals("width must be positive", (report.width as IntegrityCheck.Fail).message)
    }

    @Test
    fun `zero height fails integrity`() {
        val report = SnapshotIntegrity.validate(snapshotOf(minimalSequence(height = 0)))
        assertFalse(report.isClean)
        assertEquals("height must be positive", (report.height as IntegrityCheck.Fail).message)
    }

    @Test
    fun `zero frameRate fails integrity`() {
        val report = SnapshotIntegrity.validate(snapshotOf(minimalSequence(frameRate = 0.0)))
        assertFalse(report.isClean)
        assertEquals("frameRate must be positive", (report.frameRate as IntegrityCheck.Fail).message)
    }

    @Test
    fun `unsorted or overlapping clips are rejected`() {
        val unsorted = ProjectSnapshot(
            version = ProjectCodec.CURRENT_VERSION,
            name = "Unsorted",
            width = 100,
            height = 100,
            frameRate = 30.0,
            tracks = listOf(
                SnapshotTrack(
                    id = "t",
                    clips = listOf(
                        SnapshotClip("c1", "a.mp4", sourceStart = 0L, sourceDuration = 10L, timelineIn = 20L),
                        SnapshotClip("c2", "a.mp4", sourceStart = 0L, sourceDuration = 10L, timelineIn = 0L),
                    ),
                ),
            ),
        )
        assertFalse(SnapshotIntegrity.validate(unsorted).isClean)

        val overlapping = unsorted.copy(
            name = "Overlapping",
            tracks = listOf(
                SnapshotTrack(
                    id = "t",
                    clips = listOf(
                        SnapshotClip("c1", "a.mp4", sourceStart = 0L, sourceDuration = 10L, timelineIn = 0L),
                        SnapshotClip("c2", "a.mp4", sourceStart = 0L, sourceDuration = 10L, timelineIn = 5L),
                    ),
                ),
            ),
        )
        assertFalse(SnapshotIntegrity.validate(overlapping).isClean)
    }

    @Test
    fun `version mismatch reports version fail`() {
        val sequence = minimalSequence()
        val decoded = ProjectCodec.decodeSnapshot(ProjectCodec.encode(sequence, "V"))
        val v1 = decoded.copy(version = 1)
        val report = SnapshotIntegrity.validate(v1)
        assertFalse(report.isClean)
        val fail = report.version as IntegrityCheck.Fail
        assertTrue(fail.message.contains("version 1 unsupported"))
    }
}