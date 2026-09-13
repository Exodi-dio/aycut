package com.exodi.aycut.core.project

import com.exodi.aycut.core.model.Clip
import com.exodi.aycut.core.model.ClipId
import com.exodi.aycut.core.model.MediaId
import com.exodi.aycut.core.model.Micros
import com.exodi.aycut.core.model.Sequence
import com.exodi.aycut.core.model.TimeRange
import com.exodi.aycut.core.model.Track
import com.exodi.aycut.core.model.TrackId

/**
 * A field-level validation report (pure logic, no I/O).
 * Each check is an either-or: PASS contains the value, FAIL a message.
 */
sealed interface IntegrityCheck<out T> {
    data class Pass<T>(val value: T) : IntegrityCheck<T>
    data class Fail(val message: String) : IntegrityCheck<Nothing>
}

data class SnapshotIntegrityReport(
    val version: IntegrityCheck<Int>,
    val width: IntegrityCheck<Int>,
    val height: IntegrityCheck<Int>,
    val frameRate: IntegrityCheck<Double>,
    val tracks: IntegrityCheck<List<Track>>,
) {
    val isClean: Boolean get() = failures.isEmpty()
    val failures: List<String>
        get() = listOfNotNull(
            (version as? IntegrityCheck.Fail)?.message,
            (width as? IntegrityCheck.Fail)?.message,
            (height as? IntegrityCheck.Fail)?.message,
            (frameRate as? IntegrityCheck.Fail)?.message,
            (tracks as? IntegrityCheck.Fail)?.message,
        )
}

/** Pure-logic per-field validation against a raw snapshot. */
object SnapshotIntegrity {

    fun validate(snapshot: ProjectSnapshot): SnapshotIntegrityReport {
        val version = if (snapshot.version == ProjectCodec.CURRENT_VERSION)
            IntegrityCheck.Pass(snapshot.version) else
            IntegrityCheck.Fail("version ${snapshot.version} unsupported (expected ${ProjectCodec.CURRENT_VERSION})")
        val width = if (snapshot.width > 0) IntegrityCheck.Pass(snapshot.width) else
            IntegrityCheck.Fail("width must be positive")
        val height = if (snapshot.height > 0) IntegrityCheck.Pass(snapshot.height) else
            IntegrityCheck.Fail("height must be positive")
        val frameRate = if (snapshot.frameRate > 0.0) IntegrityCheck.Pass(snapshot.frameRate) else
            IntegrityCheck.Fail("frameRate must be positive")
        val tracks = try {
            val trackList = snapshot.tracks.map { track ->
                Track(
                    id = TrackId(track.id),
                    clips = track.clips.map { clip ->
                        Clip(
                            id = ClipId(clip.id),
                            media = MediaId(clip.media),
                            sourceRange = TimeRange(clip.sourceStart, clip.sourceDuration),
                            timelineIn = clip.timelineIn,
                        )
                    },
                )
            }
            // Validate clip-ids are unique and sorted.
            for (track in trackList) {
                require(track.clips.zipWithNext().all { (a, b) ->
                    a.timelineIn <= b.timelineIn && a.id != b.id
                }) { "clips in track ${track.id} are not sorted/unique" }
            }
            IntegrityCheck.Pass(trackList)
        } catch (e: IllegalArgumentException) {
            IntegrityCheck.Fail(e.message ?: "invalid track/clip data")
        }
        return SnapshotIntegrityReport(version, width, height, frameRate, tracks)
    }
}