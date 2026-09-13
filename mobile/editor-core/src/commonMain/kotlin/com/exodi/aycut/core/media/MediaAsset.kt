package com.exodi.aycut.core.media

import com.exodi.aycut.core.model.MediaId
import com.exodi.aycut.core.model.Micros

/**
 * Runtime metadata for an imported media asset.
 *
 * Immutable and platform-free (no Uri/ContentResolver types) so the engine
 * can reason about asset capabilities — e.g. trim clamping, export sizing —
 * without knowing how the bytes were imported.
 */
data class MediaAsset(
    val id: MediaId,
    val displayName: String,
    val mimeType: String?,
    val durationMicros: Micros,
    val width: Int,
    val height: Int,
    val rotationDegrees: Int,
) {
    init {
        require(displayName.isNotBlank()) { "displayName must not be blank" }
        require(durationMicros >= 0L) { "duration must be non-negative" }
        require(width >= 0 && height >= 0) { "dimensions must be non-negative" }
        require(rotationDegrees >= 0) { "rotation must be non-negative" }
    }

    /** True when the media is captured sideways (90/270 deg). */
    val isRotated: Boolean
        get() = rotationDegrees % 180 != 0

    /** On-screen orientation once rotation metadata is applied. */
    val displayedWidth: Int
        get() = if (isRotated) height else width

    val displayedHeight: Int
        get() = if (isRotated) width else height

    /**
     * Evenly spaced thumbnail sample times: the midpoints of [count] equal
     * lane segments, so they are strictly increasing and never touch the
     * boundaries (avoids black first/last frames).
     */
    fun thumbnailTimes(count: Int): List<Micros> {
        require(count >= 0) { "count must be non-negative" }
        if (count == 0 || durationMicros == 0L) return emptyList()
        return List(count) { i -> ((2L * i + 1L) * durationMicros) / (2L * count) }
    }
}