package com.exodi.aycut.core.model

import com.exodi.aycut.core.math.FrameRate
import com.exodi.aycut.core.math.FrameRounding
import com.exodi.aycut.core.math.Frames

/**
 * A video editing sequence: canvas size, frame rate, and a set of tracks.
 *
 * All timeline timing is integer microseconds (see [Micros]). [frameRate] is
 * the approximate decimal; [timebase] is the exact rational the engine counts
 * frames on, derived from [frameRate]. Time mapping never rounds to frames
 * unless explicitly requested (e.g. [frameAt]).
 */
data class Sequence(
    val width: Int,
    val height: Int,
    val frameRate: Double,
    val tracks: List<Track> = emptyList(),
) {
    init {
        require(width > 0) { "width must be positive" }
        require(height > 0) { "height must be positive" }
        require(frameRate > 0.0) { "frameRate must be positive" }
        require(tracks.map { it.id }.distinct().size == tracks.size) {
            "duplicate track ids"
        }
    }

    /** Exact rational timebase the engine counts frames on. */
    val timebase: FrameRate
        get() = FrameRate.of(frameRate)

    /** Length of the sequence: the farthest point any clip reaches. */
    val durationMicros: Micros
        get() = tracks.maxOfOrNull { it.duration } ?: 0L

    /** Whole (export) frame index whose span contains [micros]. */
    fun frameAt(micros: Micros): Long =
        Frames.frameIndexAt(micros, timebase, FrameRounding.FLOOR)

    /** Number of whole frames covering the sequence length. */
    val frameCount: Long
        get() = Frames.framesForMicros(durationMicros, timebase)

    fun track(trackId: TrackId): Track? = tracks.firstOrNull { it.id == trackId }

    /** Replace the track with [track] by id, or append it if absent. */
    fun withTrack(track: Track): Sequence {
        val index = tracks.indexOfFirst { it.id == track.id }
        val updated = if (index >= 0) {
            tracks.toMutableList().also { it[index] = track }
        } else {
            tracks + track
        }
        return copy(tracks = updated)
    }

    companion object {
        fun createEmpty(
            width: Int = 1920,
            height: Int = 1080,
            frameRate: Double = 30.0,
            tracks: List<Track> = emptyList(),
        ): Sequence = Sequence(width, height, frameRate, tracks)
    }
}