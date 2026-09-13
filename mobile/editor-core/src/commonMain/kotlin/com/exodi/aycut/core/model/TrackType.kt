package com.exodi.aycut.core.model

/**
 * The kind of content a [Track] carries.
 *
 * Each lane plays one role and consumes one render payload:
 *  - [VIDEO]: picture clips, composited by z-order into the frame;
 *  - [AUDIO]: sound clips, summed onto the audio bus by [com.exodi.aycut.core.audio.AudioMixer];
 *  - [TITLE]: overlay clips rendered above [VIDEO] lanes.
 */
enum class TrackType {
    VIDEO,
    AUDIO,
    TITLE,
}