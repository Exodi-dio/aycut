package com.exodi.aycut.core.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class MarkerTest {

    @Test
    fun `marker rejects a blank label`() {
        assertFailsWith<IllegalArgumentException> { Marker("", 0L) }
        assertFailsWith<IllegalArgumentException> { Marker("   ", 100L) }
    }

    @Test
    fun `marker rejects a negative time`() {
        assertFailsWith<IllegalArgumentException> { Marker("a", -1L) }
    }

    @Test
    fun `withMarker keeps markers sorted when inserting out of order`() {
        val sorted = Sequence.createEmpty()
            .withMarker(Marker("first", 300L))
            .withMarker(Marker("zero", 10L))
            .withMarker(Marker("middle", 200L))
        assertEquals(listOf(10L, 200L, 300L), sorted.markers.map { it.at })
        assertEquals(listOf("zero", "middle", "first"), sorted.markers.map { it.label })
    }

    @Test
    fun `withoutMarker removes markers at the given time`() {
        val sequence = Sequence(
            1920, 1080, 30.0,
            markers = listOf(Marker("a", 10L), Marker("b", 20L)),
        )
        assertEquals(listOf(Marker("a", 10L)), sequence.withoutMarker(20L).markers)
        assertEquals(2, sequence.markers.size)
    }
}