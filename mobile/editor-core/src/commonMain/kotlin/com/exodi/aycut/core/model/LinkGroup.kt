package com.exodi.aycut.core.model

/** Identity of a group of clips that must move/trim together. */
@JvmInline
value class LinkGroupId(val raw: String)

/**
 * Named links between clips on the timeline: clips sharing a [LinkGroupId]
 * behave as one unit for moves and trims.
 */
data class LinkGroup(val id: LinkGroupId, val clipIds: Set<ClipId>) {

    // ClipIds are unique per sequence (project invariant), so per-group
    // membership rows are unambiguous (see Sequence.linkGroups).

    init {
        require(clipIds.isNotEmpty()) { "link group must reference at least one clip" }
    }
}

/** Extract the link groups present in a sequence (link field on Clips). */
fun Sequence.linkGroups(): Map<LinkGroupId, Set<ClipId>> {
    val byId = LinkedHashMap<LinkGroupId, MutableSet<ClipId>>()
    for (track in tracks) {
        for (clip in track.clips) {
            val group = clip.groupId ?: continue
            byId.getOrPut(group) { linkedSetOf() }.add(clip.id)
        }
    }
    return byId.entries.associate { it.key to it.value.toSet() }
}

/** Every clip sharing the link group of [clipId] (including itself if linked). */
fun Sequence.linkedClips(clipId: ClipId): Set<ClipId> {
    val clip = tracks.firstNotNullOfOrNull { it.clip(clipId) } ?: return emptySet()
    val group = clip.groupId ?: return emptySet()
    return linkGroups()[group] ?: emptySet()
}