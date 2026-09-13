package com.exodi.aycut.core.project

import com.exodi.aycut.core.model.Clip
import com.exodi.aycut.core.model.ClipId
import com.exodi.aycut.core.model.MediaId
import com.exodi.aycut.core.model.Sequence
import com.exodi.aycut.core.model.TimeRange
import com.exodi.aycut.core.model.Track
import com.exodi.aycut.core.model.TrackId
import com.exodi.aycut.core.model.TrackType
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals

class ProjectCodecFuzzTest {

    @Test
    fun `round trip survives random valid sequences`() {
        val rng = Random(seed = 42_857)
        val frameRates = listOf(24.0, 25.0, 29.97, 30.0, 48.0, 50.0, 60.0, 120.0)
        repeat(200) { i ->
            val seq = randomSequence(rng, frameRates)
            val name = "Fuzz $i"
            val json = ProjectCodec.encode(seq, name)
            val decoded = ProjectCodec.decodeToSequence(json)
            assertEquals(seq, decoded, "round-trip failed at iteration $i")
        }
    }

    private fun randomSequence(rng: Random, frameRates: List<Double>): Sequence {
        val width = rng.nextInt(1, 4097)
        val height = rng.nextInt(1, 2161)
        val frameRate = frameRates.random(rng)
        val masterGain = rng.nextDouble(0.0, 2.0)
        val playhead = rng.nextLong(0, 600_000_000L)
        val trackCount = rng.nextInt(0, 4)
        val tracks = (0 until trackCount).map { t ->
            randomTrack(rng, TrackId("t$t"), t)
        }
        return Sequence(
            width = width,
            height = height,
            frameRate = frameRate,
            tracks = tracks,
            masterGain = masterGain,
            playhead = playhead,
        )
    }

    private fun randomTrack(rng: Random, id: TrackId, index: Int): Track {
        val clipCount = rng.nextInt(0, 5)
        var cursor = 0L
        val clips = (0 until clipCount).map { c ->
            val gap = rng.nextLong(0, 2_000_000L)
            val timelineIn = cursor + gap
            val sourceDuration = rng.nextLong(1, 60_000_001L)
            val sourceStart = rng.nextLong(0, 1_000_000_000L)
            val gain = rng.nextDouble(0.0, 2.0)
            val fadeIn = rng.nextLong(0, sourceDuration + 1)
            val fadeOut = rng.nextLong(0, sourceDuration + 1)
            cursor = timelineIn + sourceDuration
            Clip(
                id = ClipId("c${index}_$c"),
                media = MediaId("media$index.mp4"),
                sourceRange = TimeRange(sourceStart, sourceDuration),
                timelineIn = timelineIn,
                gain = gain,
                fadeIn = fadeIn,
                fadeOut = fadeOut,
            )
        }
        return Track(
            id = id,
            clips = clips,
            type = if (index % 2 == 0) TrackType.VIDEO else TrackType.AUDIO,
        )
    }
}
