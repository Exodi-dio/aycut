package com.exodi.aycut.core.proxy

import com.exodi.aycut.core.model.ClipId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ProxyPlannerTest {

    private val a = Triple(ClipId("a"), 1920, 1080)
    private val b = Triple(ClipId("b"), 1920, 1080)
    private val c = Triple(ClipId("c"), 1920, 1080)

    private val durations = mapOf(
        ClipId("a") to 100_000_000L,
        ClipId("b") to 400_000_000L,
        ClipId("c") to 900_000_000L,
    )

    @Test
    fun `longest-duration clip is proxied first under an unbounded budget`() {
        val plan = ProxyPlanner.plan(
            availableBytes = 10_000_000_000L,
            clips = listOf(a, b, c),
            durations = durations,
        )
        assertEquals(
            listOf(
                ProxyEntry(ClipId("c"), 4, 480, 270, 900_000_000L),
                ProxyEntry(ClipId("b"), 4, 480, 270, 400_000_000L),
                ProxyEntry(ClipId("a"), 4, 480, 270, 100_000_000L),
            ),
            plan,
        )
    }

    @Test
    fun `small budget only proxies the clips that fit`() {
        val plan = ProxyPlanner.plan(
            availableBytes = 1_200_000_000L,
            clips = listOf(a, b, c),
            durations = durations,
        )
        assertEquals(listOf(ClipId("c")), plan.map { it.clipId })
    }

    @Test
    fun `result never exceeds the available budget`() {
        val plan = ProxyPlanner.plan(
            availableBytes = 1_684_800_000L,
            clips = listOf(a, b, c),
            durations = durations,
        )
        assertEquals(listOf(ClipId("c"), ClipId("b")), plan.map { it.clipId })
        val totalCost = plan.sumOf {
            (it.width.toLong() * it.height.toLong() * it.estimatedDuration * 0.00001).toLong()
        }
        assertEquals(1_684_800_000L, totalCost)
        assertTrue(totalCost <= 1_684_800_000L)
    }

    @Test
    fun `scaleToFit floors at one and divides by the box`() {
        assertEquals(4, ProxyPlanner.scaleToFit(1920, 1080, box = 480))
        assertEquals(2, ProxyPlanner.scaleToFit(100, 80, box = 50))
        assertEquals(1, ProxyPlanner.scaleToFit(320, 240, box = 500))
        assertEquals(1, ProxyPlanner.scaleToFit(10, 10, box = 20))
    }
}