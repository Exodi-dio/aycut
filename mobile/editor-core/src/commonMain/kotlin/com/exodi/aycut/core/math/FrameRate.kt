package com.exodi.aycut.core.math

import com.exodi.aycut.core.model.Micros

/**
 * An exact positive frame timebase: `numerator / denominator` frames per second.
 *
 * Keeps NTSC rates (24/30/60 × 10001/1001... i.e. 24000/1001, 30000/1001,
 * 60000/1001) exact instead of approximating them with a Double, so frame
 * counting, timecode, and export pacing are deterministic.
 */
data class FrameRate(
    val numerator: Long,
    val denominator: Long,
) {
    init {
        require(numerator > 0L) { "numerator must be positive, was $numerator" }
        require(denominator > 0L) { "denominator must be positive, was $denominator" }
    }

    val rational: Rational
        get() = Rational(numerator, denominator)

    val framesPerSecond: Double
        get() = numerator.toDouble() / denominator.toDouble()

    /** Nominal integer frames per second used by SMPTE timecode labels. */
    val nominalFps: Int
        get() = when {
            numerator == 24_000L && denominator == 1_001L -> 24
            numerator == 30_000L && denominator == 1_001L -> 30
            numerator == 60_000L && denominator == 1_001L -> 60
            else -> Rational.halfUpDiv(numerator, denominator).toInt()
        }

    /** Exact duration of one frame in integer microseconds, rounded half-up. */
    val frameDurationMicros: Micros
        get() = Rational.halfUpDiv(denominator * SECONDS_TO_MICROS, numerator)

    override fun toString(): String = "$numerator/$denominator"

    companion object {
        private const val SECONDS_TO_MICROS: Long = 1_000_000L

        /** Common broadcast/film rates, exact. */
        val NTSC_24 = FrameRate(24_000L, 1_001L)
        val P24 = FrameRate(24L, 1L)
        val P25 = FrameRate(25L, 1L)
        val NTSC_30 = FrameRate(30_000L, 1_001L)
        val P30 = FrameRate(30L, 1L)
        val P48 = FrameRate(48L, 1L)
        val P50 = FrameRate(50L, 1L)
        val NTSC_60 = FrameRate(60_000L, 1_001L)
        val P60 = FrameRate(60L, 1L)
        val P120 = FrameRate(120L, 1L)

        val COMMON: List<FrameRate> = listOf(
            NTSC_24, P24, P25, NTSC_30, P30, P48, P50, NTSC_60, P60, P120,
        )

        /**
         * Exact [FrameRate] for a decimal frames-per-second value.
         *
         * Known broadcast rates snap to their exact rational; anything else is
         * approximated by continued fractions with a bounded denominator, so
         * `of(29.97) == NTSC_30` but `of(12.5) == FrameRate(25, 2)`.
         */
        fun of(decimal: Double): FrameRate {
            require(decimal.isFinite() && decimal > 0.0) {
                "decimal must be a positive finite value, was $decimal"
            }
            for (common in COMMON) {
                if (decimal - common.framesPerSecond in -COMMON_TOLERANCE..COMMON_TOLERANCE) {
                    return common
                }
            }
            return fromContinuedFraction(decimal)
        }

        /**
         * Rational approximation via continued fractions with
         * `denominator <= 1_000_000`.
         */
        internal fun fromContinuedFraction(decimal: Double): FrameRate {
            var x = decimal
            var h0 = 0L
            var h1 = 1L
            var k0 = 1L
            var k1 = 0L
            var guard = 0
            while (guard++ < 64) {
                val a = x.toLong()
                val h = a * h1 + h0
                val k = a * k1 + k0
                if (k > MAX_DENOMINATOR) break
                h0 = h1
                h1 = h
                k0 = k1
                k1 = k
                val fractional = x - a
                if (fractional < EPS) break
                x = 1.0 / fractional
            }
            return if (k1 == 0L) FrameRate(roundHalfUpLong(decimal), 1L) else FrameRate(h1, k1)
        }

        private fun roundHalfUpLong(decimal: Double): Long =
            (decimal + if (decimal >= 0.0) 0.5 else -0.5).toLong()

        private const val MAX_DENOMINATOR = 1_000_000L
        private const val EPS = 1e-12
        private const val COMMON_TOLERANCE = 1e-3
    }
}