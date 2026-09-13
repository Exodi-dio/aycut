package com.exodi.aycut.core.caption

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class CaptionTest {

    @Test
    fun `overlapping or unsorted captions are rejected`() {
        assertFailsWith<IllegalArgumentException> {
            CaptionSet(listOf(Caption("a", 0L, 100L), Caption("b", 50L, 150L)))
        }
        assertFailsWith<IllegalArgumentException> {
            CaptionSet(listOf(Caption("b", 100L, 300L), Caption("a", 0L, 50L)))
        }
    }

    @Test
    fun `captionAt returns the containing caption and null outside`() {
        val set = CaptionSet(listOf(Caption("a", 0L, 100L), Caption("b", 100L, 300L)))
        assertEquals("a", set.captionAt(0L)?.text)
        assertEquals("a", set.captionAt(99L)?.text)
        assertEquals("b", set.captionAt(100L)?.text)
        assertNull(set.captionAt(300L))
        assertNull(set.captionAt(-1L))
    }

    @Test
    fun `durationMicros is the last caption end`() {
        val set = CaptionSet(listOf(Caption("a", 0L, 100L), Caption("b", 100L, 300L)))
        assertEquals(300L, set.durationMicros)
        assertEquals(0L, CaptionSet(emptyList()).durationMicros)
    }

    @Test
    fun `with forces sort order and rejects overlaps`() {
        val set = CaptionSet(listOf(Caption("late", 200L, 300L)))
        val merged = set.with(Caption("early", 0L, 50L))
        assertEquals(listOf("early", "late"), merged.captions.map { it.text })
        assertEquals(listOf(0L, 200L), merged.captions.map { it.startMicros })
        assertEquals(300L, merged.durationMicros)
        assertFailsWith<IllegalArgumentException> {
            merged.with(Caption("inner", 30L, 210L))
        }
    }
}