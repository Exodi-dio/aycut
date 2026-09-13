package com.exodi.aycut.core.math

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class RationalTest {

    @Test
    fun `fractions are reduced to lowest terms`() {
        assertEquals(Rational(1L, 2L), Rational.fromLongs(4L, 8L))
        assertEquals(Rational(1L, 3L), Rational.fromLongs(2L, 6L))
    }

    @Test
    fun `negative denominators are normalized into the numerator`() {
        assertEquals(Rational(-1L, 2L), Rational.fromLongs(1L, -2L))
        assertEquals(Rational(1L, 2L), Rational.fromLongs(-1L, -2L))
    }

    @Test
    fun `zero collapses to zero over one`() {
        assertEquals(Rational.ZERO, Rational.fromLongs(0L, 7L))
        assertEquals(Rational(0L, 1L), Rational.normalize(0L, -3L))
    }

    @Test
    fun `arithmetic is exact`() {
        assertEquals(Rational(5L, 6L), Rational(1L, 2L) + Rational(1L, 3L))
        assertEquals(Rational(1L, 6L), Rational(1L, 2L) - Rational(1L, 3L))
        assertEquals(Rational(1L, 3L), Rational(1L, 2L) * Rational(2L, 3L))
        assertEquals(Rational(2L, 1L), Rational(1L, 2L) / Rational(1L, 4L))
    }

    @Test
    fun `arithmetic reduces on every step`() {
        assertEquals(Rational(1L, 2L), Rational(1L, 2L) + Rational(0L, 1L))
        assertEquals(Rational(1L, 4L), Rational(1L, 2L) * Rational(1L, 2L))
    }

    @Test
    fun `reciprocal swaps numerator and denominator`() {
        assertEquals(Rational(4L, 1L), Rational(1L, 4L).reciprocal())
        assertEquals(Rational(1L, 1L), Rational(3L, 1L).reciprocal() * Rational(3L, 1L))
    }

    @Test
    fun `gcd matches the euclidean result`() {
        assertEquals(6L, Rational.gcd(48L, 18L))
        assertEquals(1L, Rational.gcd(17L, 13L))
    }

    @Test
    fun `value and comparisons`() {
        assertTrue(Rational(1L, 2L).value == 0.5)
        assertTrue(Rational(3L, 4L).value == 0.75)
        assertTrue(Rational(1L, 2L).value < Rational(2L, 3L).value)
    }

    @Test
    fun `negative and abs behave`() {
        assertTrue(Rational(-5L, 2L).isNegative)
        assertEquals(Rational(5L, 2L), Rational(-5L, 2L).abs)
    }

    @Test
    fun `zero denominators are rejected`() {
        assertFailsWith<IllegalArgumentException> { Rational(1L, 0L) }
        assertFailsWith<IllegalArgumentException> { Rational.fromLongs(1L, 0L) }
    }
}