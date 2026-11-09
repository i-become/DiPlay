package com.shilapi.xcertplay.hud

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class Hs5GuidanceFrameTest {
    @Test
    fun `maps basic turns to amap icons`() {
        assertEquals(Hs5GuidanceFrame(2, 0, 53), frame(type = 1, distance = 53))
        assertEquals(Hs5GuidanceFrame(3, 0, 53), frame(type = 2, distance = 53))
        assertEquals(Hs5GuidanceFrame(9, 0, 53), frame(type = 3, distance = 53))
        assertEquals(Hs5GuidanceFrame(4, 0, 53), frame(type = 49, distance = 53))
        assertEquals(Hs5GuidanceFrame(5, 0, 53), frame(type = 14, distance = 53))
    }

    @Test
    fun `numbered roundabout exit carries exit number`() {
        assertEquals(Hs5GuidanceFrame(11, 2, 120), frame(type = 29, distance = 120))
        assertEquals(Hs5GuidanceFrame(17, 2, 120), frame(type = 29, distance = 120, drivingSide = 1))
    }

    @Test
    fun `u-turn follows driving side and arrival is destination`() {
        assertEquals(8, frame(type = 4).icon)
        assertEquals(19, frame(type = 4, drivingSide = 1).icon)
        assertEquals(15, frame(type = 12).icon)
    }

    @Test
    fun `negative distance is clamped`() {
        assertEquals(0, frame(type = 1, distance = -5).distanceMeters)
    }

    @Test fun `unknown maneuver is not advertised as straight`() {
        for (type in listOf(0, 15, 16, 17, 54, 255)) assertEquals(0, frame(type).icon)
        for (type in listOf(3, 5, 8, 9, 11, 51)) assertEquals(9, frame(type).icon)
    }

    @Test fun `lane extras preserve positions statuses and all directions`() {
        val extras = Hs5GuidanceFrame(
                icon = 2,
                roundaboutExit = 0,
                distanceMeters = 40,
                lanes = listOf(
                    Hs5LaneInfo(0, 2, listOf(-90, 0), highlightedAngle = 0),
                    Hs5LaneInfo(1, 1, listOf(90), highlightedAngle = null),
                ),
            ).laneGuidanceExtras()

        assertArrayEquals(intArrayOf(0, 1), extras.positions)
        assertArrayEquals(intArrayOf(2, 1), extras.statuses)
        assertArrayEquals(intArrayOf(0, 1000), extras.highlightedDirections)
        assertArrayEquals(intArrayOf(0, 2, 3), extras.directionOffsets)
        assertArrayEquals(intArrayOf(-90, 0, 90), extras.directions)
    }

    private fun frame(type: Int, distance: Int = 0, drivingSide: Int = 0) =
        Hs5GuidanceFrame.from(Hs5AppleManeuver(distance, type, drivingSide))
}
