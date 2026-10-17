package com.shilapi.xcertplay

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CarPlayConnectionTimingTest {
    @Test
    fun stepsLogSinceStartAndSincePrevious() {
        var now = 0L
        val timing = CarPlayConnectionTiming(nowNanos = { now })
        timing.begin()
        now = 1_000_000_000L
        val first = timing.onStep(1, "mfi-or-usb-start").single()
        assertTrue(first.contains("sinceStartMs=1000"))
        assertTrue(first.contains("sincePreviousMs=1000"))
        now = 2_500_000_000L
        val second = timing.onStep(2, "mfi-wait").single()
        assertTrue(second.contains("sinceStartMs=2500"))
        assertTrue(second.contains("sincePreviousMs=1500"))
    }

    @Test
    fun finishSuccessSummarizesSlowestStep() {
        var now = 0L
        val timing = CarPlayConnectionTiming(nowNanos = { now })
        timing.begin()
        now = 1_000_000_000L
        timing.onStep(1, "a")
        now = 12_000_000_000L
        timing.onStep(22, "start-session")
        val lines = timing.finishSuccess()
        assertTrue(lines.any { it.startsWith("connection timing success totalMs=12000") })
        assertTrue(lines.any { it.contains("slowestStep step=22") })
        assertEquals(2, timing.recordsSnapshot().size)
    }
}
