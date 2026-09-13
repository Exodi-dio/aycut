package com.exodi.aycut.core.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class SpeedRampTest {

    @Test
    fun `playRateAt holds the boundary before first at and after last`() {
        val ramp = SpeedRamp(
            listOf(RampSegment(10L, 0.5), RampSegment(100L, 2.0), RampSegment(500L, 1.5)),
        )
        assertEquals(0.5, ramp.playRateAt(0L))
        assertEquals(0.5, ramp.playRateAt(9L))
        assertEquals(0.5, ramp.playRateAt(10L))
        assertEquals(2.0, ramp.playRateAt(100L))
        assertEquals(2.0, ramp.playRateAt(499L))
        assertEquals(1.5, ramp.playRateAt(500L))
        assertEquals(1.5, ramp.playRateAt(10_000L))
    }

    @Test
    fun `projectedSourceOffset at constant rate equals elapsed time`() {
        val ramp = SpeedRamp(listOf(RampSegment(0L, 1.0)))
        assertEquals(0L, ramp.projectedSourceOffset(0L))
        assertEquals(100L, ramp.projectedSourceOffset(100L))
        assertEquals(1_000_000L, ramp.projectedSourceOffset(1_000_000L))
    }

    @Test
    fun `projectedSourceOffset integrates each segment rate`() {
        val ramp = SpeedRamp(listOf(RampSegment(0L, 0.5), RampSegment(100L, 2.0)))
        // At the second segment boundary only the first rate has been consumed.
        assertEquals(50L, ramp.projectedSourceOffset(100L))
        // 0.5 * 100 + 2.0 * 100 across the whole span.
        assertEquals(250L, ramp.projectedSourceOffset(200L))
    }

    @Test
    fun `ramp rejects non-strictly-sorted segments and invalid entries`() {
        assertFailsWith<IllegalArgumentException> {
            SpeedRamp(listOf(RampSegment(0L, 1.0), RampSegment(0L, 2.0)))
        }
        assertFailsWith<IllegalArgumentException> {
            SpeedRamp(listOf(RampSegment(100L, 1.0), RampSegment(0L, 2.0)))
        }
        assertFailsWith<IllegalArgumentException> { SpeedRamp(emptyList()) }
        assertFailsWith<IllegalArgumentException> { RampSegment(-1L, 1.0) }
        assertFailsWith<IllegalArgumentException> { RampSegment(0L, 0.0) }
    }
}