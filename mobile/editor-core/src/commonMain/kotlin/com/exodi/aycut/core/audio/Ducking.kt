package com.exodi.aycut.core.audio

import com.exodi.aycut.core.model.Micros

/**
 * Keyframed audio-ducking sidechain rule: someone's gain is reduced in
 * proportion to another stream's level. Pure data; the sampler computes
 * the instantaneous duck gain.
 */
data class DuckRule(
    val triggerClipId: String,
    val targetClipId: String,
    val duckAmount: Double = 0.5,
    val attackMicros: Micros = 100_000L,
    val releaseMicros: Micros = 200_000L,
) {
    init {
        require(duckAmount in 0.0..1.0) { "duckAmount must be in 0..1, was $duckAmount" }
        require(attackMicros >= 0L) { "attack must be non-negative" }
        require(releaseMicros >= 0L) { "release must be non-negative" }
    }
}

/**
 * Sampler that, given the loudness envelope of the trigger clip
 * (0.0 = silent, 1.0 = full level) and the offset since the trigger started,
 * returns the gain multiplier for the target. Attack/release follow the
 * rule's [DuckRule.attackMicros]/[DuckRule.releaseMicros].
 */
class DuckCurve(private val rule: DuckRule) {

    fun gainAt(triggerLevel: Double, elapsedMicros: Micros): Double {
        val attack = rule.attackMicros.toDouble().coerceAtLeast(1.0)
        val release = rule.releaseMicros.toDouble().coerceAtLeast(1.0)
        val amount = (triggerLevel.coerceIn(0.0, 1.0)) * rule.duckAmount
        val envelope = when {
            elapsedMicros < attack -> elapsedMicros.toDouble() / attack
            elapsedMicros < attack + release -> 1.0 - (elapsedMicros - attack) / release
            else -> 0.0
        }
        val duck = amount * envelope.coerceIn(0.0, 1.0)
        return 1.0 - duck
    }
}