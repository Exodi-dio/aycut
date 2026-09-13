package com.exodi.aycut.core.cache

import com.exodi.aycut.core.model.ClipId
import com.exodi.aycut.core.model.Micros

/** Priority score requesting an asset stay warm in cache. */
data class CacheScore(val priority: Int, val lastAccessMicros: Micros)

/** What the cache should hold next, given a budget and access history. */
data class CachePlan(val keep: Set<ClipId>, val evict: Set<ClipId>)

/** LRU-with-priority cache eviction planner (pure scheduling — no I/O). */
object CachePlanner {

    fun plan(
        budgetBytes: Long,
        sizesBytes: Map<ClipId, Long>,
        scores: Map<ClipId, CacheScore>,
    ): CachePlan {
        if (sizesBytes.isEmpty()) return CachePlan(emptySet(), emptySet())
        val ranked = scores.entries.sortedWith(
            compareByDescending<Map.Entry<ClipId, CacheScore>> { it.value.priority }
                .thenByDescending { it.value.lastAccessMicros },
        )
        val keep = mutableSetOf<ClipId>()
        var used = 0L
        for ((id, _) in ranked) {
            val size = sizesBytes[id] ?: continue
            if (used + size <= budgetBytes) {
                keep += id
                used += size
            }
        }
        return CachePlan(keep = keep, evict = scores.keys - keep)
    }

    fun clear(): CachePlan = CachePlan(emptySet(), emptySet())
}