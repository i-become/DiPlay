package com.shilapi.xcertplay

import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class OemClusterGeometryTest {
    @Test fun approvedReferenceHasFullCircularDialsAndSeparateTireBand() {
        assertEquals(334f * .9375f, OemClusterGeometry.DIAL_LEFT_X, .001f)
        assertEquals(1691f * .9375f, OemClusterGeometry.DIAL_RIGHT_X, .001f)
        assertEquals(373f * .9375f, OemClusterGeometry.DIAL_Y, .001f)
        assertEquals(277f * .9375f, OemClusterGeometry.DIAL_RADIUS, .001f)
        assertTrue(OemClusterGeometry.DIAL_Y + OemClusterGeometry.DIAL_RADIUS < 660f * .9375f)
    }

    @Test fun negotiatedMapMatchesCentralViewportWithoutStretchingAtEveryScale() {
        for ((w, h) in listOf(1920 to 720, 1280 to 480, 1920 to 480, 800 to 600)) {
            for (scale in listOf(25, 67, 83, 100, 125, 150)) {
                val config = OemClusterGeometry.streamConfig(w, h, scale)
                assertEquals(OemClusterGeometry.MAP_ASPECT, config.widthPixels.toFloat() / config.heightPixels, .04f)
                assertEquals(0, config.widthPixels % 8)
                assertEquals(0, config.heightPixels % 8)
                assertTrue(config.widthPixels > 0 && config.heightPixels > 0)
                val bounds = ClusterDashboardView.mapBounds(w, h, Float.NaN)
                assertEquals(OemClusterGeometry.MAP_ASPECT, bounds.width() / bounds.height(), .001f)
                assertEquals(true, config.safeAreaDrawOutside)
                // Even at the narrowest point between the dials, the marker's entire safe
                // area is outside the opaque rims and their transition shadows.
                val safeArea = requireNotNull(config.safeArea)
                val safeLeft = bounds.left + bounds.width() * safeArea.left / config.widthPixels
                val safeRight = bounds.right - bounds.width() * safeArea.right / config.widthPixels
                val panelScale = minOf(w / OemClusterGeometry.WIDTH, h / OemClusterGeometry.HEIGHT)
                val offsetX = (w - OemClusterGeometry.WIDTH * panelScale) / 2
                assertTrue(safeLeft >= offsetX + (334 + 277) * .9375f * panelScale - 1)
                assertTrue(safeRight <= offsetX + (1691 - 277) * .9375f * panelScale + 1)
            }
        }
    }

    @Test fun mapFillsTheReferenceApertureAboveTheTireBand() {
        for ((w, h) in listOf(1920 to 720, 1280 to 480)) {
            val map = ClusterDashboardView.mapBounds(w, h, OemClusterGeometry.MAP_ASPECT)
            assertEquals(h * 99f / 768f, map.top, .001f)
            assertEquals(h * 491f / 768f, map.height(), .001f)
            assertEquals(h * 590f / 768f, map.bottom, .001f)
            assertEquals(w * 334f / 2048f, map.left, .001f)
            assertEquals(w * 1691f / 2048f, map.right, .001f)
        }
    }

    @Test fun bothCustomStylesNegotiateMoreMapAreaWithoutChangingTheViewport() {
        for (style in listOf(ClusterStyle.BASIC, ClusterStyle.SPORT)) {
            val bounds = style.mapBounds(2048, 768)
            val config = style.streamConfig(2048, 768, 83, 50, 45)
            assertTrue("map density must reduce magnification for $style", config.heightPixels > bounds.height() * 1.2f)
            assertTrue(config.heightPixels < bounds.height() * 1.3f)
            assertEquals(bounds, style.mapBounds(2048, 768))
        }
        assertEquals(125, ClusterMapRenderPolicy.scalePercent(83))
        assertEquals(150, ClusterMapRenderPolicy.scalePercent(150))
    }

    @Test fun hs5MarkerSettingsChangeTheNegotiatedMapSafeAreaInBothDirections() {
        fun centre(x: Int, y: Int): Pair<Float, Float> {
            val config = OemClusterGeometry.streamConfig(1920, 720, 83, x, y)
            val safe = requireNotNull(config.safeArea)
            return (safe.left + config.widthPixels - safe.right) * 50f / config.widthPixels to
                (safe.top + config.heightPixels - safe.bottom) * 50f / config.heightPixels
        }
        for ((x, y) in listOf(35 to 25, 50 to 45, 65 to 75)) {
            val actual = centre(x, y)
            assertEquals(x.toFloat(), actual.first, .2f)
            assertEquals(y.toFloat(), actual.second, .2f)
        }
    }

    @Test fun browsingAndTrailingEdgeNavigationAnchorsStayNearTheSelectedMarkerPosition() {
        for (scale in listOf(25, 67, 100, 150)) for (x in listOf(35, 50, 65)) {
            val config = OemClusterGeometry.streamConfig(1920, 720, scale, x, 45)
            val safe = requireNotNull(config.safeArea)
            val leading = safe.left * 100f / config.widthPixels
            val trailing = (config.widthPixels - safe.right) * 100f / config.widthPixels
            assertEquals(x - 2f, leading, .3f)
            assertEquals(x + 2f, trailing, .3f)
            assertTrue(config.safeAreaDrawOutside == true)
        }
    }

    @Test fun needleAnglesMatchActualHs5RangesRatherThanReferenceExamples() {
        assertEquals(140f, OemClusterGeometry.needleDegrees(0f, 7000f), 0f)
        assertEquals(270f, OemClusterGeometry.needleDegrees(3500f, 7000f), 0f)
        assertEquals(400f, OemClusterGeometry.needleDegrees(7000f, 7000f), 0f)
        assertEquals(140f, OemClusterGeometry.speedDegrees(0f), 0f)
        assertEquals(150.7f, OemClusterGeometry.speedDegrees(10f), .001f)
        assertEquals(354f, OemClusterGeometry.speedDegrees(200f), 0f)
        assertEquals(377f, OemClusterGeometry.speedDegrees(230f), 0f)
        assertEquals(400f, OemClusterGeometry.speedDegrees(260f), 0f)
    }

    @Test fun unavailableAndInvalidVehicleValuesNeverBecomeZeroOrPark() {
        val unknown = OemClusterVehicleState()
        assertNull(unknown.speedKph)
        assertNull(unknown.rpm)
        assertNull(unknown.gear)
        assertNull(unknown.assistance)
        val invalid = unknown.copy(speedKph = Float.NaN, rpm = -1f, fuelFraction = 2f,
            coolantCelsius = Float.POSITIVE_INFINITY, outsideCelsius = 99f, gear = "UNKNOWN").validated()
        assertEquals(unknown, invalid)
        val parked = unknown.copy(speedKph = 0f, rpm = 0f, gear = "P", fuelFraction = 0f)
        assertEquals(parked, parked.validated())
    }

    @Test fun disconnectClearsPhoneDataButRetainsIndependentVehicleReadings() {
        val car = OemClusterVehicleState(positionLights = true, speedKph = 40f)
        val disconnected = ClusterDashboardState(vehicle = car,
            music = ClusterDashboardMusic(com.shilapi.xcertplay.media.CarPlayNowPlaying(title = "old"))).current()
        assertEquals(car, disconnected.vehicle)
        assertNull(disconnected.music.metadata.title)
        assertFalse(disconnected.guidance.connected)
    }
}
