package com.exodi.aycut.core.export

import com.exodi.aycut.core.model.Clip
import com.exodi.aycut.core.model.ClipId
import com.exodi.aycut.core.model.MediaId
import com.exodi.aycut.core.model.Sequence
import com.exodi.aycut.core.model.TimeRange
import com.exodi.aycut.core.model.Track
import com.exodi.aycut.core.model.TrackId
import com.exodi.aycut.core.profile.Profile
import kotlin.test.Test
import kotlin.test.assertEquals

private val TRACK = TrackId("v1")

/** 30 whole frames at 30fps: 30 x 33_333us = 999_990us, an exact grid boundary. */
private fun populatedSequence(frameRate: Double = 30.0): Sequence = Sequence.createEmpty(
    frameRate = frameRate,
    tracks = listOf(
        Track(
            TRACK,
            listOf(Clip(ClipId("c1"), MediaId("m1"), TimeRange(0L, 999_990L), 0L)),
        ),
    ),
)

class MasterManifestTest {

    @Test
    fun `assemble with a profile derives frameCount from the sequence`() {
        val sequence = populatedSequence()
        val manifest = ExportPlanner.assemble(sequence, Profile.P1080P30, "out.mp4")

        assertEquals(sequence.frameCount, manifest.frameCount)
        assertEquals(30L, manifest.frameCount) // 30 whole frames cover the span

        assertEquals(1920, manifest.width)
        assertEquals(1080, manifest.height)
        assertEquals(12_000_000, manifest.videoBitrate)
        assertEquals(192_000, manifest.audioBitrate)
        assertEquals(30.0, manifest.frameRate)
        assertEquals("out.mp4", manifest.outputFileName)
        assertEquals(sequence, manifest.sequence)
    }

    @Test
    fun `assemble with an export request picks the frame rate from the source sequence`() {
        val sequence = populatedSequence(frameRate = 24.0)
        val manifest = ExportPlanner.assemble(
            ExportRequest(
                profileName = "ignored",
                sourceSequence = sequence,
                outputFileName = "timeline.mp4",
            ),
        )

        assertEquals(sequence.frameRate, manifest.profile.frameRate)
        assertEquals(24.0, manifest.frameRate)
        assertEquals(sequence.width, manifest.width)
        assertEquals(sequence.height, manifest.height)
        assertEquals(sequence.frameCount, manifest.frameCount)
        assertEquals("timeline.mp4", manifest.outputFileName)
    }
}