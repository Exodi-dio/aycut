package com.exodi.aycut.core.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class AudioEnvelopeTest {

    private fun rampUp() = AudioEnvelope(
        listOf(EnvelopePoint(0L, 0.0), EnvelopePoint(1_000_000L, 1.0)),
    )

    @Test
    fun `interpolates linearly between two points`() {
        val envelope = rampUp()
        assertEquals(0.0, envelope.gainAt(0L), 1e-12)
        assertEquals(0.25, envelope.gainAt(250_000L), 1e-12)
        assertEquals(0.5, envelope.gainAt(500_000L), 1e-12)
        assertEquals(0.999_999, envelope.gainAt(999_999L), 1e-12)
    }

    @Test
    fun `holds the boundary value before first and after last point`() {
        val envelope = rampUp()
        assertEquals(0.0, envelope.gainAt(0L), 1e-12)
        assertEquals(1.0, envelope.gainAt(1_000_000L), 1e-12)
        assertEquals(1.0, envelope.gainAt(2_000_000L), 1e-12)
    }

    @Test
    fun `interpolates piecewise across non-uniform keyframes`() {
        val envelope = AudioEnvelope(
            listOf(
                EnvelopePoint(0L, 0.0),
                EnvelopePoint(400_000L, 1.0),
                EnvelopePoint(1_000_000L, 0.5),
            ),
        )
        assertEquals(0.5, envelope.gainAt(200_000L), 1e-12)
        assertEquals(1.0, envelope.gainAt(400_000L), 1e-12)
        assertEquals(2.0 / 3.0, envelope.gainAt(800_000L), 1e-12)
        assertEquals(0.5, envelope.gainAt(1_000_000L), 1e-12)
    }

    @Test
    fun `constant envelope is expressed with two identical points`() {
        val envelope = AudioEnvelope(
            listOf(EnvelopePoint(0L, 0.7), EnvelopePoint(1_000_000L, 0.7)),
        )
        assertEquals(0.7, envelope.gainAt(0L), 1e-12)
        assertEquals(0.7, envelope.gainAt(500_000L), 1e-12)
        assertEquals(0.7, envelope.gainAt(1_000_000L), 1e-12)
    }

    @Test
    fun `UNITY is flat at one`() {
        assertEquals(1.0, AudioEnvelope.UNITY.gainAt(0L), 1e-12)
        assertEquals(1.0, AudioEnvelope.UNITY.gainAt(500_000L), 1e-12)
    }

    @Test
    fun `envelope must not be empty`() {
        assertFailsWith<IllegalArgumentException> { AudioEnvelope(emptyList()) }
    }

    @Test
    fun `keyframes must be strictly sorted with no duplicates`() {
        assertFailsWith<IllegalArgumentException> {
            AudioEnvelope(listOf(EnvelopePoint(0L, 0.0), EnvelopePoint(0L, 1.0)))
        }
        assertFailsWith<IllegalArgumentException> {
            AudioEnvelope(listOf(EnvelopePoint(100L, 0.0), EnvelopePoint(0L, 1.0)))
        }
    }

    @Test
    fun `offsets and gains are validated`() {
        assertFailsWith<IllegalArgumentException> { EnvelopePoint(-1L, 1.0) }
        assertFailsWith<IllegalArgumentException> { EnvelopePoint(0L, -0.1) }
        assertFailsWith<IllegalArgumentException> { AudioEnvelope.UNITY.gainAt(-1L) }
    }
}