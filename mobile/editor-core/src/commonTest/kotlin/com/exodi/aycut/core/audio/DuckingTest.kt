package com.exodi.aycut.core.audio

import kotlin.test.Test
import kotlin.test.assertEquals

class DuckingTest {

    private val curve = DuckCurve(
        DuckRule(
            triggerClipId = "voice",
            targetClipId = "music",
            duckAmount = 0.5,
            attackMicros = 100_000L,
            releaseMicros = 200_000L,
        ),
    )

    @Test
    fun `full trigger level at elapsed zero does not duck yet`() {
        assertEquals(1.0, curve.gainAt(1.0, 0L), 1e-9)
    }

    @Test
    fun `elapsed at exactly the attack time reaches the full duck amount`() {
        assertEquals(0.5, curve.gainAt(1.0, 100_000L), 1e-9)
    }

    @Test
    fun `gain recovers through release and back to unity`() {
        assertEquals(0.75, curve.gainAt(1.0, 200_000L), 1e-9)
        assertEquals(1.0, curve.gainAt(1.0, 300_000L), 1e-9)
        assertEquals(1.0, curve.gainAt(1.0, 1_000_000L), 1e-9)
    }

    @Test
    fun `zero trigger level leaves gain at unity for every elapsed time`() {
        assertEquals(1.0, curve.gainAt(0.0, 0L), 1e-9)
        assertEquals(1.0, curve.gainAt(0.0, 100_000L), 1e-9)
        assertEquals(1.0, curve.gainAt(0.0, 500_000L), 1e-9)
    }
}