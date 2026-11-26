package com.shilapi.xcertplay.hud

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class QisiVehicleBinderTest {
    @Test fun hudDistanceUnitsEncodeForInstrument() {
        assertArrayEquals(intArrayOf(0, 0), QisiVehicleBinder.hudDistanceUnits(10))
        assertArrayEquals(intArrayOf(1, 500), QisiVehicleBinder.hudDistanceUnits(500))
        assertArrayEquals(intArrayOf(1, 999), QisiVehicleBinder.hudDistanceUnits(999))
        assertArrayEquals(intArrayOf(2, 10), QisiVehicleBinder.hudDistanceUnits(1_000))
        assertArrayEquals(intArrayOf(2, 10), QisiVehicleBinder.hudDistanceUnits(1_099))
        assertArrayEquals(intArrayOf(2, 35), QisiVehicleBinder.hudDistanceUnits(3_500))
        assertArrayEquals(intArrayOf(3, 12), QisiVehicleBinder.hudDistanceUnits(12_500))
    }

    @Test fun hudNextCrossDistanceUsesTheSamePrecisionAsTheDisplayedValue() {
        assertEquals(999, QisiVehicleBinder.hudNextCrossDistanceMeters(999))
        assertEquals(1_000, QisiVehicleBinder.hudNextCrossDistanceMeters(1_000))
        assertEquals(1_000, QisiVehicleBinder.hudNextCrossDistanceMeters(1_099))
        assertEquals(1_100, QisiVehicleBinder.hudNextCrossDistanceMeters(1_100))
        assertEquals(9_900, QisiVehicleBinder.hudNextCrossDistanceMeters(9_999))
        assertEquals(10_000, QisiVehicleBinder.hudNextCrossDistanceMeters(10_000))
        assertEquals(12_000, QisiVehicleBinder.hudNextCrossDistanceMeters(12_999))
    }

    @Test fun segmentDistanceUsesChineseUnits() {
        assertEquals("现在", QisiDistanceFormat.segmentDistance(10))
        assertEquals("800米", QisiDistanceFormat.segmentDistance(800))
        assertEquals("3.5公里", QisiDistanceFormat.segmentDistance(3_500))
    }
}
