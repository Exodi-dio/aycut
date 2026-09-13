package com.exodi.aycut.core.profile

import com.exodi.aycut.core.math.FrameRate
import com.exodi.aycut.core.model.Sequence

/** An export/preset profile: canvas, frame rate and bitrate ceilings. */
data class Profile(
    val name: String,
    val width: Int,
    val height: Int,
    val frameRate: Double,
    val videoBitrate: Int,
    val audioBitrate: Int,
) {
    init {
        require(name.isNotBlank()) { "profile name must not be blank" }
        require(width > 0 && height > 0) { "profile dimensions must be positive" }
        require(frameRate > 0.0) { "profile frameRate must be positive" }
        require(videoBitrate > 0 && audioBitrate > 0) { "bitrates must be positive" }
    }

    val timebase: FrameRate get() = FrameRate.of(frameRate)

    companion object {
        val P1080P30 = Profile("1080P30", 1920, 1080, 30.0, 12_000_000, 192_000)
        val P1080P60 = Profile("1080P60", 1920, 1080, 60.0, 20_000_000, 192_000)
        val P720P30 = Profile("720P30", 1280, 720, 30.0, 6_000_000, 128_000)

        /** Canonical profile that matches a sequence canvas at a new frame rate. */
        fun forSequence(sequence: Sequence, frameRate: Double, scale: Int = 1): Profile {
            val w = (sequence.width / scale).coerceAtLeast(1)
            val h = (sequence.height / scale).coerceAtLeast(1)
            return Profile(
                name = "${w}x${h}P${frameRate.toInt()}",
                width = w,
                height = h,
                frameRate = frameRate,
                videoBitrate = Profile.suggestedVideoBitrate(w, h, frameRate),
                audioBitrate = 192_000,
            )
        }

        /** Rough h264 heuristic: 0.1 bits/pixel/frame, floored at 2 Mbps. */
        fun suggestedVideoBitrate(width: Int, height: Int, frameRate: Double): Int =
            (width * height * frameRate * 0.1).toInt().coerceAtLeast(2_000_000)
    }
}