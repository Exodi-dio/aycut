package com.exodi.aycut.core.math

import com.exodi.aycut.core.model.Micros

/**
 * SMPTE timecode `HH:MM:SS;FF` with optional drop-frame counting.
 *
 * Timecode labels are built on the [FrameRate.nominalFps] grid (29.97 and
 * 59.94 are labeled 30/60). Drop-frame skips the first two frames of each
 * minute (on 29.97; four on 59.94) so the label tracks *real* elapsed time,
 * except for every tenth minute, exactly per the standard. Non-drop counts
 * `nominal * 3600 * hours + ...` with no skipping.
 *
 * Conversion to/from micros uses the exact frame grid: a timecode value maps
 * to the microsecond at the start of its frame, so round trips are stable to
 * at most half a [FrameRate.frameDurationMicros].
 */
data class Timecode(
    val hours: Int,
    val minutes: Int,
    val seconds: Int,
    val frames: Int,
    val frameRate: FrameRate,
    val dropFrame: Boolean,
) {
    init {
        require(hours in 0..99) { "hours must be in 0..99, was $hours" }
        require(minutes in 0..59) { "minutes must be in 0..59, was $minutes" }
        require(seconds in 0..59) { "seconds must be in 0..59, was $seconds" }
        require(frames in 0 until frameRate.nominalFps) {
            "frames must be in 0 until ${frameRate.nominalFps}, was $frames"
        }
        if (dropFrame) {
            val nominal = frameRate.nominalFps
            require(nominal == 30 || nominal == 60) {
                "drop-frame is only defined for nominal 30/60fps, got $frameRate"
            }
        }
    }

    /** Total nominal-grid frames this label represents (drop frame not applied). */
    val totalNominalFrames: Micros
        get() = (((hours.toLong() * 60L + minutes) * 60L + seconds) * frameRate.nominalFps) + frames

    /** Microseconds at the start of this timecode's frame. */
    fun toMicros(): Micros {
        val nominal = frameRate.nominalFps.toLong()
        val realFrames = if (dropFrame) {
            val d = nominal / 15L
            val perMinute = nominal * 60L - d
            val perTenMinutes = nominal * 600L - d * 9L
            val tens = totalNominalFrames / perTenMinutes
            val remainder = totalNominalFrames % perTenMinutes
            val dropped = d * 9L * tens + d * ((remainder - d) / perMinute)
            totalNominalFrames - dropped
        } else {
            totalNominalFrames
        }
        return Frames.frameStartMicros(realFrames, frameRate)
    }

    fun toDisplayString(): String {
        val separator = if (dropFrame) ";" else ":"
        fun pad(value: Int) = value.toString().padStart(2, '0')
        return buildString {
            append(pad(hours)).append(':')
            append(pad(minutes)).append(':')
            append(pad(seconds)).append(separator).append(pad(frames))
        }
    }

    override fun toString(): String = toDisplayString()

    companion object {

        /** SMPTE label for [micros] on the given timebase. */
        fun fromMicros(
            micros: Micros,
            frameRate: FrameRate,
            dropFrame: Boolean = false,
        ): Timecode {
            require(micros >= 0L) { "micros must be non-negative, was $micros" }
            val wholeFrames = Frames.frameIndexAt(micros, frameRate, FrameRounding.ROUND)
            if (!dropFrame) return decomposeNominal(wholeFrames, frameRate)
            return decomposeDropFrame(wholeFrames, frameRate)
        }

        /** Non-drop: the real frame count labels directly at the nominal grid. */
        private fun decomposeNominal(realFrames: Micros, frameRate: FrameRate): Timecode =
            decompose(realFrames, frameRate, dropFrame = false)

        private fun decomposeDropFrame(realFrames: Micros, frameRate: FrameRate): Timecode {
            val nominal = frameRate.nominalFps.toLong()
            val d = nominal / 15L
            val perMinute = nominal * 60L - d
            val perTenMinutes = nominal * 600L - d * 9L
            val tens = realFrames / perTenMinutes
            val remainder = realFrames % perTenMinutes
            val shifted = if (remainder > d) {
                realFrames + d * 9L * tens + d * ((remainder - d) / perMinute)
            } else {
                realFrames + d * 9L * tens
            }
            return decompose(shifted, frameRate, dropFrame = true)
        }

        private fun decompose(totalNominalFrames: Micros, frameRate: FrameRate, dropFrame: Boolean): Timecode {
            val nominal = frameRate.nominalFps.toLong()
            val hourFrames = nominal * 3_600L
            val hours = (totalNominalFrames / hourFrames).toInt().also { check(it in 0..99) }
            val rest = totalNominalFrames % hourFrames
            val minutes = (rest / (nominal * 60L)).toInt()
            val seconds = (rest / nominal % 60L).toInt()
            val frames = (rest % nominal).toInt()
            return Timecode(hours, minutes, seconds, frames, frameRate, dropFrame)
        }
    }
}