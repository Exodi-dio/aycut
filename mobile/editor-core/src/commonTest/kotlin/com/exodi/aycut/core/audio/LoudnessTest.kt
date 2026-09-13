package com.exodi.aycut.core.audio

import kotlin.math.abs
import kotlin.math.pow
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LoudnessTest {

    @Test
    fun `empty sample list measures negative infinity`() {
        assertEquals(Double.NEGATIVE_INFINITY, Loudness.integratedLoudness(emptyList()))
    }

    @Test
    fun `constant amplitude tone measures finite negative loudness`() {
        val samples = List(4000) { 0.1 }
        val lufs = Loudness.integratedLoudness(samples)
        assertTrue(lufs.isFinite())
        assertTrue(lufs < 0.0)
        assertTrue(lufs > -70.0)
    }

    @Test
    fun `gainToReach applies the dB difference as a linear gain`() {
        val gain = Loudness.gainToReach(measuredLufs = -20.0, targetLufs = -14.0)
        assertEquals(10.0.pow(0.3), gain, 1e-9)
        assertEquals(1.0, Loudness.gainToReach(-14.0, -14.0), 1e-9)
    }

    @Test
    fun `gainToReach applied to measured loudness reaches the target`() {
        val samples = List(4000) { 0.1 }
        val measured = Loudness.integratedLoudness(samples)
        val target = Loudness.STREAMING_TARGET_LUFS
        val gain = Loudness.gainToReach(measured, target)
        val boosted = samples.map { it * gain }
        val boostedLufs = Loudness.integratedLoudness(boosted)
        assertTrue(abs(boostedLufs - target) < 0.5)
    }
}