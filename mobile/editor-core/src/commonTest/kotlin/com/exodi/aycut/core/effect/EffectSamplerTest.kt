package com.exodi.aycut.core.effect

import com.exodi.aycut.core.composite.NormalizedRect
import com.exodi.aycut.core.model.Clip
import com.exodi.aycut.core.model.ClipId
import com.exodi.aycut.core.model.MediaId
import com.exodi.aycut.core.model.TimeRange
import kotlin.test.Test
import kotlin.test.assertEquals

class EffectSamplerTest {

    private fun clip(effects: List<Effect> = emptyList()) = Clip(
        id = ClipId("c"),
        media = MediaId("m"),
        sourceRange = TimeRange(0L, 1_000_000L),
        timelineIn = 0L,
        effects = effects,
    )

    @Test
    fun `clips without effects are fully opaque`() {
        assertEquals(1f, EffectSampler.effectiveOpacity(clip(), 500_000L))
    }

    @Test
    fun `opacity effect value is used and clamped into unit range`() {
        val half = clip(listOf(opacity(0.5)))
        assertEquals(0.5f, EffectSampler.effectiveOpacity(half, 500_000L), 1e-6f)

        val over = clip(listOf(opacity(1.5)))
        assertEquals(1f, EffectSampler.effectiveOpacity(over, 500_000L), 1e-6f)

        val under = clip(listOf(opacity(-0.5)))
        assertEquals(0f, EffectSampler.effectiveOpacity(under, 500_000L), 1e-6f)
    }

    @Test
    fun `keyframed opacity interpolates within the clip`() {
        val keyed = clip(
            listOf(
                Effect(
                    id = CoreEffectIds.OPACITY_ID,
                    curves = mapOf(
                        "value" to ParamCurve(
                            listOf(
                                Keyframe(0L, ParameterValue.Num(0.0)),
                                Keyframe(1_000_000L, ParameterValue.Num(1.0)),
                            ),
                        ),
                    ),
                ),
            ),
        )
        assertEquals(0.25f, EffectSampler.effectiveOpacity(keyed, 250_000L), 1e-6f)
        assertEquals(0.75f, EffectSampler.effectiveOpacity(keyed, 750_000L), 1e-6f)
    }

    @Test
    fun `transform preserves the base rect when absent`() {
        val noTransform = clip()
        val result = EffectSampler.transformRect(NormalizedRect.FULL, noTransform, 0L)
        assertEquals(NormalizedRect.FULL.left, result.left, 1e-6f)
        assertEquals(NormalizedRect.FULL.right, result.right, 1e-6f)
        assertEquals(NormalizedRect.FULL.top, result.top, 1e-6f)
        assertEquals(NormalizedRect.FULL.bottom, result.bottom, 1e-6f)
    }

    @Test
    fun `transform scale expands around the anchor`() {
        val scaled = clip(
            listOf(effect(CoreEffectIds.TRANSFORM_ID, "scale", ParameterValue.Num(2.0))),
        )
        val result = EffectSampler.transformRect(NormalizedRect.FULL, scaled, 0L)
        assertEquals(-0.5f, result.left, 1e-6f)
        assertEquals(1.5f, result.right, 1e-6f)
        assertEquals(-0.5f, result.top, 1e-6f)
        assertEquals(1.5f, result.bottom, 1e-6f)
    }

    @Test
    fun `transform position moves the anchor`() {
        val moved = clip(
            listOf(
                Effect(
                    id = CoreEffectIds.TRANSFORM_ID,
                    params = mapOf("position" to ParameterValue.Point(0f, 1f)),
                ),
            ),
        )
        val result = EffectSampler.transformRect(NormalizedRect.FULL, moved, 0L)
        assertEquals(-0.5f, result.left, 1e-6f)
        assertEquals(0.5f, result.right, 1e-6f)
        assertEquals(0.5f, result.top, 1e-6f)
        assertEquals(1.5f, result.bottom, 1e-6f)
    }

    @Test
    fun `transform rotation applies only when the effect is present`() {
        assertEquals(0f, EffectSampler.rotationDegrees(clip(), 0L))
        val rotated = clip(listOf(effect(CoreEffectIds.TRANSFORM_ID, "rotation", ParameterValue.Num(45.0))))
        assertEquals(45f, EffectSampler.rotationDegrees(rotated, 0L), 1e-6f)
    }

    @Test
    fun `zero scale collapses the rect to its anchor`() {
        val flat = clip(listOf(effect(CoreEffectIds.TRANSFORM_ID, "scale", ParameterValue.Num(0.0))))
        val result = EffectSampler.transformRect(NormalizedRect.FULL, flat, 0L)
        assertEquals(0.5f, result.left, 1e-3f)
        assertEquals(0.5f, result.right, 1e-3f)
        assertEquals(0.5f, result.top, 1e-3f)
        assertEquals(0.5f, result.bottom, 1e-3f)
    }

    private fun opacity(value: Double) =
        effect(CoreEffectIds.OPACITY_ID, "value", ParameterValue.Num(value))

    private fun effect(id: EffectId, param: String, value: ParameterValue) =
        Effect(id = id, params = mapOf(param to value))
}