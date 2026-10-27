package com.shilapi.xcertplay

import org.junit.Assert.*
import org.junit.Test

class ClusterNeedleMotionTest {
    @Test fun retargetingKeepsDisplayedPositionAndForwardVelocity() {
        val needle = ClusterNeedleMotion(75)
        needle.update(0f, 0, true)
        needle.update(60f, 100, true)
        val before = needle.valueAt(160)!!
        val velocity = (before - needle.valueAt(159)!!)
        needle.update(90f, 160, true)
        assertEquals(before, needle.valueAt(160)!!, .0001f)
        assertEquals(velocity, needle.valueAt(161)!! - before, .01f)
        var previous = before
        for (at in 161L..1000L step 8) {
            val value = needle.valueAt(at)!!
            assertTrue(value >= previous && value <= 90f)
            previous = value
        }
        assertEquals(90f, needle.valueAt(1000)!!, 0f)
        assertFalse(needle.moving(1000))
    }

    @Test fun reversalsBrakeWithoutOvershootingEitherTarget() {
        val needle = ClusterNeedleMotion()
        needle.update(0f, 0, true)
        needle.update(100f, 100, true)
        val before = needle.valueAt(200)!!
        needle.update(5f, 200, true)
        assertEquals(before, needle.valueAt(200)!!, 0f)
        var previous = before
        for (at in 201L..1000L step 8) {
            val value = needle.valueAt(at)!!
            assertTrue(value <= previous && value >= 5f)
            previous = value
        }
        assertEquals(5f, needle.valueAt(1000)!!, 0f)
    }

    @Test fun identicalMeasurementsDoNotRestartMotionAndReducedMotionSnaps() {
        val needle = ClusterNeedleMotion()
        val reference = ClusterNeedleMotion()
        for (motion in listOf(needle, reference)) {
            motion.update(0f, 0, true)
            motion.update(120f, 100, true)
        }
        needle.update(120f, 150, true)
        assertEquals(reference.valueAt(300)!!, needle.valueAt(300)!!, 0f)
        needle.update(50f, 350, false)
        assertEquals(50f, needle.valueAt(350)!!, 0f)
        assertFalse(needle.moving(350))
    }

    @Test fun missingSignalsDisappearAndRecoveryStartsAtTheNewMeasurement() {
        val needle = ClusterNeedleMotion()
        needle.update(70f, 0, true)
        needle.update(null, 50, true)
        assertNull(needle.valueAt(50))
        assertFalse(needle.moving(50))
        needle.update(60f, 100, true)
        assertEquals(60f, needle.valueAt(100)!!, 0f)
        needle.update(Float.NaN, 110, true)
        assertNull(needle.valueAt(110))
        needle.update(Float.POSITIVE_INFINITY, 120, true)
        assertNull(needle.valueAt(120))
    }

    @Test fun lowRateRpmSamplesKeepMovingWithoutHoldingForMostOfTheInterval() {
        val needle = ClusterNeedleMotion(125)
        needle.update(700f, 0, true)
        needle.update(1700f, 750, true)
        assertTrue(needle.moving(1300))
        val late = needle.valueAt(1400)!!
        assertTrue(late > needle.valueAt(1300)!! && late < 1700f)
        // Most of the response still arrives promptly, without waiting for the next sample.
        assertTrue(needle.valueAt(1000)!! > 1250f)
        needle.update(2700f, 1500, true)
        assertTrue(needle.valueAt(1750)!! > 2200f)
    }

    @Test fun timingIsIndependentOfFrameRateAndLongPausesSettle() {
        val fast = ClusterNeedleMotion()
        val slow = ClusterNeedleMotion()
        for (needle in listOf(fast, slow)) {
            needle.update(0f, 0, true); needle.update(100f, 100, true)
        }
        for (at in 100L..250L step 4) fast.valueAt(at)
        for (at in 100L..250L step 33) slow.valueAt(at)
        assertEquals(fast.valueAt(260)!!, slow.valueAt(260)!!, 0f)
        assertEquals(100f, fast.valueAt(60_000)!!, 0f)
        assertFalse(fast.moving(60_000))
    }
}
