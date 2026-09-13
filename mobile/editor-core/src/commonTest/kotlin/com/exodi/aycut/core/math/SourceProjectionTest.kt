package com.exodi.aycut.core.math

import com.exodi.aycut.core.model.Micros
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class SourceProjectionTest {

    private val MILLI = 1_000L * 1_000L

    private fun timelineDuration(source: Micros, rate: Double): Micros =
        SourceProjection.timelineDurationOf(source, rate)

    private fun forward(
        t: Micros,
        tIn: Micros,
        tEnd: Micros,
        sStart: Micros,
        sEnd: Micros,
        rate: Double = 1.0,
        reverse: Boolean = false,
    ): Micros = SourceProjection.forward(t, tIn, tEnd, sStart, sEnd, rate, reverse)

    private fun inverse(
        s: Micros,
        tIn: Micros,
        tEnd: Micros,
        sStart: Micros,
        sEnd: Micros,
        rate: Double = 1.0,
        reverse: Boolean = false,
    ): Micros = SourceProjection.inverse(s, tIn, tEnd, sStart, sEnd, rate, reverse)

    @Test
    fun `timeline duration scales the source span by speed`() {
        assertEquals(500_000L, timelineDuration(MILLI, 2.0))
        assertEquals(666_667L, timelineDuration(MILLI, 1.5))
        assertEquals(2 * MILLI, timelineDuration(MILLI, 0.5))
        assertEquals(MILLI, timelineDuration(MILLI, 1.0))
    }

    @Test
    fun `timeline duration rounds to the nearest microsecond`() {
        assertEquals(3L, timelineDuration(10L, 3.0))
        assertEquals(4L, timelineDuration(10L, 2.5))
    }

    @Test
    fun `timeline duration never drops below one microsecond`() {
        assertEquals(1L, timelineDuration(1L, 1_000_000_000.0))
    }

    @Test
    fun `invalid play rates are rejected`() {
        for (rate in listOf(0.0, -1.0, Double.NaN, Double.POSITIVE_INFINITY)) {
            assertFailsWith<IllegalArgumentException> { timelineDuration(10L, rate) }
            assertFailsWith<IllegalArgumentException> { forward(0L, 0L, 10L, 0L, 10L, rate) }
        }
    }

    @Test
    fun `timeline duration overflow is rejected`() {
        assertFailsWith<IllegalArgumentException> {
            timelineDuration(Long.MAX_VALUE, 1e-9)
        }
    }

    @Test
    fun `unit speed forward maps exactly one source microsecond per timeline microsecond`() {
        assertEquals(103L, forward(3L, 0L, 10L, 100L, 110L))
        assertEquals(109L, forward(9L, 0L, 10L, 100L, 110L))
    }

    @Test
    fun `double speed advances source twice per timeline microsecond`() {
        assertEquals(106L, forward(3L, 0L, 5L, 100L, 110L, rate = 2.0))
        assertEquals(108L, forward(4L, 0L, 5L, 100L, 110L, rate = 2.0))
    }

    @Test
    fun `double speed clamps the final instant into the source span`() {
        assertEquals(9L, forward(9L, 0L, 10L, 0L, 10L, rate = 2.0))
    }

    @Test
    fun `slow motion never leaves the source span`() {
        assertEquals(9L, forward(19L, 0L, 20L, 0L, 10L, rate = 0.5))
    }

    @Test
    fun `reverse plays the source backwards at unit speed`() {
        assertEquals(9L, forward(0L, 0L, 10L, 0L, 10L, reverse = true))
        assertEquals(0L, forward(9L, 0L, 10L, 0L, 10L, reverse = true))
        assertEquals(107L, forward(2L, 0L, 10L, 100L, 110L, reverse = true))
    }

    @Test
    fun `slow reverse clamps into the source span`() {
        assertEquals(9L, forward(0L, 0L, 100L, 0L, 10L, rate = 0.1, reverse = true))
        assertEquals(0L, forward(99L, 0L, 100L, 0L, 10L, rate = 0.1, reverse = true))
    }

    @Test
    fun `reverse at double speed starts at the tail and walks to the head`() {
        assertEquals(8L, forward(0L, 0L, 5L, 0L, 10L, rate = 2.0, reverse = true))
        assertEquals(6L, forward(1L, 0L, 5L, 0L, 10L, rate = 2.0, reverse = true))
        assertEquals(0L, forward(4L, 0L, 5L, 0L, 10L, rate = 2.0, reverse = true))
    }

    @Test
    fun `forward result always stays inside the source span`() {
        for (rate in listOf(0.1, 0.5, 1.0, 1.5, 2.0, 4.0)) {
            val span = timelineDuration(10L, rate)
            for (t in 0L until span) {
                val result = forward(t, 0L, span, 0L, 10L, rate)
                check(result in 0L until 10L) { "exit at rate $rate, t $t -> $result" }
            }
        }
    }

    @Test
    fun `forward rejects timeline times outside the span`() {
        assertFailsWith<IllegalArgumentException> { forward(10L, 0L, 10L, 0L, 10L) }
        assertFailsWith<IllegalArgumentException> { forward(-1L, 0L, 10L, 0L, 10L) }
    }

    @Test
    fun `inverse is exact at unit speed both directions`() {
        assertEquals(0L, inverse(100L, 0L, 10L, 100L, 110L))
        assertEquals(9L, inverse(109L, 0L, 10L, 100L, 110L))
        assertEquals(0L, inverse(109L, 0L, 10L, 100L, 110L, reverse = true))
        assertEquals(9L, inverse(100L, 0L, 10L, 100L, 110L, reverse = true))
    }

    @Test
    fun `inverse at double speed seek lands on the played source`() {
        assertEquals(2L, inverse(104L, 0L, 5L, 100L, 110L, rate = 2.0))
        assertEquals(2L, inverse(104L, 0L, 5L, 100L, 110L, rate = 2.0, reverse = true))
    }

    @Test
    fun `forward then inverse round trips at unit speed`() {
        val clip = listOf(0L to 10L, 100L to 110L)
        for ((start, end) in clip) {
            for (t in 0L until 10L) {
                val projected = forward(t, 0L, 10L, start, end)
                assertEquals(t, inverse(projected, 0L, 10L, start, end))
            }
        }
    }

    @Test
    fun `source span for timeline is the rounded inverse of timeline duration`() {
        assertEquals(2L, SourceProjection.sourceSpanForTimeline(1L, 2.0))
        assertEquals(6L, SourceProjection.sourceSpanForTimeline(4L, 1.5))
        assertFailsWith<IllegalArgumentException> {
            SourceProjection.sourceSpanForTimeline(-1L, 1.0)
        }
    }

    @Test
    fun `forward projection overflow is rejected`() {
        assertFailsWith<IllegalArgumentException> {
            forward(Long.MAX_VALUE - 1L, 0L, Long.MAX_VALUE, 0L, Long.MAX_VALUE, rate = 1e9)
        }
    }
}