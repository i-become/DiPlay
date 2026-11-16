package com.shilapi.xcertplay.glance

import org.junit.After
import org.junit.Assert.*
import org.junit.Test

class CarlinkGlanceTest {
    @After fun cleanup() {
        CarPlayGlance.clearExternal()
        CarPlayGlance.setConnected(false)
    }

    @Test fun dashboardReadsRetainCarlinkNavigationAndMusicUntilDisconnect() {
        val icon = byteArrayOf(1, 2, 3)
        CarPlayGlance.onExternalNavigation(true, 350, "Main Street", directionIcon = icon,
            routeSummary = "18:20 到达   剩余12公里")
        CarPlayGlance.onExternalMusic("Song", true)
        repeat(3) {
            val snapshot = CarPlayGlance.snapshot()
            assertTrue(snapshot.connected)
            assertTrue(snapshot.navigating)
            assertEquals(350, snapshot.distanceMeters)
            assertEquals("Main Street", snapshot.road)
            assertEquals("18:20 到达   剩余12公里", snapshot.routeSummary)
            assertArrayEquals(icon, snapshot.directionIcon)
            assertEquals("Song", snapshot.song)
            assertTrue(snapshot.playing)
        }
        CarPlayGlance.clearExternal()
        assertEquals(CarPlayGlance.Snapshot(), CarPlayGlance.snapshot())
    }

    @Test fun stoppingAnUnusedCarlinkBridgeDoesNotClearCarplay() {
        CarPlayGlance.setConnected(true)
        val before = CarPlayGlance.snapshot()
        CarPlayGlance.clearExternal()
        assertEquals(before, CarPlayGlance.snapshot())
        assertTrue(CarPlayGlance.snapshot().connected)
    }
}
