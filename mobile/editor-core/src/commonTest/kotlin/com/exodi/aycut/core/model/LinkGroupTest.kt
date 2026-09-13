package com.exodi.aycut.core.model

import kotlin.test.Test
import kotlin.test.assertEquals

class LinkGroupTest {

    private val g1 = LinkGroupId("g1")
    private val g2 = LinkGroupId("g2")

    private fun clip(id: String, timelineIn: Micros, groupId: LinkGroupId? = null): Clip =
        Clip(ClipId(id), MediaId("clip.mp4"), TimeRange(0L, 10L), timelineIn, groupId = groupId)

    private fun sequence(): Sequence {
        val track = Track.empty(TrackId("v1"))
            .plusClip(clip("c1", 0L, g1))
            .plusClip(clip("c2", 20L, g1))
            .plusClip(clip("c3", 40L, g2))
            .plusClip(clip("c4", 60L))
        return Sequence.createEmpty(tracks = listOf(track))
    }

    @Test
    fun `linkGroups maps each group id to its clip ids`() {
        assertEquals(
            mapOf(g1 to setOf(ClipId("c1"), ClipId("c2")), g2 to setOf(ClipId("c3"))),
            sequence().linkGroups(),
        )
    }

    @Test
    fun `linkedClips returns the whole group including the queried clip`() {
        assertEquals(setOf(ClipId("c1"), ClipId("c2")), sequence().linkedClips(ClipId("c1")))
    }

    @Test
    fun `linkedClips on an unlinked or unknown clip returns the empty set`() {
        assertEquals(emptySet<ClipId>(), sequence().linkedClips(ClipId("c4")))
        assertEquals(emptySet<ClipId>(), sequence().linkedClips(ClipId("ghost")))
    }
}