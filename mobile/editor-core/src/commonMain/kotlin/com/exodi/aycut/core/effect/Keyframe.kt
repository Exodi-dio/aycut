package com.exodi.aycut.core.effect

import com.exodi.aycut.core.model.Micros

/** Interpolation policy between neighbouring keyframes. Only LINEAR is defined. */
enum class Easing { LINEAR }

/**
 * A single keyframe of a [ParamCurve]: the value of a parameter at a
 * clip-local instant, in microseconds from the owning clip's in point.
 */
data class Keyframe(
    val at: Micros,
    val value: ParameterValue,
    val easing: Easing = Easing.LINEAR,
) {
    init {
        require(at >= 0L) { "keyframe time must be non-negative, was $at" }
    }
}

/**
 * A piecewise-linear, keyframed parameter curve.
 *
 * Keyframes are strictly sorted by time (no duplicates). [valueAt] holds the
 * boundary value before the first and after the last keyframe and
 * interpolates linearly between neighbours ([lerp]); boolean values step at
 * the halfway point. A curve with a single keyframe is constant.
 */
data class ParamCurve(val keyframes: List<Keyframe>) {

    init {
        require(keyframes.isNotEmpty()) { "curve must contain at least one keyframe" }
        require(keyframes.zipWithNext().all { it.first.at < it.second.at }) {
            "curve keyframes must be strictly sorted by time, with no duplicates"
        }
    }

    /** True when the curve never changes across its whole domain. */
    val isConstant: Boolean
        get() = keyframes.size == 1 ||
            keyframes.zipWithNext().all { it.first.value == it.second.value }

    fun valueAt(at: Micros): ParameterValue {
        require(at >= 0L) { "at must be non-negative, was $at" }
        val first = keyframes.first()
        val last = keyframes.last()
        if (at <= first.at) return first.value
        if (at >= last.at) return last.value
        for (i in 0 until keyframes.lastIndex) {
            val lo = keyframes[i]
            val hi = keyframes[i + 1]
            if (at < hi.at) {
                val t = (at - lo.at).toDouble() / (hi.at - lo.at).toDouble()
                return lerp(lo.value, hi.value, t)
            }
        }
        error("unreachable: at $at within [${first.at}, ${last.at})")
    }
}