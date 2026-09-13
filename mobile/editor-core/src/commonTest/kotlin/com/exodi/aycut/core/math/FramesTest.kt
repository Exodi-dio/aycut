package com.exodi.aycut.core.math

import kotlin.test.Test
import kotlin.test.assertEquals

class FramesTest {

    private val p30 = FrameRate.P30

    @Test
    fun `floor rounding takes the containing frame`() {
        assertEquals(0L, Frames.frameIndexAt(0L, p30, FrameRounding.FLOOR))
        assertEquals(0L, Frames.frameIndexAt(33_332L, p30, FrameRounding.FLOOR))
        assertEquals(1L, Frames.frameIndexAt(33_333L, p30, FrameRounding.FLOOR))
        assertEquals(30L, Frames.frameIndexAt(1_000_000L, p30, FrameRounding.FLOOR))
    }

    @Test
    fun `ceil rounding carries at exact boundaries`() {
        assertEquals(0L, Frames.frameIndexAt(0L, p30, FrameRounding.CEIL))
        assertEquals(1L, Frames.frameIndexAt(33_333L, p30, FrameRounding.CEIL))
        assertEquals(31L, Frames.framesForMicros(1_000_000L, p30))
    }

    @Test
    fun `round rounding halves up`() {
        assertEquals(0L, Frames.frameIndexAt(16_666L, p30, FrameRounding.ROUND))
        assertEquals(1L, Frames.frameIndexAt(16_667L, p30, FrameRounding.ROUND))
        assertEquals(1L, Frames.frameIndexAt(33_332L, p30, FrameRounding.ROUND))
        assertEquals(1L, Frames.frameIndexAt(33_333L, p30, FrameRounding.ROUND))
    }

    @Test
    fun `frame span helpers agree`() {
        assertEquals(66_666L, Frames.frameStartMicros(2L, p30))
        assertEquals(99_999L, Frames.frameEndMicros(2L, p30))
        assertEquals(TimeRangeLite(33_333L, 66_666L), Frames.frameSpanMicros(50_000L, p30))
    }

    @Test
    fun `nearest boundary picks the closer edge`() {
        assertEquals(33_333L, Frames.nearestBoundaryMicros(33_340L, p30))
        assertEquals(66_666L, Frames.nearestBoundaryMicros(66_660L, p30))
    }

    @Test
    fun `ntsc grid uses the expanded frame duration`() {
        val ntsc = FrameRate.NTSC_30
        assertEquals(1L, Frames.frameIndexAt(33_367L, ntsc, FrameRounding.FLOOR))
        assertEquals(3L, Frames.frameIndexAt(100_101L, ntsc, FrameRounding.ROUND))
        assertEquals(33_367L, ntsc.frameDurationMicros)
    }
}