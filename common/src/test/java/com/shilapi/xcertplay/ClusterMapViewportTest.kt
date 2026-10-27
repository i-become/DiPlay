package com.shilapi.xcertplay

import org.junit.Assert.*
import org.junit.Test

class ClusterMapViewportTest {
    @Test fun browsingAndNeutralCalibrationPreserveTheFullSource() {
        assertEquals(ClusterMapViewport(), ClusterMapViewport.navigation(false, 60, 45))
        assertEquals(ClusterMapViewport(), ClusterMapViewport.navigation(true, 0, 45))
    }

    @Test fun aRightBiasedNavigationMarkerCanBeCenteredWithoutStretching() {
        val crop = ClusterMapViewport.navigation(true, 50, 45)
        assertEquals(.5f, .75f * crop.scale + crop.x, .00001f)
        assertEquals(.45f, .45f * crop.scale + crop.y, .00001f)
        assertEquals(2f, crop.scale, .00001f)
        val opposite = ClusterMapViewport.navigation(true, -50, 45)
        assertEquals(.5f, .25f * opposite.scale + opposite.x, .00001f)
    }

    @Test fun everyAllowedCropStillCoversTheEntireViewport() {
        for (value in -100..100) for (anchor in listOf(-100, 5, 45, 95, 200)) {
            val crop = ClusterMapViewport.navigation(true, value, anchor)
            assertTrue(crop.scale.isFinite() && crop.scale in 1f..2.501f)
            assertTrue(crop.x <= 0 && crop.y <= 0)
            assertTrue(crop.x + crop.scale >= .9999f)
            assertTrue(crop.y + crop.scale >= .9999f)
        }
    }
}

