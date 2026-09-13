package com.exodi.aycut.core.effect

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class KeyframeTest {

    private fun ramp() = ParamCurve(
        listOf(Keyframe(0L, ParameterValue.Num(0.0)), Keyframe(1_000_000L, ParameterValue.Num(1.0))),
    )

    @Test
    fun `interpolates linearly and holds the boundaries`() {
        val curve = ramp()
        assertEquals(0.0, curve.valueAt(0L).asNumber())
        assertEquals(0.25, curve.valueAt(250_000L).asNumber(), 1e-12)
        assertEquals(0.75, curve.valueAt(750_000L).asNumber(), 1e-12)
        assertEquals(1.0, curve.valueAt(1_000_000L).asNumber(), 1e-12)
        assertEquals(1.0, curve.valueAt(5_000_000L).asNumber(), 1e-12)
    }

    @Test
    fun `interpolates piecewise over non-uniform keyframes`() {
        val curve = ParamCurve(
            listOf(
                Keyframe(0L, ParameterValue.Num(0.0)),
                Keyframe(400_000L, ParameterValue.Num(2.0)),
                Keyframe(1_000_000L, ParameterValue.Num(0.0)),
            ),
        )
        assertEquals(1.0, curve.valueAt(200_000L).asNumber(), 1e-12)
        assertEquals(2.0 / 3.0, curve.valueAt(800_000L).asNumber(), 1e-12)
        assertEquals(0.0, curve.valueAt(1_000_000L).asNumber(), 1e-12)
    }

    @Test
    fun `point values interpolate componentwise`() {
        val curve = ParamCurve(
            listOf(
                Keyframe(0L, ParameterValue.Point(0f, 0f)),
                Keyframe(1_000_000L, ParameterValue.Point(1f, 1f)),
            ),
        )
        val mid = curve.valueAt(500_000L).asPoint()
        assertEquals(0.5f, mid.x, 1e-6f)
        assertEquals(0.5f, mid.y, 1e-6f)
    }

    @Test
    fun `boolean values step at the halfway point`() {
        val curve = ParamCurve(
            listOf(
                Keyframe(0L, ParameterValue.Bool(false)),
                Keyframe(1_000_000L, ParameterValue.Bool(true)),
            ),
        )
        assertTrue(curve.valueAt(750_000L).asBoolean())
        assertFalse(curve.valueAt(250_000L).asBoolean())
    }

    @Test
    fun `single and duplicate-valued curves are constant`() {
        val single = ParamCurve(listOf(Keyframe(0L, ParameterValue.Num(0.7))))
        assertTrue(single.isConstant)
        assertEquals(0.7, single.valueAt(500_000L).asNumber(), 1e-12)

        val flat = ParamCurve(
            listOf(Keyframe(0L, ParameterValue.Num(0.7)), Keyframe(1_000_000L, ParameterValue.Num(0.7))),
        )
        assertTrue(flat.isConstant)
    }

    @Test
    fun `curves reject empty, unsorted and negative inputs`() {
        assertFailsWith<IllegalArgumentException> { ParamCurve(emptyList()) }
        assertFailsWith<IllegalArgumentException> {
            ParamCurve(listOf(Keyframe(0L, ParameterValue.Num(0.0)), Keyframe(0L, ParameterValue.Num(1.0))))
        }
        assertFailsWith<IllegalArgumentException> {
            ParamCurve(listOf(Keyframe(100L, ParameterValue.Num(0.0)), Keyframe(0L, ParameterValue.Num(1.0))))
        }
        assertFailsWith<IllegalArgumentException> { Keyframe(-1L, ParameterValue.Num(1.0)) }
    }

    @Test
    fun `effect resolves curve over static params`() {
        val effect = Effect(
            id = CoreEffectIds.OPACITY_ID,
            params = mapOf("value" to ParameterValue.Num(1.0)),
            curves = mapOf(
                "value" to ParamCurve(
                    listOf(
                        Keyframe(0L, ParameterValue.Num(0.0)),
                        Keyframe(1_000_000L, ParameterValue.Num(1.0)),
                    ),
                ),
            ),
        )
        assertEquals(0.0, effect.valueAt("value", 0L)?.asNumber() ?: 0.0, 1e-12)
        assertEquals(0.5, effect.valueAt("value", 500_000L)?.asNumber() ?: 0.0, 1e-12)
        assertEquals(0.0, effect.valueAt("missing", 500_000L)?.asNumber() ?: 0.0, 1e-12)
    }
}