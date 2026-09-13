package com.exodi.aycut.core.math

import kotlin.test.Test
import kotlin.test.assertEquals

class FrameRateTest {

    @Test
    fun `common rates snap to exact rationals`() {
        assertEquals(FrameRate.NTSC_30, FrameRate.of(29.97))
        assertEquals(FrameRate.NTSC_24, FrameRate.of(23.976))
        assertEquals(FrameRate.NTSC_60, FrameRate.of(59.94))
        assertEquals(FrameRate.P30, FrameRate.of(30.0))
        assertEquals(FrameRate.P25, FrameRate.of(25.0))
        assertEquals(FrameRate.P24, FrameRate.of(24.0))
    }

    @Test
    fun `uncommon rates resolve via continued fractions`() {
        assertEquals(FrameRate(25L, 2L), FrameRate.of(12.5))
        assertEquals(FrameRate.P30, FrameRate.of(30.000_000_1))
    }

    @Test
    fun `nominal fps labels the smpte grid`() {
        assertEquals(24, FrameRate.NTSC_24.nominalFps)
        assertEquals(30, FrameRate.NTSC_30.nominalFps)
        assertEquals(60, FrameRate.NTSC_60.nominalFps)
        assertEquals(30, FrameRate.P30.nominalFps)
        assertEquals(25, FrameRate.P25.nominalFps)
    }

    @Test
    fun `frame duration is exact integer microseconds`() {
        assertEquals(33_333L, FrameRate.P30.frameDurationMicros)
        assertEquals(40_000L, FrameRate.P25.frameDurationMicros)
        assertEquals(41_667L, FrameRate.P24.frameDurationMicros)
        assertEquals(33_367L, FrameRate.NTSC_30.frameDurationMicros)
        assertEquals(16_683L, FrameRate.NTSC_60.frameDurationMicros)
    }

    @Test
    fun `invalid rates are rejected`() {
        for (bad in listOf(0.0, -5.0, Double.NaN, Double.POSITIVE_INFINITY)) {
            try {
                FrameRate.of(bad)
                error("FrameRate.of($bad) should have failed")
            } catch (_: IllegalArgumentException) {
            }
        }
        try {
            FrameRate(0L, 1L)
            error("zero numerator should have failed")
        } catch (_: IllegalArgumentException) {
        }
    }

    @Test
    fun `rational view is exact`() {
        assertEquals(Rational(30_000L, 1_001L), FrameRate.NTSC_30.rational)
    }
}