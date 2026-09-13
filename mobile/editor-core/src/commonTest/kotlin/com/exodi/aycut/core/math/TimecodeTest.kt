package com.exodi.aycut.core.math

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class TimecodeTest {

    private val p30 = FrameRate.P30
    private val ntsc30 = FrameRate.NTSC_30

    @Test
    fun `non-drop p30 hour is exactly 108000 frames`() {
        assertEquals(108_000L, Timecode(1, 0, 0, 0, p30, dropFrame = false).totalNominalFrames)
        assertEquals(
            3_599_964_000L,
            Timecode(1, 0, 0, 0, p30, dropFrame = false).toMicros(),
        )
        assertEquals("01:00:00:00", Timecode.fromMicros(3_599_964_000L, p30).toDisplayString())
    }

    @Test
    fun `non-drop labels round trip over the grid`() {
        var micros = 0L
        while (micros <= 2_000_000L) {
            val label = Timecode.fromMicros(micros, ntsc30)
            assertEquals(label, Timecode.fromMicros(label.toMicros(), ntsc30))
            micros += 12_345L
        }
    }

    @Test
    fun `non-drop 2997 labels real frames at the nominal grid`() {
        val label = Timecode.fromMicros(3_600_032_364L, ntsc30, dropFrame = false)
        assertEquals("00:59:56:12", label.toDisplayString())
        assertEquals(3_600_032_364L, label.toMicros())
    }

    @Test
    fun `drop frame skips two frames per minute except every ten`() {
        val beforeMinute =
            Timecode(0, 0, 59, 29, ntsc30, dropFrame = true).toMicros()
        val minute =
            Timecode(0, 1, 0, 0, ntsc30, dropFrame = true).toMicros()
        assertEquals(60_027_233L, beforeMinute)
        assertEquals(60_127_334L, minute)
        assertEquals(3L, (minute - beforeMinute) / ntsc30.frameDurationMicros)
    }

    @Test
    fun `drop frame hour label is the canonical 107892 real frames`() {
        assertEquals(
            3_600_032_364L,
            Timecode(1, 0, 0, 0, ntsc30, dropFrame = true).toMicros(),
        )
        assertEquals(
            "01:00:00;00",
            Timecode.fromMicros(3_600_032_364L, ntsc30, dropFrame = true).toDisplayString(),
        )
    }

    @Test
    fun `drop frame every tenth minute does not skip`() {
        // Ten real minutes is 600s x 30000/1001 = 17982 real frames. If minute 10
        // also dropped two labels, the label would only reach real frame 17980.
        val ten = Timecode(0, 10, 0, 0, ntsc30, dropFrame = true)
        assertEquals(600_005_394L, ten.toMicros())
        assertEquals(17_982L, ten.toMicros() / ntsc30.frameDurationMicros)
    }

    @Test
    fun `drop vs non-drop agree on real frame counts at anchors`() {
        assertEquals(18_000L, Timecode(0, 10, 0, 0, ntsc30, dropFrame = false).toMicros() / ntsc30.frameDurationMicros)
        assertEquals(108_000L, Timecode(1, 0, 0, 0, ntsc30, dropFrame = false).toMicros() / ntsc30.frameDurationMicros)
        assertEquals(107_892L, Timecode(1, 0, 0, 0, ntsc30, dropFrame = true).toMicros() / ntsc30.frameDurationMicros)
    }

    @Test
    fun `drop frame labels round trip`() {
        for (label in listOf(
            Timecode(0, 0, 0, 0, ntsc30, dropFrame = true),
            Timecode(0, 0, 59, 29, ntsc30, dropFrame = true),
            Timecode(0, 1, 0, 0, ntsc30, dropFrame = true),
            Timecode(0, 9, 59, 29, ntsc30, dropFrame = true),
            Timecode(0, 10, 0, 0, ntsc30, dropFrame = true),
            Timecode(1, 0, 0, 0, ntsc30, dropFrame = true),
        )) {
            assertEquals(label, Timecode.fromMicros(label.toMicros(), ntsc30, dropFrame = true))
        }
    }

    @Test
    fun `display separates drop from non-drop`() {
        assertEquals(
            "01:02:03:04",
            Timecode(1, 2, 3, 4, p30, dropFrame = false).toDisplayString(),
        )
        assertEquals(
            "01:02:03;04",
            Timecode(1, 2, 3, 4, ntsc30, dropFrame = true).toDisplayString(),
        )
    }

    @Test
    fun `drop frame is rejected outside nominal 30 or 60`() {
        assertFailsWith<IllegalArgumentException> {
            Timecode(0, 0, 0, 0, FrameRate.P25, dropFrame = true)
        }
        assertFailsWith<IllegalArgumentException> {
            Timecode.fromMicros(0L, FrameRate.P25, dropFrame = true)
        }
    }
}