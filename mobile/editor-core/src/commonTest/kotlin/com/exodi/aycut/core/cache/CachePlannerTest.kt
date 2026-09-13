package com.exodi.aycut.core.cache

import com.exodi.aycut.core.model.ClipId
import kotlin.test.Test
import kotlin.test.assertEquals

class CachePlannerTest {

    private val a = ClipId("a")
    private val b = ClipId("b")

    @Test
    fun `higher priority is kept and lower evicted when budget is tight`() {
        val plan = CachePlanner.plan(
            budgetBytes = 100L,
            sizesBytes = mapOf(a to 100L, b to 100L),
            scores = mapOf(
                a to CacheScore(priority = 10, lastAccessMicros = 50L),
                b to CacheScore(priority = 1, lastAccessMicros = 99L),
            ),
        )
        assertEquals(setOf(a), plan.keep)
        assertEquals(setOf(b), plan.evict)
    }

    @Test
    fun `later access wins ties on equal priority`() {
        val plan = CachePlanner.plan(
            budgetBytes = 100L,
            sizesBytes = mapOf(a to 100L, b to 100L),
            scores = mapOf(
                a to CacheScore(priority = 10, lastAccessMicros = 10L),
                b to CacheScore(priority = 10, lastAccessMicros = 99L),
            ),
        )
        assertEquals(setOf(b), plan.keep)
        assertEquals(setOf(a), plan.evict)
    }

    @Test
    fun `nothing is evicted when budget fits every score`() {
        val plan = CachePlanner.plan(
            budgetBytes = 200L,
            sizesBytes = mapOf(a to 100L, b to 100L),
            scores = mapOf(
                a to CacheScore(priority = 5, lastAccessMicros = 10L),
                b to CacheScore(priority = 5, lastAccessMicros = 20L),
            ),
        )
        assertEquals(setOf(a, b), plan.keep)
        assertEquals(emptySet<ClipId>(), plan.evict)
    }

    @Test
    fun `clips without a size entry are never kept but still evicted`() {
        val plan = CachePlanner.plan(
            budgetBytes = 150L,
            sizesBytes = mapOf(a to 100L),
            scores = mapOf(
                a to CacheScore(priority = 10, lastAccessMicros = 1L),
                b to CacheScore(priority = 9, lastAccessMicros = 2L),
            ),
        )
        assertEquals(setOf(a), plan.keep)
        assertEquals(setOf(b), plan.evict)
    }
}