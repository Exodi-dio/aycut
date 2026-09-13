package com.exodi.aycut.core.composite

import com.exodi.aycut.core.media.MediaAsset
import com.exodi.aycut.core.media.MediaAssetRegistry
import com.exodi.aycut.core.model.Clip
import com.exodi.aycut.core.model.ClipId
import com.exodi.aycut.core.model.Sequence
import com.exodi.aycut.core.model.Track
import com.exodi.aycut.core.model.TrackId
import com.exodi.aycut.core.model.TimeRange
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private const val WIDTH = 1920
private const val HEIGHT = 1080

private val v = MediaAsset(
    id = com.exodi.aycut.core.model.MediaId("v"),
    displayName = "v.mp4",
    mimeType = "video/mp4",
    durationMicros = 1_000_000L,
    width = 1280,
    height = 720,
    rotationDegrees = 0,
)

private fun clip(id: String, trackStart: Long, trackEnd: Long, mediaId: String = "v") =
    Clip(
        id = ClipId(id),
        media = com.exodi.aycut.core.model.MediaId(mediaId),
        sourceRange = TimeRange(trackStart, trackEnd - trackStart),
        timelineIn = trackStart,
    )

private fun sequence(vararg tracks: Track): Sequence =
    Sequence.createEmpty(WIDTH, HEIGHT, 30.0).let { base ->
        tracks.fold(base) { s, t -> s.withTrack(t) }
    }

class RenderLayoutTest {

    @Test
    fun `fit adds vertical bars for wider source`() {
        val rect = RenderLayout.fitRect(16f / 9f, 4f / 3f)
        assertTrue(rect.width == 1f)
        assertEquals(0.75f, rect.height, 1e-4f)
        assertEquals(0.125f, rect.top, 1e-4f)
        assertEquals(0.875f, rect.bottom, 1e-4f)
    }

    @Test
    fun `fit adds horizontal bars for taller source`() {
        val rect = RenderLayout.fitRect(4f / 3f, 16f / 9f)
        assertTrue(rect.height == 1f)
        assertEquals(0.75f, rect.width, 1e-4f)
        assertEquals(0.125f, rect.left, 1e-4f)
        assertEquals(0.875f, rect.right, 1e-4f)
    }

    @Test
    fun `identical aspect fits fully`() {
        assertEquals(NormalizedRect.FULL, RenderLayout.fitRect(16f / 9f, 16f / 9f))
    }

    @Test
    fun `fill crops the taller source vertically`() {
        val window = RenderLayout.fillUvWindow(4f / 3f, 16f / 9f)
        assertEquals(1f, window.width, 1e-4f)
        assertEquals(0.75f, window.height, 1e-4f)
        assertEquals(0f, window.left, 1e-4f)
        assertEquals(1f, window.right, 1e-4f)
        assertEquals(0.125f, window.top, 1e-4f)
        assertEquals(0.875f, window.bottom, 1e-4f)
    }

    @Test
    fun `centeredIn rescales by band`() {
        val rect = NormalizedRect(0f, 0f, 1f, 1f).centeredIn(boxWidth = 0.5f, boxHeight = 1f)
        assertEquals(NormalizedRect(0.25f, 0f, 0.75f, 1f), rect)
    }
}

class CompositorGraphTest {

    @Test
    fun `no layers inside a gap`() {
        val graph = CompositorGraph(sequence(), MediaAssetRegistry.EMPTY)
        assertTrue(graph.layers(0L).isEmpty())
    }

    @Test
    fun `one clip yields one full-canvas letterboxed layer`() {
        val track = Track(TrackId("t1"), listOf(clip("c", 0L, 500_000L)))
        val graph = CompositorGraph(
            sequence(track),
            MediaAssetRegistry.EMPTY.register(v),
        )
        val layers = graph.layers(250_000L)
        assertEquals(1, layers.size)
        val layer = layers.single()
        assertEquals(v.id, layer.mediaId)
        assertEquals(NormalizedRect.FULL, layer.outputRect)
        assertEquals(NormalizedRect.FULL, layer.uvWindow)
        assertEquals(1f, layer.opacity)
    }

    @Test
    fun `rotation carries through to the layer`() {
        val track = Track(TrackId("t1"), listOf(clip("c", 0L, 500_000L)))
        val rotated = v.copy(rotationDegrees = 90)
        val graph = CompositorGraph(
            sequence(track),
            MediaAssetRegistry.EMPTY.register(rotated),
        )
        val layer = graph.layers(10_000L).single()
        assertEquals(90, layer.rotationDegrees)
        // displayed 720x1280 -> tall source letterboxed into landscape canvas
        assertEquals((720f / 1280f) / (16f / 9f), layer.outputRect.width, 1e-4f)
    }

    @Test
    fun `missing media drops the layer`() {
        val track = Track(TrackId("t1"), listOf(clip("c", 0L, 500_000L)))
        val graph = CompositorGraph(sequence(track), MediaAssetRegistry.EMPTY)
        assertTrue(graph.layers(100_000L).isEmpty())
    }

    @Test
    fun `audio-only assets do not produce a layer`() {
        val track = Track(TrackId("t1"), listOf(clip("c", 0L, 500_000L)))
        val audio = v.copy(
            displayName = "a.m4a",
            width = 0,
            height = 0,
        )
        val graph = CompositorGraph(
            sequence(track),
            MediaAssetRegistry.EMPTY.register(audio),
        )
        assertTrue(graph.layers(100_000L).isEmpty())
    }

    @Test
    fun `upper track renders on top without blocking the layer below`() {
        val bottom = Track(TrackId("b"), listOf(clip("b1", 0L, 500_000L)))
        val top = Track(TrackId("t"), listOf(clip("t1", 0L, 500_000L)))
        // BOTH tracks are active; layers() returns bottom first so the
        // renderer draws it first (behind the upper track).
        val graph = CompositorGraph(
            sequence(bottom, top),
            MediaAssetRegistry.EMPTY.register(v),
        )
        val layers = graph.layers(10_000L)
        assertEquals(2, layers.size)
        assertEquals(listOf(v.id, v.id), layers.map { it.mediaId })
    }

    @Test
    fun `aspect ratio matches the sequence`() {
        val graph = CompositorGraph(sequence(), MediaAssetRegistry.EMPTY)
        assertEquals(16f / 9f, graph.aspectRatio(), 1e-4f)
    }
}