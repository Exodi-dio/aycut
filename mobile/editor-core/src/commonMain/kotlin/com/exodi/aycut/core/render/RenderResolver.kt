package com.exodi.aycut.core.render

import com.exodi.aycut.core.audio.AudioMixer
import com.exodi.aycut.core.composite.NormalizedRect
import com.exodi.aycut.core.composite.RenderLayout
import com.exodi.aycut.core.effect.EffectSampler
import com.exodi.aycut.core.effect.TransitionKind
import com.exodi.aycut.core.math.Timecode
import com.exodi.aycut.core.media.MediaAssetRegistry
import com.exodi.aycut.core.model.Clip
import com.exodi.aycut.core.model.MediaId
import com.exodi.aycut.core.model.Micros
import com.exodi.aycut.core.model.Sequence
import com.exodi.aycut.core.model.Track
import com.exodi.aycut.core.model.TrackType

/** One picture the compositor must draw for an active clip at a playhead. */
data class RenderLayer(
    val mediaId: MediaId,
    val sourceTime: Micros,
    val uvWindow: NormalizedRect,
    val outputRect: NormalizedRect,
    val rotationDegrees: Float,
    val opacity: Float,
)

/** The complete resolve of a [Sequence] at one timeline instant. */
data class CompositeFrame(
    val micros: Micros,
    val frameIndex: Long,
    val timecode: String,
    val videoLayers: List<RenderLayer>,
    val audioGain: Double,
)

/**
 * The single preview/export contract: turns [Sequence] + [MediaAssetRegistry]
 * into a [CompositeFrame] at any playhead. Video and title lanes contribute
 * ordered [RenderLayer]s (bottom first, later tracks on top); the audio bus
 * is the instantaneous [AudioMixer] mix.
 *
 * Transitions at a clip's head ramp the incoming layer's opacity; a
 * cross-dissolve additionally samples the previous clip on the same lane
 * behind it. Effects are sampled per clip ([EffectSampler]); audio-only
 * assets never produce a picture layer but still feed the mix.
 */
class RenderResolver(
    private val sequence: Sequence,
    private val media: MediaAssetRegistry,
) {

    private val targetAspect = sequence.width.toFloat() / sequence.height.toFloat()

    private val epsilon = 1e-6f

    fun resolve(at: Micros): CompositeFrame {
        require(at in 0L..sequence.durationMicros) {
            "at $at outside sequence span [0, ${sequence.durationMicros}]"
        }
        val layers = mutableListOf<RenderLayer>()
        for (track in sequence.tracks) {
            if (track.type == TrackType.AUDIO) continue
            collectTrackLayers(track, at, layers)
        }
        return CompositeFrame(
            micros = at,
            frameIndex = sequence.frameAt(at),
            timecode = Timecode.fromMicros(at, sequence.timebase).toDisplayString(),
            videoLayers = layers,
            audioGain = AudioMixer.mixGain(sequence, at),
        )
    }

    private fun collectTrackLayers(
        track: Track,
        at: Micros,
        out: MutableList<RenderLayer>,
    ) {
        val clip = track.clipAt(at) ?: return
        val transition = clip.transitionIn
        val inWindow = transition != null &&
            at >= clip.timelineIn &&
            at < clip.timelineIn + transition.durationMicros
        if (transition == null || !inWindow) {
            emitLayer(track, clip, at, at - clip.timelineIn,
                EffectSampler.effectiveOpacity(clip, at - clip.timelineIn), out)
            return
        }
        val progress = (at - clip.timelineIn).toFloat() / transition.durationMicros.toFloat()
        when (transition.kind) {
            TransitionKind.FADE_TO_BLACK -> {
                val opacity = EffectSampler.effectiveOpacity(clip, at - clip.timelineIn) * progress
                emitLayer(track, clip, at, at - clip.timelineIn, opacity, out)
            }
            TransitionKind.CROSS_DISSOLVE -> {
                emitOutgoing(track, clip, at, progress, transition.durationMicros, out)
                val opacity =
                    EffectSampler.effectiveOpacity(clip, at - clip.timelineIn) * progress
                emitLayer(track, clip, at, at - clip.timelineIn, opacity, out)
            }
        }
    }

    /** The clip directly before [clip] on its lane, sampled within the dissolve. */
    private fun emitOutgoing(
        track: Track,
        clip: Clip,
        at: Micros,
        progress: Float,
        durationMicros: Micros,
        out: MutableList<RenderLayer>,
    ) {
        val index = track.indexOfClip(clip.id)
        val previous = if (index > 0) track.clips[index - 1] else null
        if (previous == null) return
        val back = (progress * durationMicros).toLong()
        val previousTime = (clip.timelineIn - durationMicros + back)
            .coerceIn(previous.timelineIn, previous.timelineEnd - 1L)
        if (previous.timelineRange.contains(previousTime)) {
            emitLayer(
                track, previous, previousTime, previousTime - previous.timelineIn,
                EffectSampler.effectiveOpacity(previous, previousTime - previous.timelineIn),
                out,
            )
        }
    }

    private fun emitLayer(
        track: Track,
        clip: Clip,
        at: Micros,
        offsetInClip: Micros,
        opacity: Float,
        out: MutableList<RenderLayer>,
    ) {
        if (opacity <= epsilon) return
        val asset = media.asset(clip.media) ?: return
        if (asset.displayedWidth == 0 || asset.displayedHeight == 0) return // audio-only
        val displayedAspect =
            asset.displayedWidth.toFloat() / maxOf(asset.displayedHeight.toFloat(), 1f)
        val base = RenderLayout.fitRect(displayedAspect, targetAspect)
        val transformed = EffectSampler.transformRect(base, clip, offsetInClip)
        if (!transformed.intersectsUnit()) return
        val effectRotation = EffectSampler.rotationDegrees(clip, offsetInClip)
        val rotation = (asset.rotationDegrees + effectRotation).mod(360f)
        out += RenderLayer(
            mediaId = asset.id,
            sourceTime = clip.sourceTimeAt(at),
            uvWindow = RenderLayout.fillUvWindow(displayedAspect, targetAspect),
            outputRect = transformed.clampedToUnit(),
            rotationDegrees = rotation,
            opacity = opacity,
        )
    }
}