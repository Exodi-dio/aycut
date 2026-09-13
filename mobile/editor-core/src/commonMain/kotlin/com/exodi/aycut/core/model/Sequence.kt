package com.exodi.aycut.core.model

/**
 * A video editing sequence: canvas size, frame rate, and a set of tracks.
 *
 * All timeline timing is integer microseconds (see [Micros]); [frameRate] is
 * informational for now — time mapping is never rounded to frames until
 * explicitly requested.
 */
data class Sequence(
    val width: Int,
    val height: Int,
    val frameRate: Double,
    val tracks: List<Track>,
) {
    init {
        require(width > 0) { "width must be positive" }
        require(height > 0) { "height must be positive" }
        require(frameRate > 0.0) { "frameRate must be positive" }
        require(tracks.map { it.id }.distinct().size == tracks.size) {
            "duplicate track ids"
        }
    }

    /** Length of the sequence: the farthest point any clip reaches. */
    val durationMicros: Micros
        get() = tracks.maxOfOrNull { it.duration } ?: 0L

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