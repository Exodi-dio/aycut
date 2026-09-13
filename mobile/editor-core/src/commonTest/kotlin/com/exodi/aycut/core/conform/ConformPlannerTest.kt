package com.exodi.aycut.core.conform

import com.exodi.aycut.core.model.Clip
import com.exodi.aycut.core.model.ClipId
import com.exodi.aycut.core.model.MediaId
import com.exodi.aycut.core.model.Sequence
import com.exodi.aycut.core.model.TimeRange
import com.exodi.aycut.core.model.Track
import com.exodi.aycut.core.model.TrackId
import kotlin.test.Test
import kotlin.test.assertEquals

class ConformPlannerTest {

    @Test
    fun `plan returns nothing for an empty sequence`() {
        assertEquals(emptyList<ConformTarget>(), ConformPlanner.plan(Sequence.createEmpty()))
    }

    @Test
    fun `plan returns nothing even when clips are present`() {
        val sequence = Sequence.createEmpty(
            tracks = listOf(
                Track.empty(TrackId("v1")).plusClip(
                    Clip(ClipId("c1"), MediaId("a.mp4"), TimeRange(0L, 1_000_000L), 0L),
                ),
            ),
        )
        assertEquals(emptyList<ConformTarget>(), ConformPlanner.plan(sequence))
    }

    @Test
    fun `needsResample is true only when source and destination frame rates differ`() {
        val resample = ConformTarget("c1", 1920, 1080, 30.0, 60.0)
        val conformed = ConformTarget("c2", 1920, 1080, 30.0, 30.0)
        assertEquals(true, resample.needsResample)
        assertEquals(false, conformed.needsResample)
    }
}