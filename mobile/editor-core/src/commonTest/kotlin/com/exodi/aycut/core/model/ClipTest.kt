package com.exodi.aycut.core.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ClipTest {

    private val media = MediaId("clip.mp4")

    private fun clip(
        sourceStart: Micros = 0L,
        duration: Micros = 10L,
        timelineIn: Micros = 0L,
        playRate: Double = 1.0,
        reverse: Boolean = false,
    ) = Clip(ClipId("c1"), media, TimeRange(sourceStart, duration), timelineIn, playRate, reverse)

    @Test
    fun `defaults keep unit speed behaviour`() {
        val c = clip(duration = 1_000_000L)
        assertEquals(1.0, c.playRate)
        assertFalse(c.reverse)
        assertEquals(1_000_000L, c.timelineDuration)
        assertEquals(1_000_000L, c.timelineEnd)
        assertEquals(TimeRange(0L, 1_000_000L), c.timelineRange)
    }

    @Test
    fun `speed scales the timeline span`() {
        assertEquals(500_000L, clip(duration = 1_000_000L, playRate = 2.0).timelineEnd)
        assertEquals(2_000_000L, clip(duration = 1_000_000L, playRate = 0.5).timelineEnd)
    }

    @Test
    fun `timeline in offsets the derived end`() {
        val c = clip(sourceStart = 10L, duration = 10L, timelineIn = 5L, playRate = 2.0)
        assertEquals(10L, c.timelineEnd)
        assertEquals(TimeRange(5L, 5L), c.timelineRange)
    }

    @Test
    fun `unit speed forward maps timeline to source exactly`() {
        val c = clip(sourceStart = 100L, duration = 10L)
        assertEquals(100L, c.sourceTimeAt(0L))
        assertEquals(105L, c.sourceTimeAt(5L))
        assertEquals(109L, c.sourceTimeAt(9L))
    }

    @Test
    fun `double speed reads ahead in the source`() {
        val c = clip(duration = 10L, playRate = 2.0)
        assertEquals(0L, c.sourceTimeAt(0L))
        assertEquals(6L, c.sourceTimeAt(3L))
        assertEquals(9L, c.sourceTimeAt(4L)) // clamp into the source span
    }

    @Test
    fun `reverse inverts the source mapping`() {
        val c = clip(duration = 10L, reverse = true)
        assertEquals(9L, c.sourceTimeAt(0L))
        assertEquals(4L, c.sourceTimeAt(5L))
        assertEquals(0L, c.sourceTimeAt(9L))
    }

    @Test
    fun `reverse combined with speed maps and clamps`() {
        val c = clip(duration = 10L, playRate = 2.0, reverse = true)
        assertEquals(8L, c.sourceTimeAt(0L))
        assertEquals(0L, c.sourceTimeAt(4L))
    }

    @Test
    fun `source time outside the clip span is rejected`() {
        val c = clip(duration = 10L)
        assertFailsWith<IllegalArgumentException> { c.sourceTimeAt(10L) }
        assertFailsWith<IllegalArgumentException> { c.sourceTimeAt(-1L) }
    }

    @Test
    fun `invalid play rates are rejected on construction`() {
        for (rate in listOf(0.0, -1.0, Double.NaN, Double.POSITIVE_INFINITY)) {
            assertFailsWith<IllegalArgumentException> { clip(playRate = rate) }
        }
    }

    @Test
    fun `reverse flag is preserved by copy`() {
        val c = clip(duration = 10L, reverse = true)
        assertTrue(c.copy(playRate = 2.0).reverse)
    }
}