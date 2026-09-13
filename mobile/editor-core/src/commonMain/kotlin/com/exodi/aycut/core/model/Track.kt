package com.exodi.aycut.core.model

/** Opaque identity of a track inside a sequence. */
@JvmInline
value class TrackId(val raw: String)

/**
 * An ordered lane of clips.
 *
 * [type] declares what the lane carries (see [TrackType]); clips on a lane
 * must be of the matching kind, so an audio lane never feeds the video
 * compositor and vice versa. The timeline invariants are identical for every
 * type:
 *  - clips are sorted by [Clip.timelineIn];
 *  - consecutive clips never overlap (a clip may not start inside or at the
 *    end of its predecessor); gaps between clips are allowed.
 */
data class Track(
    val id: TrackId,
    val clips: List<Clip>,
    val type: TrackType = TrackType.VIDEO,
) {
    init {
        var previous: Clip? = null
        for (clip in clips) {
            previous?.let { prev ->
                check(prev.timelineIn < clip.timelineIn) {
                    "clips must be sorted by timelineIn"
                }
                check(clip.timelineIn >= prev.timelineEnd) {
                    "clip ${clip.id} overlaps ${prev.id}"
                }
            }
            previous = clip
        }
    }

    val duration: Micros
        get() = clips.maxOfOrNull { it.timelineEnd } ?: 0L

    /** The clip covering [timelineTime]; on a boundary, the later clip wins. */
    fun clipAt(timelineTime: Micros): Clip? =
        clips.lastOrNull { timelineTime in it.timelineRange }

    fun indexOfClip(clipId: ClipId): Int = clips.indexOfFirst { it.id == clipId }

    fun clip(clipId: ClipId): Clip? = clips.firstOrNull { it.id == clipId }

    /** Insert a clip at its timeline position, preserving sort order. */
    fun plusClip(clip: Clip): Track {
        val sorted = (clips + clip).sortedBy { it.timelineIn }.distinctBy { it.id }
        require(sorted.size == clips.size + 1) { "clip ${clip.id} already on track" }
        return Track(id, sorted, type)
    }

    fun removeClip(clipId: ClipId): Track =
        Track(id, clips.filterNot { it.id == clipId }, type)

    /** Replace a clip, preserving sorted order and overlap invariants. */
    fun replaceClip(clipId: ClipId, replacement: Clip): Track {
        val existing = clip(clipId) ?: error("clip $clipId not on track")
        val mapped = clips.map { if (it.id == clipId) replacement else it }
        return Track(id, mapped.sortedBy { it.timelineIn }, type)
    }

    /** Copy this lane with a new [type]. */
    fun withType(type: TrackType): Track = copy(type = type)

    companion object {
        fun empty(id: TrackId, type: TrackType = TrackType.VIDEO): Track =
            Track(id, emptyList(), type)
    }
}