package com.exodi.aycut.core.color

import kotlin.test.Test
import kotlin.test.assertEquals

class ColorMathTest {

    @Test
    fun `srgb boundary constant maps to the linear boundary`() {
        assertEquals(0.0031308, ColorMath.srgbToLinear(0.04045), 1e-8)
    }

    @Test
    fun `srgb and bt709 transfer curves round trip`() {
        for (x in listOf(0.0, 0.005, 0.2, 0.5, 0.9, 1.0)) {
            assertEquals(x, ColorMath.linearToSrgb(ColorMath.srgbToLinear(x)), 1e-4)
        }
        for (x in listOf(0.0, 0.01, 0.3, 0.9, 1.0)) {
            assertEquals(x, ColorMath.linearToBt709(ColorMath.bt709ToLinear(x)), 1e-4)
        }
    }

    @Test
    fun `src over composites front over opaque back`() {
        val back = floatArrayOf(0.5f, 0.8f, 0.2f, 1.0f)
        val front = floatArrayOf(1.0f, 0.2f, 0.4f, 0.5f)
        val out = compositeColor(back, front, BlendMode.SRC_OVER)
        assertEquals(1.0, out[3].toDouble(), 1e-6)
        assertEquals((1.0f + 0.5f * 0.5f).toDouble(), out[0].toDouble(), 1e-6)
        assertEquals((0.2f + 0.8f * 0.5f).toDouble(), out[1].toDouble(), 1e-6)
        assertEquals((0.4f + 0.2f * 0.5f).toDouble(), out[2].toDouble(), 1e-6)
    }

    @Test
    fun `multiply multiplies each channel`() {
        val back = floatArrayOf(0.8f, 0.4f, 0.2f, 1.0f)
        val front = floatArrayOf(0.5f, 0.5f, 0.5f, 0.5f)
        val out = compositeColor(back, front, BlendMode.MULTIPLY)
        assertEquals(0.4, out[0].toDouble(), 1e-6)
        assertEquals(0.2, out[1].toDouble(), 1e-6)
        assertEquals(0.1, out[2].toDouble(), 1e-6)
        assertEquals(1.0, out[3].toDouble(), 1e-6)
    }
}