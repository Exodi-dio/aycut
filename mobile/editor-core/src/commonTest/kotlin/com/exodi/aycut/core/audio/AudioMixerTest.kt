package com.exodi.aycut.core.audio

import com.exodi.aycut.core.model.AudioEnvelope
import com.exodi.aycut.core.model.Clip
import com.exodi.aycut.core.model.ClipId
import com.exodi.aycut.core.model.EnvelopePoint
import com.exodi.aycut.core.model.MediaId
import com.exodi.aycut.core.model.Sequence
import com.exodi.aycut.core.model.TimeRange
import com.exodi.aycut.core.model.Track
import com.exodi.aycut.core.model.TrackId
import com.exodi.aycut.core.model.TrackType
import kotlin.test.Test
import kotlin.test.assertEquals

class AudioMixerTest {

    private val media = MediaId("clip.mp4")

    private fun clip(
        id: String,
        timelineIn: Long,
        duration: Long,
        gain: Double = 1.0,
        fadeIn: Long = 0L,
        fadeOut: Long = 0L,
        envelope: AudioEnvelope? = null,
    ) = Clip(
        id = ClipId(id),
        media = media,
        sourceRange = TimeRange(0L, duration),
        timelineIn = timelineIn,
        gain = gain,
        fadeIn = fadeIn,
        fadeOut = fadeOut,
        audioEnvelope = envelope,
    )

    private fun audioTrack(id: String, vararg clips: Clip): Track =
        Track.empty(TrackId(id), TrackType.AUDIO).let { track ->
            clips.fold(track) { acc, clip -> acc.plusClip(clip) }
        }

    @Test
    fun `empty sequence is silent`() {
        assertEquals(0.0, AudioMixer.mixGain(Sequence.createEmpty(), 0L), 1e-12)
    }

    @Test
    fun `single unity audio clip yields unity mix`() {
        val sequence = Sequence.createEmpty(
            tracks = listOf(audioTrack("a1", clip("c1", 0L, 1_000_000L))),
        )
        assertEquals(1.0, AudioMixer.mixGain(sequence, 500_000L), 1e-12)
    }

    @Test
    fun `constant gain scales the mix`() {
        val sequence = Sequence.createEmpty(
            tracks = listOf(audioTrack("a1", clip("c1", 0L, 1_000_000L, gain = 0.5))),
        )
        assertEquals(0.5, AudioMixer.mixGain(sequence, 500_000L), 1e-12)
    }

    @Test
    fun `video and title tracks carry no audio bus`() {
        val video = Track.empty(TrackId("v1"), TrackType.VIDEO)
            .plusClip(clip("c1", 0L, 1_000_000L))
        val title = Track.empty(TrackId("t1"), TrackType.TITLE)
            .plusClip(clip("c2", 0L, 1_000_000L))
        val sequence = Sequence.createEmpty(tracks = listOf(video, title))
        assertEquals(0.0, AudioMixer.mixGain(sequence, 500_000L), 1e-12)
    }

    @Test
    fun `overlapping clips on separate audio lanes sum`() {
        val sequence = Sequence.createEmpty(
            tracks = listOf(
                audioTrack("a1", clip("c1", 0L, 1_000_000L)),
                audioTrack("a2", clip("c2", 500_000L, 1_000_000L)),
            ),
        )
        assertEquals(1.0, AudioMixer.mixGain(sequence, 250_000L), 1e-12)
        assertEquals(2.0, AudioMixer.mixGain(sequence, 750_000L), 1e-12)
    }

    @Test
    fun `master gain scales the full bus and can silence it`() {
        val sequence = Sequence.createEmpty(
            tracks = listOf(audioTrack("a1", clip("c1", 0L, 1_000_000L))),
            masterGain = 0.25,
        )
        assertEquals(0.25, AudioMixer.mixGain(sequence, 500_000L), 1e-12)
        assertEquals(0.0, AudioMixer.mixGain(sequence.copy(masterGain = 0.0), 500_000L), 1e-12)
    }

    @Test
    fun `fade in ramps from silence`() {
        val sequence = Sequence.createEmpty(
            tracks = listOf(audioTrack("a1", clip("c1", 0L, 1_000_000L, fadeIn = 1_000_000L))),
        )
        assertEquals(0.0, AudioMixer.mixGain(sequence, 0L), 1e-12)
        assertEquals(0.5, AudioMixer.mixGain(sequence, 500_000L), 1e-12)
        assertEquals(1.0, AudioMixer.mixGain(sequence, 999_999L), 1e-12)
    }

    @Test
    fun `fade out ramps to silence at the clip end`() {
        val sequence = Sequence.createEmpty(
            tracks = listOf(audioTrack("a1", clip("c1", 0L, 1_000_000L, fadeOut = 1_000_000L))),
        )
        assertEquals(0.5, AudioMixer.mixGain(sequence, 500_000L), 1e-12)
        assertEquals(0.25, AudioMixer.mixGain(sequence, 750_000L), 1e-12)
    }

    @Test
    fun `envelope automation overrides the static gain`() {
        val envelope = AudioEnvelope(
            listOf(EnvelopePoint(0L, 0.0), EnvelopePoint(1_000_000L, 1.0)),
        )
        val sequence = Sequence.createEmpty(
            tracks = listOf(
                audioTrack("a1", clip("c1", 0L, 1_000_000L, gain = 0.5, envelope = envelope)),
            ),
        )
        assertEquals(0.0, AudioMixer.mixGain(sequence, 0L), 1e-12)
        assertEquals(0.5, AudioMixer.mixGain(sequence, 500_000L), 1e-12)
        assertEquals(1.0, AudioMixer.mixGain(sequence, 999_999L), 1e-12)
    }

    @Test
    fun `mix is silent in gaps between audio clips`() {
        val sequence = Sequence.createEmpty(
            tracks = listOf(audioTrack("a1", clip("c1", 0L, 100L), clip("c2", 300L, 100L))),
        )
        assertEquals(1.0, AudioMixer.mixGain(sequence, 50L), 1e-12)
        assertEquals(0.0, AudioMixer.mixGain(sequence, 200L), 1e-12)
        assertEquals(1.0, AudioMixer.mixGain(sequence, 350L), 1e-12)
    }
}