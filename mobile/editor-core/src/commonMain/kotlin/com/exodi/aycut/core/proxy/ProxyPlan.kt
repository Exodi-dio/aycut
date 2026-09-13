package com.exodi.aycut.core.proxy

import com.exodi.aycut.core.model.ClipId
import com.exodi.aycut.core.model.Micros

/** Planned proxy work item (pure scheduler — no I/O). */
data class ProxyEntry(
    val clipId: ClipId,
    val scale: Int,
    val width: Int,
    val height: Int,
    val estimatedDuration: Micros,
)

/** Scheduler that picks which clips to proxy at which scale. */
object ProxyPlanner {

    fun plan(
        availableBytes: Long,
        clips: List<Triple<ClipId, Int, Int>>,   // (clipId, width, height)
        durations: Map<ClipId, Micros>,
        scale: Int = 4,
    ): List<ProxyEntry> {
        if (clips.isEmpty()) return emptyList()
        val costPerByte = 0.00001   // rough placeholder budget weight
        val entries = clips.mapNotNull { (id, w, h) ->
            val dur = durations[id] ?: return@mapNotNull null
            val sw = (w / scale).coerceAtLeast(1)
            val sh = (h / scale).coerceAtLeast(1)
            ProxyEntry(id, scale, sw, sh, dur)
        }
        val result = mutableListOf<ProxyEntry>()
        var budget = availableBytes
        // Highest-duration clips first so they benefit most from reduced resolution.
        for (entry in entries.sortedByDescending { it.estimatedDuration }) {
            val cost = entry.width.toLong() * entry.height.toLong() * entry.estimatedDuration * costPerByte
            if (cost <= budget) {
                result += entry
                budget -= cost.toLong()
            }
        }
        return result
    }

    /** Scale factor needed to fit [width]x[height] within a bounding box. */
    fun scaleToFit(width: Int, height: Int, box: Int): Int =
        maxOf(1, maxOf(width.coerceAtLeast(1), height.coerceAtLeast(1)) / box)
}