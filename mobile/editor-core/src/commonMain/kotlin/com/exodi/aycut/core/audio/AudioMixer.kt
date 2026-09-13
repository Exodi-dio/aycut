package com.exodi.aycut.core.audio

import com.exodi.aycut.core.model.Micros
import com.exodi.aycut.core.model.Sequence
import com.exodi.aycut.core.model.TrackType

/**
 * Pure mixdown math for a [Sequence].
 *
 * The instantaneous mix level at a timeline instant is the sum of the linear
 * gain of every clip covering that instant on an [TrackType.AUDIO] lane,
 * scaled by the sequence's [Sequence.masterGain]. Video and title lanes carry
 * no audio bus and never contribute. When several clips overlap in time the
 * levels sum, mirroring how androidApp will route decoded audio into a mixer
 * (ducking and R128 loudness are applied later, at E10).
 *
 * All components are linear gain, not decibels; the dB <-> linear conversion
 * is the UI's concern. This object touches no Android or audio API — it is a
 * pure function of the model that renderers sample per output instant.
 */
object AudioMixer {

    /** Sum of audio-clip gains covering [at], scaled by the master gain. */
    fun mixGain(sequence: Sequence, at: Micros): Double {
        var bus = 0.0
        for (track in sequence.tracks) {
            if (track.type != TrackType.AUDIO) continue
            val clip = track.clipAt(at) ?: continue
            bus += clip.gainAt(at - clip.timelineIn)
        }
        return bus * sequence.masterGain
    }
}