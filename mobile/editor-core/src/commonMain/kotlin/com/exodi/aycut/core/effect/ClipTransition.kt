package com.exodi.aycut.core.effect

import com.exodi.aycut.core.model.Micros

/** Visual transition between a clip and its predecessor on the same lane. */
enum class TransitionKind {
    /** The outgoing clip dissolves underneath the incoming clip. */
    CROSS_DISSOLVE,

    /** The incoming clip fades up over the (default black) background. */
    FADE_TO_BLACK,
}

/**
 * A transition at the head of a clip, expressed in clip-local microseconds
 * from its in point. Cross-dissolves sample the previous clip behind the
 * incoming one across [durationMicros]; fade-to-black only ramps the clip's
 * own opacity. Audio cross-fades are modelled separately via [Clip] fades.
 */
data class ClipTransition(
    val kind: TransitionKind,
    val durationMicros: Micros,
) {
    init {
        require(durationMicros > 0L) { "transition duration must be positive, was $durationMicros" }
    }
}