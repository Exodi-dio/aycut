package com.exodi.aycut.core.math

/** A number expressed as an exact fraction `numerator / denominator`. */
data class Rational(
    val numerator: Long,
    val denominator: Long,
) {
    init {
        require(denominator != 0L) { "denominator must be non-zero, was $denominator" }
    }

    val value: Double
        get() = numerator.toDouble() / denominator.toDouble()

    val isNegative: Boolean
        get() = numerator < 0L

    val abs: Rational
        get() = if (isNegative) Rational(-numerator, denominator) else this

    operator fun plus(other: Rational): Rational = fromLongs(
        numerator * other.denominator + other.numerator * denominator,
        denominator * other.denominator,
    )

    operator fun minus(other: Rational): Rational = fromLongs(
        numerator * other.denominator - other.numerator * denominator,
        denominator * other.denominator,
    )

    operator fun times(other: Rational): Rational =
        fromLongs(numerator * other.numerator, denominator * other.denominator)

    operator fun div(other: Rational): Rational =
        fromLongs(numerator * other.denominator, denominator * other.numerator)

    /** Reciprocal `denominator / numerator`; fails for zero. */
    fun reciprocal(): Rational = Rational(denominator, numerator)

    companion object {
        val ZERO = Rational(0L, 1L)
        val ONE = Rational(1L, 1L)

        /**
         * Reduced form with a positive denominator.
         *
         * The engine's frame/rate magnitudes never approach Long overflow, so
         * plain long multiplication is suitable here; huge numerators that
         * overflow surface as incorrect (not silently dangerous) values and are
         * rejected by downstream [require]s.
         */
        fun fromLongs(numerator: Long, denominator: Long): Rational {
            require(denominator != 0L) { "denominator must be non-zero" }
            return normalize(numerator, denominator)
        }

        internal fun normalize(numerator: Long, denominator: Long): Rational {
            var n = numerator
            var d = denominator
            if (d < 0L) {
                n = -n
                d = -d
            }
            if (n == 0L) return Rational(0L, 1L)
            val g = gcd(abs(n), abs(d))
            return Rational(n / g, d / g)
        }

        internal fun gcd(a: Long, b: Long): Long {
            var x = abs(a)
            var y = abs(b)
            while (y != 0L) {
                val t = x % y
                x = y
                y = t
            }
            return x
        }

        /** Rounds `a / b` (both positive) to the nearest integer, halves up. */
        internal fun halfUpDiv(a: Long, b: Long): Long = (a + b / 2L) / b
    }
}