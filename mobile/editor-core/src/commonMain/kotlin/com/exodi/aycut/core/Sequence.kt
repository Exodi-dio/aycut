package com.exodi.aycut.core

/**
 * A video editing sequence: resolution, frame rate, and total size in frames.
 *
 * Phase-1 placeholder — the full timeline model (tracks, clips, operations,
 * time mapping) lands in M2. This file exists to prove the shared-core
 * build and test pipeline end to end.
 */
data class Sequence(
    val width: Int,
    val height: Int,
    val frameRate: Double,
    val frameCount: Long,
) {
    val durationMillis: Long
        get() = (frameCount * 1000.0 / frameRate).toLong()

    companion object {
        /** Typical 1080p30 sequence. */
        fun createEmpty(width: Int = 1920, height: Int = 1080, frameRate: Double = 30.0): Sequence =
            Sequence(width, height, frameRate, frameCount = 0L)
    }
}