package com.exodi.aycut.core.project

import com.exodi.aycut.core.effect.ClipTransition
import com.exodi.aycut.core.effect.Effect
import com.exodi.aycut.core.effect.EffectId
import com.exodi.aycut.core.effect.Keyframe
import com.exodi.aycut.core.effect.ParamCurve
import com.exodi.aycut.core.effect.ParameterValue
import com.exodi.aycut.core.effect.TransitionKind
import com.exodi.aycut.core.model.AudioEnvelope
import com.exodi.aycut.core.model.Clip
import com.exodi.aycut.core.model.ClipId
import com.exodi.aycut.core.model.EnvelopePoint
import com.exodi.aycut.core.model.LinkGroupId
import com.exodi.aycut.core.model.Marker
import com.exodi.aycut.core.model.MediaId
import com.exodi.aycut.core.model.Sequence
import com.exodi.aycut.core.model.SpeedRamp
import com.exodi.aycut.core.model.RampSegment
import com.exodi.aycut.core.model.TimeRange
import com.exodi.aycut.core.model.Track
import com.exodi.aycut.core.model.TrackId
import com.exodi.aycut.core.model.TrackType
import kotlinx.serialization.Serializable

/**
 * Versioned, portable on-disk representation of a project (format v2).
 *
 * Evolves by adding defaulted fields; [ProjectCodec.CURRENT_VERSION] is 2.
 * v1 snapshots decode into this shape (defaults preserve v1 behavior) and
 * [Migration] stamps them to the current version.
 */
@Serializable
data class ProjectSnapshot(
    val version: Int = ProjectCodec.CURRENT_VERSION,
    val name: String,
    val width: Int,
    val height: Int,
    val frameRate: Double,
    val tracks: List<SnapshotTrack> = emptyList(),
    val masterGain: Double = 1.0,
    val playhead: Long = 0L,
    val markers: List<SnapshotMarker> = emptyList(),
)

@Serializable
data class SnapshotMarker(val label: String, val at: Long)

@Serializable
data class SnapshotTrack(
    val id: String,
    val clips: List<SnapshotClip> = emptyList(),
    val type: String = TrackType.VIDEO.name,
)

@Serializable
data class SnapshotClip(
    val id: String,
    val media: String,
    val sourceStart: Long,
    val sourceDuration: Long,
    val timelineIn: Long,
    val playRate: Double = 1.0,
    val reverse: Boolean = false,
    val gain: Double = 1.0,
    val fadeIn: Long = 0L,
    val fadeOut: Long = 0L,
    val envelope: SnapshotEnvelope? = null,
    val effects: List<SnapshotEffect> = emptyList(),
    val transitionIn: SnapshotTransition? = null,
    val groupId: String? = null,
    val speedRamp: SnapshotSpeedRamp? = null,
)

@Serializable
data class SnapshotEnvelope(val points: List<SnapshotEnvelopePoint>)

@Serializable
data class SnapshotEnvelopePoint(val offset: Long, val gain: Double)

@Serializable
data class SnapshotEffect(
    val id: String,
    val params: Map<String, SnapshotParamValue> = emptyMap(),
    val curves: Map<String, SnapshotCurve> = emptyMap(),
)

/** Serializable mirror of ParameterValue. */
@Serializable
sealed interface SnapshotParamValue {
    @Serializable
    data class Num(val value: Double) : SnapshotParamValue

    @Serializable
    data class Bool(val value: Boolean) : SnapshotParamValue

    @Serializable
    data class Color(val r: Float, val g: Float, val b: Float) : SnapshotParamValue

    @Serializable
    data class Point(val x: Float, val y: Float) : SnapshotParamValue
}

@Serializable
data class SnapshotCurve(val keyframes: List<SnapshotKeyframe>)

@Serializable
data class SnapshotKeyframe(val at: Long, val value: SnapshotParamValue, val easing: String = "LINEAR")

@Serializable
data class SnapshotTransition(val kind: String, val durationMicros: Long)

@Serializable
data class SnapshotSpeedRamp(val segments: List<SnapshotRampSegment>)

@Serializable
data class SnapshotRampSegment(val offsetInClip: Long, val playRate: Double)

/** Convert the in-memory model to its portable form. */
fun Sequence.toSnapshot(name: String): ProjectSnapshot = ProjectSnapshot(
    name = name,
    width = width,
    height = height,
    frameRate = frameRate,
    tracks = tracks.map { track -> track.toSnapshot() },
    masterGain = masterGain,
    playhead = playhead,
    markers = markers.map { SnapshotMarker(it.label, it.at) },
)

