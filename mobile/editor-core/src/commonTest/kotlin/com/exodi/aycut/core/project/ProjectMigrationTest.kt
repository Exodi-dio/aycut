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
import com.exodi.aycut.core.model.RampSegment
import com.exodi.aycut.core.model.Sequence
import com.exodi.aycut.core.model.SpeedRamp
import com.exodi.aycut.core.model.TimeRange
import com.exodi.aycut.core.model.Track
import com.exodi.aycut.core.model.TrackId
import com.exodi.aycut.core.model.TrackType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private const val V1_GOLDEN = """
    {"version":1,"name":"Golden","width":640,"height":480,"frameRate":25.0,
     "tracks":[{"id":"v1","clips":[
       {"id":"c1","media":"a.mp4","sourceStart":10,"sourceDuration":4,"timelineIn":0}]}]}
"""

private const val V1_EFFECTLESS_CLIP = """
    {"version":1,"name":"Leaf","width":1920,"height":1080,"frameRate":30.0,
     "tracks":[{"id":"v1","clips":[
       {"id":"c1","media":"a.mp4","sourceStart":0,"sourceDuration":8_000_000,"timelineIn":1_000_000}]}]}
"""

class ProjectMigrationTest {

    @Test
    fun `v1 snapshot decodes with v2 defaults`() {
        val sequence = ProjectCodec.decodeToSequence(V1_GOLDEN)
        assertEquals(1.0, sequence.masterGain)
        assertEquals(0L, sequence.playhead)
        assertTrue(sequence.markers.isEmpty())
        val track = sequence.tracks.single()
        assertEquals(TrackType.VIDEO, track.type)
        val clip = track.clips.single()
        assertEquals(1.0, clip.playRate)
        assertEquals(false, clip.reverse)
        assertEquals(1.0, clip.gain)
        assertEquals(0L, clip.fadeIn)
        assertEquals(0L, clip.fadeOut)
        assertEquals(null, clip.audioEnvelope)
        assertTrue(clip.effects.isEmpty())
        assertEquals(null, clip.transitionIn)
        assertEquals(null, clip.groupId)
        assertEquals(null, clip.speedRamp)
    }

    @Test
    fun `v1 snapshot is stamped to current version`() {
        val snapshot = ProjectCodec.decodeSnapshot(V1_GOLDEN)
        assertEquals(1, snapshot.version)
        val upgraded = Migration.upgradeToCurrent(snapshot)
        assertEquals(ProjectCodec.CURRENT_VERSION, upgraded.version)
    }

    @Test
    fun `full v2 round trip is identity`() {
        val envelopePoints = listOf(EnvelopePoint(0L, 0.5), EnvelopePoint(1_000_000L, 1.0))
        val fadeIn = 200_000L
        val fadeOut = 300_000L
        val clip = Clip(
            id = ClipId("c1"),
            media = MediaId("a.mp4"),
            sourceRange = TimeRange(0L, 8_000_000L),
            timelineIn = 0L,
            playRate = 1.0,
            reverse = false,
            gain = 0.5,
            fadeIn = fadeIn,
            fadeOut = fadeOut,
            audioEnvelope = AudioEnvelope(envelopePoints),
            effects = listOf(
                Effect(
                    id = EffectId("opacity"),
                    params = mapOf("opacity" to ParameterValue.Num(0.75)),
                    curves = mapOf(
                        "opacity" to ParamCurve(
                            listOf(Keyframe(0L, ParameterValue.Num(0.0)), Keyframe(1_000_000L, ParameterValue.Num(1.0))),
                        ),
                    ),
                ),
                Effect(
                    id = EffectId("tint"),
                    params = mapOf(
                        "color" to ParameterValue.Color(0.1f, 0.2f, 0.3f),
                        "mix" to ParameterValue.Bool(true),
                        "pos" to ParameterValue.Point(0.5f, 0.5f),
                    ),
                ),
            ),
            transitionIn = ClipTransition(TransitionKind.CROSS_DISSOLVE, 250_000L),
            groupId = LinkGroupId("g1"),
            speedRamp = SpeedRamp(
                listOf(RampSegment(0L, 0.5), RampSegment(1_000_000L, 2.0)),
            ),
        )
        val sequence = Sequence(
            width = 1280,
            height = 720,
            frameRate = 24.0,
            tracks = listOf(Track(TrackId("v1"), listOf(clip), TrackType.VIDEO)),
            masterGain = 0.7,
            playhead = 123_456L,
            markers = listOf(Marker("intro", 0L), Marker("outro", 2_000_000L)),
        )
        val json = ProjectCodec.encode(sequence, "Full")
        assertEquals(sequence, ProjectCodec.decodeToSequence(json))
    }

    @Test
    fun `encoding is stable for the same input`() {
        val sequence = Sequence.createEmpty(
            tracks = listOf(
                Track.empty(TrackId("v1"), TrackType.VIDEO).plusClip(
                    Clip(ClipId("c1"), MediaId("a.mp4"), TimeRange(0L, 5L), 0L),
                ),
            ),
        )
        val json1 = ProjectCodec.encode(sequence, "Stable")
        val json2 = ProjectCodec.encode(sequence, "Stable")
        assertEquals(json1, json2)
    }

    @Test
    fun `v1 json with effect fields missing decodes without crash`() {
        val sequence = ProjectCodec.decodeToSequence(V1_EFFECTLESS_CLIP)
        val clip = sequence.tracks.single().clips.single()
        assertEquals(8_000_000L, clip.sourceRange.durationMicros)
        assertEquals(1_000_000L, clip.timelineIn)
        assertTrue(clip.effects.isEmpty())
        assertEquals(null, clip.audioEnvelope)
        assertEquals(null, clip.transitionIn)
        assertEquals(null, clip.groupId)
        assertEquals(1.0, clip.gain)
    }
}