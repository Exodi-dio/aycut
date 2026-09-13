package com.exodi.aycut.core.render

import com.exodi.aycut.core.effect.ClipTransition
import com.exodi.aycut.core.effect.Effect
import com.exodi.aycut.core.effect.TransitionKind
import com.exodi.aycut.core.math.FrameRate
import com.exodi.aycut.core.media.MediaAsset
import com.exodi.aycut.core.media.MediaAssetRegistry
import com.exodi.aycut.core.model.Clip
import com.exodi.aycut.core.model.ClipId
import com.exodi.aycut.core.model.MediaId
import com.exodi.aycut.core.model.Micros
import com.exodi.aycut.core.model.Sequence
import com.exodi.aycut.core.model.Track
import com.exodi.aycut.core.model.TrackId
import com.exodi.aycut.core.model.TrackType
import com.exodi.aycut.core.model.TimeRange
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RenderResolverTest {

    private val registry = MediaAssetRegistry.EMPTY
        .register(MediaAsset(MediaId("video"), "Video", "video/mp4", 10_000_000L, 1920, 1080, 0))
        .register(MediaAsset(MediaId("audio"), "Audio", "audio/mp4", 10_000_000L, 0, 0, 0))

    private val p30 = FrameRate.P30

    private fun videoClip(id: String, sourceIn: Micros, sourceOut: Micros, timelineIn: Micros) =
        Clip(
            id = ClipId(id),
            media = MediaId("video"),
            sourceRange = TimeRange(sourceIn, sourceOut),
            timelineIn = timelineIn,
        )

    private fun audioClip(id: String, timelineIn: Micros, gain: Double) =
        Clip(
            id = ClipId(id),
            media = MediaId("audio"),
            sourceRange = TimeRange(0L, 10_000_000L),
            timelineIn = timelineIn,
            gain = gain,
        )

    private fun sequence(vararg tracks: Track) =
        Sequence(width = 1920, height = 1080, frameRate = p30.framesPerSecond, tracks = tracks.asList())

    @Test
    fun `resolves a single clip into one frame with timecode`() {
        val sequence = sequence(
            Track(
                TrackId("t0"), listOf(videoClip("c", 0L, 1_000_000L, 0L)), TrackType.VIDEO),
        )
        val frame = RenderResolver(sequence, registry).resolve(500_000L)

        assertEquals(500_000L, frame.micros)
        assertEquals(15L, frame.frameIndex)
        assertEquals("00:00:00:15", frame.timecode)
        assertEquals(1, frame.videoLayers.size)
        val layer = frame.videoLayers.first()
        assertEquals(MediaId("video"), layer.mediaId)
        assertEquals(500_000L, layer.sourceTime)
        assertEquals(1f, layer.opacity, 1e-6f)
        assertEquals(0.0, frame.audioGain)
    }

    @Test
    fun `resolves the end of the sequence without a clip`() {
        val sequence = sequence(
            Track(TrackId("t0"), listOf(videoClip("c", 0L, 1_000_000L, 0L))),
        )
        val frame = RenderResolver(sequence, registry).resolve(sequence.durationMicros)
        assertEquals(0, frame.videoLayers.size)
        assertEquals(0.0, frame.audioGain)
    }

    @Test
    fun `audio lanes drive the mix and never produce picture layers`() {
        val sequence = sequence(
            Track(TrackId("t0"), listOf(audioClip("a", 0L, 0.5)), TrackType.AUDIO),
        )
        val frame = RenderResolver(sequence, registry).resolve(500_000L)

        assertEquals(0, frame.videoLayers.size)
        assertEquals(0.5, frame.audioGain, 1e-12)
    }

    @Test
    fun `cross dissolve layers the previous clip behind the incoming one`() {
        val dissolve = ClipTransition(TransitionKind.CROSS_DISSOLVE, 1_000_000L)
        val first = videoClip("a", 0L, 1_000_000L, 0L)
        val second = videoClip("b", 0L, 1_000_000L, 1_000_000L).copy(transitionIn = dissolve)
        val sequence = sequence(
            Track(TrackId("t0"), listOf(first, second)),
        )

        val frame = RenderResolver(sequence, registry).resolve(1_500_000L)

        assertEquals(2, frame.videoLayers.size)
        val outgoing = frame.videoLayers[0]
        val incoming = frame.videoLayers[1]
        assertEquals(MediaId("video"), outgoing.mediaId)
        assertEquals(500_000L, outgoing.sourceTime)
        assertEquals(1f, outgoing.opacity, 1e-6f)
        assertEquals(0.5f, incoming.opacity, 1e-6f)
    }

    @Test
    fun `fade to black ramps a lone clip that has no predecessor`() {
        val fade = ClipTransition(TransitionKind.FADE_TO_BLACK, 1_000_000L)
        val clip = videoClip("c", 0L, 1_000_000L, 0L).copy(transitionIn = fade)
        val sequence = sequence(
            Track(TrackId("t0"), listOf(clip)),
        )

        val before = RenderResolver(sequence, registry).resolve(0L)
        assertEquals(0, before.videoLayers.size)

        val mid = RenderResolver(sequence, registry).resolve(250_000L)
        assertEquals(1, mid.videoLayers.size)
        assertEquals(0.25f, mid.videoLayers.first().opacity, 1e-6f)
    }

    @Test
    fun `later tracks render on top in principle`() {
        val sequence = sequence(
            Track(TrackId("t0"), listOf(videoClip("a", 0L, 1_000_000L, 0L))),
            Track(TrackId("t1"), listOf(videoClip("b", 0L, 1_000_000L, 0L))),
        )

        val frame = RenderResolver(sequence, registry).resolve(500_000L)

        assertEquals(2, frame.videoLayers.size)
        assertTrue(frame.videoLayers[0].mediaId == MediaId("video"))
        assertTrue(frame.videoLayers[1].mediaId == MediaId("video"))
    }

    @Test
    fun `fully transparent clips drop their layer`() {
        val clip = videoClip("c", 0L, 1_000_000L, 0L).copy(
            effects = listOf(
                Effect(id = com.exodi.aycut.core.effect.CoreEffectIds.OPACITY_ID,
                    params = mapOf("value" to com.exodi.aycut.core.effect.ParameterValue.Num(0.0))),
            ),
        )
        val sequence = sequence(Track(TrackId("t0"), listOf(clip)))

        val frame = RenderResolver(sequence, registry).resolve(500_000L)

        assertEquals(0, frame.videoLayers.size)
    }
}