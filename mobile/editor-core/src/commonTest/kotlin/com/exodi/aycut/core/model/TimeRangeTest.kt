package com.exodi.aycut.core.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TimeRangeTest {

    @Test
    fun `start is inclusive and end is exclusive`() {
        val range = TimeRange(1_000L, 5_000L)
        assertTrue(1_000L in range)
        assertTrue(5_999L in range)
        assertFalse(6_000L in range)
        assertFalse(999L in range)
    }

    @Test
    fun `end equals start plus duration`() {
        assertEquals(6_000L, TimeRange(1_000L, 5_000L).end)
    }

    @Test
    fun `negative start is rejected`() {
        assertFailsWith<IllegalArgumentException> { TimeRange(-1L, 5L) }
    }

    @Test
    fun `zero duration is rejected`() {
        assertFailsWith<IllegalArgumentException> { TimeRange(0L, 0L) }
    }

    @Test
    fun `overlap is symmetric and half-open`() {
        val a = TimeRange(0L, 10L)
        assertTrue(a.overlaps(TimeRange(5L, 5L)))
        assertTrue(a.overlaps(TimeRange(9L, 5L)))
        assertFalse(a.overlaps(TimeRange(10L, 5L))) // touching at end only
        assertFalse(a.overlaps(TimeRange(-10_000L, 10L)))
    }
}