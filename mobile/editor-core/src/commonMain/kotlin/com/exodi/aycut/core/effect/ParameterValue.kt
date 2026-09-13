package com.exodi.aycut.core.effect

import com.exodi.aycut.core.model.Micros

/**
 * A typed effect parameter value. Effects are pure data: [ParamCurve]s drive
 * the keyframable parameters over clip-local microseconds, and the renderer
 * (GLES) turns the sampled values into draw state.
 */
sealed interface ParameterValue {

    data class Num(val value: Double) : ParameterValue

    data class Bool(val value: Boolean) : ParameterValue

    data class Color(val r: Float, val g: Float, val b: Float) : ParameterValue {
        init {
            require(r in 0f..1f && g in 0f..1f && b in 0f..1f) {
                "color channels must be in 0..1, was ($r, $g, $b)"
            }
        }
    }

    /** A normalized canvas position: (0,0) is top-left, (1,1) bottom-right. */
    data class Point(val x: Float, val y: Float) : ParameterValue

    fun asNumber(default: Double = 0.0): Double = when (this) {
        is Num -> value
        else -> default
    }

    fun asBoolean(default: Boolean = false): Boolean = when (this) {
        is Bool -> value
        else -> default
    }

    fun asPoint(): Point = this as? Point ?: Point(0.5f, 0.5f)

    fun asColor(): Color = this as? Color ?: Color(1f, 1f, 1f)
}

/** Linear interpolation between two values; booleans step at [t] against the second. */
fun lerp(a: ParameterValue, b: ParameterValue, t: Double): ParameterValue = when {
    a is ParameterValue.Num && b is ParameterValue.Num ->
        ParameterValue.Num(a.value + (b.value - a.value) * t)

    a is ParameterValue.Point && b is ParameterValue.Point -> {
        val x = a.x + (b.x - a.x) * t.toFloat()
        val y = a.y + (b.y - a.y) * t.toFloat()
        ParameterValue.Point(x, y)
    }

    a is ParameterValue.Color && b is ParameterValue.Color -> {
        val r = a.r + (b.r - a.r) * t.toFloat()
        val g = a.g + (b.g - a.g) * t.toFloat()
        val bl = a.b + (b.b - a.b) * t.toFloat()
        ParameterValue.Color(r, g, bl)
    }

    else -> if (t < 0.5) a else b
}

/** Opaque identity of an effect class (e.g. "opacity", "transform"). */
@JvmInline
value class EffectId(val raw: String)

/** Convenience idents for the built-in core effects. */
object CoreEffectIds {
    const val OPACITY = "opacity"
    const val TRANSFORM = "transform"
    val OPACITY_ID = EffectId(OPACITY)
    val TRANSFORM_ID = EffectId(TRANSFORM)
}

/**
 * One applied instance of an effect on a [com.exodi.aycut.core.model.Clip].
 *
 * [params] holds the static (non-animated) values by parameter name;
 * [curves] holds keyframeed values for the same parameter names and wins over
 * [params] at any sampled instant. Parameter names are the effect schema's
 * concern (see the effect registry, E12).
 */
data class Effect(
    val id: EffectId,
    val params: Map<String, ParameterValue> = emptyMap(),
    val curves: Map<String, ParamCurve> = emptyMap(),
) {
    /** Value of parameter [name] at clip-local [offsetInClip], curve first. */
    fun valueAt(name: String, offsetInClip: Micros): ParameterValue? =
        curves[name]?.valueAt(offsetInClip) ?: params[name]

    fun valueAt(name: String, offsetInClip: Micros, default: ParameterValue): ParameterValue =
        valueAt(name, offsetInClip) ?: default
}