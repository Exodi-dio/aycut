package com.exodi.aycut.core

import kotlin.test.Test
import kotlin.test.assertEquals

class SequenceTest {

    @Test
    fun `empty 1080p30 sequence has zero duration`() {
        val sequence = Sequence.createEmpty()
        assertEquals(1920, sequence.width)
        assertEquals(1080, sequence.height)
        assertEquals(30.0, sequence.frameRate)
        assertEquals(0L, sequence.frameCount)
        assertEquals(0L, sequence.durationMillis)
    }

    @Test
    fun `duration derives from frame count and rate`() {
        val sequence = Sequence(640, 480, frameRate = 25.0, frameCount = 250L)
        assertEquals(10_000L, sequence.durationMillis)
    }
}