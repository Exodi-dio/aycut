package com.exodi.aycut.core.media

import com.exodi.aycut.core.model.MediaId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

private val asset = MediaAsset(
    id = MediaId("a1"),
    displayName = "clip.mp4",
    mimeType = "video/mp4",
    durationMicros = 1_000_000L,
    width = 1920,
    height = 1080,
    rotationDegrees = 90,
)

class MediaAssetTest {

    @Test
    fun `rotated media swaps displayed dimensions`() {
        assertTrue(asset.isRotated)
        assertEquals(1080, asset.displayedWidth)
        assertEquals(1920, asset.displayedHeight)
        assertFalse(asset.copy(rotationDegrees = 180).isRotated)
        assertFalse(asset.copy(rotationDegrees = 0).isRotated)
    }

    @Test
    fun `zero-length and negative metadata is rejected`() {
        assertFails { asset.copy(durationMicros = -1L) }
        assertFails { asset.copy(width = -1) }
        assertFails { asset.copy(rotationDegrees = -90) }
        assertFails { asset.copy(displayName = "  ") }
    }

    @Test
    fun `single thumbnail is the exact midpoint`() {
        val times = asset.thumbnailTimes(1)
        assertEquals(listOf(500_000L), times)
    }

    @Test
    fun `thumbnail times are strictly increasing midpoints`() {
        val times = asset.thumbnailTimes(4)
        assertEquals(listOf(125_000L, 375_000L, 625_000L, 875_000L), times)
        assertEquals(times, times.sorted())
        assertTrue(times.distinct().size == times.size)
        assertTrue(times.all { it > 0L && it < asset.durationMicros })
    }

    @Test
    fun `no thumbnails for empty media or zero count`() {
        assertEquals(emptyList(), asset.thumbnailTimes(0))
        assertEquals(emptyList(), asset.copy(durationMicros = 0L).thumbnailTimes(3))
    }

    @Test
    fun `registry registers removes and looks up`() {
        val registry = MediaAssetRegistry.EMPTY.register(asset)
        assertEquals(asset, registry.asset(MediaId("a1")))
        assertEquals(1, registry.size)
        assertNull(registry.remove(MediaId("a1")).asset(MediaId("a1")))
        assertNull(registry.asset(MediaId("missing")))
    }

    @Test
    fun `registry rejects zero-length assets`() {
        assertFails { MediaAssetRegistry.EMPTY.register(asset.copy(durationMicros = 0L)) }
    }

    @Test
    fun `sanitize keeps safe characters and neutralizes the rest`() {
        assertEquals("holiday_beach.mp4", sanitizeFileName("holiday beach.mp4"))
        assertEquals("a_b_c", sanitizeFileName("a/b\\c"))
        assertEquals("media", sanitizeFileName("../.."))
        assertEquals("media", sanitizeFileName(""))
        assertEquals("media", sanitizeFileName("///***"))
    }

    private inline fun assertFails(block: () -> Unit) =
        try {
            block()
            throw AssertionError("expected an exception")
        } catch (expected: IllegalArgumentException) {
            // pass
        }
}