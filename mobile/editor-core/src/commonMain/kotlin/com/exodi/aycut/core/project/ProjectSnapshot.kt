package com.exodi.aycut.core.project

import com.exodi.aycut.core.model.Clip
import com.exodi.aycut.core.model.ClipId
import com.exodi.aycut.core.model.MediaId
import com.exodi.aycut.core.model.Sequence
import com.exodi.aycut.core.model.TimeRange
import com.exodi.aycut.core.model.Track
import com.exodi.aycut.core.model.TrackId
import kotlinx.serialization.Serializable

/**
 * Versioned, portable on-disk representation of a project.
 *
 * This schema is the stable wire/disk format and must evolve by adding a new
 * [version] branch, never by mutating existing fields in place. Field types
 * mirror the core model deliberately as plain primitives so the format does
 * not couple to the in-memory value classes.
 */
@Serializable
data class ProjectSnapshot(
    val version: Int = ProjectCodec.CURRENT_VERSION,
    val name: String,
    val width: Int,
    val height: Int,
    val frameRate: Double,
    val tracks: List<SnapshotTrack> = emptyList(),
)

@Serializable
data class SnapshotTrack(
    val id: String,
    val clips: List<SnapshotClip> = emptyList(),
)

@Serializable
data class SnapshotClip(
    val id: String,
    val media: String,
    val sourceStart: Long,
    val sourceDuration: Long,
    val timelineIn: Long,
)

/** Convert the in-memory model to its portable form. */
fun Sequence.toSnapshot(name: String): ProjectSnapshot = ProjectSnapshot(
    name = name,
    width = width,
    height = height,
    frameRate = frameRate,
    tracks = tracks.map { track ->
        SnapshotTrack(
            id = track.id.raw,
            clips = track.clips.map { clip -> clip.toSnapshot() },
        )
    },
)

private fun Clip.toSnapshot(): SnapshotClip = SnapshotClip(
    id = id.raw,
    media = media.raw,
    sourceStart = sourceRange.start,
    sourceDuration = sourceRange.durationMicros,
    timelineIn = timelineIn,
)

/**
 * Rebuild the in-memory model from a portable snapshot.
 *
 * Must go through [ProjectCodec.decodeToSequence] so invariants are validated.
 */
internal fun ProjectSnapshot.toSequence(): Sequence = Sequence(
    width = width,
    height = height,
    frameRate = frameRate,
    tracks = tracks.map { track ->
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
    },
)