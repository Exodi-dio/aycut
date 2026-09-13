package com.exodi.aycut.core.timeline

import com.exodi.aycut.core.model.MediaId
import com.exodi.aycut.core.model.Micros
import com.exodi.aycut.core.model.Sequence
import com.exodi.aycut.core.model.TrackId

/**
 * Where a point on the timeline translates to in the source media of the
 * clip covering it.
 */
data class SourcePosition(
    val media: MediaId,
    val sourceTime: Micros,
)

/**
 * Maps timeline (playhead) time to source time per track.
 *
 * Clips store their in/out trims as source + timeline ranges and project
 * through [com.exodi.aycut.core.math.SourceProjection], which keeps
 * `sourceTime = source.start + (offset * playRate)` exact at unit speed and
 * inverts the direction for reversed clips. The mapping survives every edit:
 * split, start/end trim, and move all keep the projection exact. Gaps on a
 * track and unknown tracks map to nothing.
 */
object TimeMapper {

    /**
     * @return the [SourcePosition] for [timelineTime] on [trackId], or null
     * if the time falls in a gap or the track does not exist.
     */
    fun sourcePositionAt(
        sequence: Sequence,
        trackId: TrackId,
        timelineTime: Micros,
    ): SourcePosition? {
        val track = sequence.track(trackId) ?: return null
        val clip = track.clipAt(timelineTime) ?: return null
        return SourcePosition(clip.media, clip.sourceTimeAt(timelineTime))
    }
}