private fun Track.toSnapshot(): SnapshotTrack = SnapshotTrack(
    id = id.raw,
    clips = clips.map { it.toSnapshot() },
    type = type.name,
)

private fun Clip.toSnapshot(): SnapshotClip = SnapshotClip(
    id = id.raw,
    media = media.raw,
    sourceStart = sourceRange.start,
    sourceDuration = sourceRange.durationMicros,
    timelineIn = timelineIn,
    playRate = playRate,
    reverse = reverse,
    gain = gain,
    fadeIn = fadeIn,
    fadeOut = fadeOut,
    envelope = audioEnvelope?.let { SnapshotEnvelope(it.points.map { p -> SnapshotEnvelopePoint(p.offset, p.gain) }) },
    effects = effects.map { it.toSnapshot() },
    transitionIn = transitionIn?.let { SnapshotTransition(it.kind.name, it.durationMicros) },
    groupId = groupId?.raw,
    speedRamp = speedRamp?.let {
        SnapshotSpeedRamp(it.segments.map { s -> SnapshotRampSegment(s.offsetInClip, s.playRate) })
    },
)

private fun Effect.toSnapshot(): SnapshotEffect = SnapshotEffect(
    id = id.raw,
    params = params.mapValues { it.value.toSnapshot() },
    curves = curves.mapValues { SnapshotCurve(it.value.keyframes.map { k -> k.toSnapshot() }) },
)

private fun ParameterValue.toSnapshot(): SnapshotParamValue = when (this) {
    is ParameterValue.Num -> SnapshotParamValue.Num(value)
    is ParameterValue.Bool -> SnapshotParamValue.Bool(value)
    is ParameterValue.Color -> SnapshotParamValue.Color(r, g, b)
    is ParameterValue.Point -> SnapshotParamValue.Point(x, y)
}

private fun Keyframe.toSnapshot(): SnapshotKeyframe =
    SnapshotKeyframe(at, value.toSnapshot(), easing.name)

/**
 * Rebuild the in-memory model from a portable snapshot.
 *
 * Must go through [ProjectCodec.decodeToSequence] so invariants are validated.
 */
internal fun ProjectSnapshot.toSequence(): Sequence = Sequence(
    width = width,
    height = height,
    frameRate = frameRate,
    tracks = tracks.map { it.toModel() },
    masterGain = masterGain,
    playhead = playhead,
    markers = markers.map { Marker(it.label, it.at) },
)

private fun SnapshotTrack.toModel(): Track = Track(
    id = TrackId(id),
    clips = clips.map { it.toModel() },
    type = type.let { runCatching { TrackType.valueOf(it) }.getOrDefault(TrackType.VIDEO) },
)

private fun SnapshotClip.toModel(): Clip = Clip(
    id = ClipId(id),
    media = MediaId(media),
    sourceRange = TimeRange(sourceStart, sourceDuration),
    timelineIn = timelineIn,
    playRate = playRate,
    reverse = reverse,
    gain = gain,
    fadeIn = fadeIn,
    fadeOut = fadeOut,
    audioEnvelope = envelope?.let { AudioEnvelope(it.points.map { p -> EnvelopePoint(p.offset, p.gain) }) },
    effects = effects.map { it.toModel() },
    transitionIn = transitionIn?.let {
        ClipTransition(runCatching { TransitionKind.valueOf(it.kind) }.getOrDefault(TransitionKind.CROSS_DISSOLVE), it.durationMicros)
    },
    groupId = groupId?.let { LinkGroupId(it) },
    speedRamp = speedRamp?.let { SpeedRamp(it.segments.map { s -> RampSegment(s.offsetInClip, s.playRate) }) },
)

private fun SnapshotEffect.toModel(): Effect = Effect(
    id = EffectId(id),
    params = params.mapValues { it.value.toModel() },
    curves = curves.mapValues { ParamCurve(it.value.keyframes.map { k -> k.toModel() }) },
)

private fun SnapshotParamValue.toModel(): ParameterValue = when (this) {
    is SnapshotParamValue.Num -> ParameterValue.Num(value)
    is SnapshotParamValue.Bool -> ParameterValue.Bool(value)
    is SnapshotParamValue.Color -> ParameterValue.Color(r, g, b)
    is SnapshotParamValue.Point -> ParameterValue.Point(x, y)
}

private fun SnapshotKeyframe.toModel(): Keyframe = Keyframe(at, value.toModel())