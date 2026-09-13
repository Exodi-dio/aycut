package com.exodi.aycut.core.color

import kotlin.math.pow
import kotlin.math.log10

/**
 * Color-math primitives shared by the render pipeline. All math is pure and
 * platform-free; GPU/rasterization stays out of scope (M-series).
 */
enum class ColorSpaceId { S_RGB, LINEAR_RGB, REC_709, REC_2100_PQ }

/** Transfer curve between encoded and linear light. */
enum class TransferCurve { S_RGB, LINEAR, BT_709, PQ }

/** Stateless, deterministic color-math functions. */
object ColorMath {

    /** sRGB encoded [0..1] to linear light [0..1]. */
    fun srgbToLinear(c: Double): Double =
        if (c <= 0.04045) c / 12.92
        else ((c + 0.055) / 1.055).pow(2.4)

    /** Linear light [0..1] to sRGB encoded [0..1]. */
    fun linearToSrgb(c: Double): Double =
        if (c <= 0.0031308) c * 12.92
        else 1.055 * c.pow(1.0 / 2.4) - 0.055

    /** BT.709 encoded to linear. */
    fun bt709ToLinear(c: Double): Double =
        if (c <= 0.081) c / 4.5
        else ((c + 0.099) / 1.099).pow(1.0 / 0.45)

    /** Linear to BT.709 encoded. */
    fun linearToBt709(c: Double): Double =
        if (c <= 0.018) c * 4.5
        else 1.099 * c.pow(0.45) - 0.099
}

/** Porter-Duff-style blending modes for premultiplied RGBA layers. */
enum class BlendMode { SRC_OVER, MULTIPLY, SCREEN, ADD }

/**
 * Composite two RGBA colors (each channel [0,1]) in [mode], linear light.
 */
fun compositeColor(back: FloatArray, front: FloatArray, mode: BlendMode): FloatArray {
    val a0 = back[3]; val a1 = front[3]
    val out = FloatArray(4)
    out[0] = when (mode) {
        BlendMode.SRC_OVER -> front[0] + back[0] * (1f - a1)
        BlendMode.MULTIPLY -> front[0] * back[0]
        BlendMode.SCREEN   -> front[0] + back[0] - front[0] * back[0]
        BlendMode.ADD      -> (front[0] + back[0]).coerceAtMost(1f)
    }
    out[1] = when (mode) {
        BlendMode.SRC_OVER -> front[1] + back[1] * (1f - a1)
        BlendMode.MULTIPLY -> front[1] * back[1]
        BlendMode.SCREEN   -> front[1] + back[1] - front[1] * back[1]
        BlendMode.ADD      -> (front[1] + back[1]).coerceAtMost(1f)
    }
    out[2] = when (mode) {
        BlendMode.SRC_OVER -> front[2] + back[2] * (1f - a1)
        BlendMode.MULTIPLY -> front[2] * back[2]
        BlendMode.SCREEN   -> front[2] + back[2] - front[2] * back[2]
        BlendMode.ADD      -> (front[2] + back[2]).coerceAtMost(1f)
    }
    // Alpha: normal compositing, premultiplied.
    out[3] = a1 + a0 * (1f - a1)
    return out
}