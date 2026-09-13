package com.exodi.aycut.core.caption

import com.exodi.aycut.core.model.Micros

/** One on-screen caption line, half-open span [startMicros, endMicros). */
data class Caption(
    val text: String,
    val startMicros: Micros,
    val endMicros: Micros,
) {
    init {
        require(text.isNotBlank()) { "caption text must not be blank" }
        require(startMicros >= 0L) { "caption start must be non-negative" }
        require(endMicros > startMicros) { "caption span must be positive" }
    }
}

/**
 * An ordered, non-overlapping caption set with manual/computed timing.
 * On-device transcription ("auto-captions") produces the initial proposals;
 * the editor snaps them to clips later.
 */
class CaptionSet(captions: List<Caption>) {

    val captions: List<Caption> = captions

    init {
        require(captions.zipWithNext().all { it.first.endMicros <= it.second.startMicros }) {
            "captions must be sorted and non-overlapping"
        }
    }

    /** The caption containing [at], or null. */
    fun captionAt(at: Micros): Caption? = captions.firstOrNull { at in it.startMicros until it.endMicros }

    val durationMicros: Micros
        get() = captions.maxOfOrNull { it.endMicros } ?: 0L

    fun with(caption: Caption): CaptionSet =
        CaptionSet((captions + caption).sortedBy { it.startMicros })
}