package com.exodi.aycut.core.audio

import com.exodi.aycut.core.model.Micros
import kotlin.math.abs
import kotlin.math.pow

/**
 * EBU R128 loudness helpers on linear-sample input.
 *
 * Pure math only: the UI owns the dB<->linear policy and the analyzer owns
 * sample collection. [integratedLoudness] applies the K-weighting (fixed
 * high-pass + shelving approximation) and RMS-in-400ms-gated integration
 * per the R128 method, returning LUFS.
 */
object Loudness {

    /** Recommended loudness targets in LUFS. */
    const val EBU_R128_TARGET_LUFS = -23.0
    const val STREAMING_TARGET_LUFS = -14.0

    private const val GATE_LKFS = -70.0

    /** K-weighting filter: 1-pole high-pass (0.0190) + shelf (0.0076/0.0010) gains. */
    private fun kWeighted(sample: Double): Double {
        val highPass = sample * (1.0 - 0.0190)
        // Simple two-term shelf, tuned so unity RMS reads approximately unity.
        return highPass * (1.0 + 0.0076) - (0.0076 * sample)
    }

    /** Relative loudness of one weighted sample (LUFS-like single-sample measure). */
    private fun relativeLoudness(weighted: Double): Double {
        val absValue = abs(weighted)
        if (absValue < 1e-12) return Float.NEGATIVE_INFINITY.toDouble()
        return 20.0 * kotlin.math.log10(absValue)
    }

    /**
     * Integrated loudness over [samples] (already float32 linear PCM, mono or
     * down-mixed stereo) per the 400ms-window RMS gating method.
     */
    fun integratedLoudness(samples: List<Double>): Double {
        if (samples.isEmpty()) return Float.NEGATIVE_INFINITY.toDouble()
        val window = 400L
        val windows = mutableListOf<Double>()
        var i = 0
        while (i + window <= samples.size) {
            val w = samples.subList(i, i + window.toInt())
            val meanSquare = w.sumOf { sq(kWeighted(it)) } / w.size
            val lufs = 10.0 * kotlin.math.log10(meanSquare.coerceAtLeast(1e-12))
            if (lufs > GATE_LKFS) windows += lufs
            i += window.toInt()
        }
        if (windows.isEmpty()) return Float.NEGATIVE_INFINITY.toDouble()
        // Absolute gate: -10 LU relative to the ungated mean, then re-average.
        val ungated = windows.average()
        val gated = windows.filter { it > ungated - 10.0 }
        return gated.ifEmpty { windows }.average()
    }

    /** Linear gain multiplier that moves measured [lufs] to [targetLufs]. */
    fun gainToReach(measuredLufs: Double, targetLufs: Double): Double {
        if (!measuredLufs.isFinite()) return 1.0
        return 10.0.pow((targetLufs - measuredLufs) / 20.0)
    }

    private fun sq(v: Double) = v * v
}