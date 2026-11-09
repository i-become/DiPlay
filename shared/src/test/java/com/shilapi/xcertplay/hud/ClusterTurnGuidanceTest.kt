package com.shilapi.xcertplay.hud

import org.junit.Assert.assertEquals
import org.junit.Test

class ClusterTurnGuidanceTest {
    @Test
    fun straightAheadKeepsTheCard() {
        val guidance = ClusterTurnGuidance.from(Hs5GuidanceFrame.from(Hs5AppleManeuver(400, 3, 0, "环城路")))
        assertEquals(9, guidance.icon)
        assertEquals(400, guidance.distanceMeters)
        assertEquals("环城路", guidance.road)
    }

    @Test
    fun unknownManeuverMustNotInventAStraightInstruction() {
        val guidance = ClusterTurnGuidance.from(Hs5GuidanceFrame.from(Hs5AppleManeuver(80, 0, 0)))
        assertEquals(0, guidance.icon)
        assertEquals(80, guidance.distanceMeters)
    }

    @Test
    fun leftTurnIsUnchanged() {
        val guidance = ClusterTurnGuidance.from(Hs5GuidanceFrame.from(Hs5AppleManeuver(53, 1, 0)))
        assertEquals(2, guidance.icon)
    }
}
