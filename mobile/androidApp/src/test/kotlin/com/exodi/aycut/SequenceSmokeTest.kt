package com.exodi.aycut

import com.exodi.aycut.core.model.Sequence
import org.junit.Assert.assertEquals
import org.junit.Test

class SequenceSmokeTest {
    @Test
    fun `editor core wires into the android host test classpath`() {
        val sequence = Sequence.createEmpty()
        assertEquals(1920, sequence.width)
        assertEquals(0L, sequence.durationMicros)
    }
}