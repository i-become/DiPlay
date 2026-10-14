package com.shilapi.xcertplay.hud

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class QisiVehicleBinderTest {
    @Test fun hudDistanceUnitsEncodeForInstrument() {
        assertArrayEquals(intArrayOf(0, 0), QisiVehicleBinder.hudDistanceUnits(10))
        assertArrayEquals(intArrayOf(1, 500), QisiVehicleBinder.hudDistanceUnits(500))
        assertArrayEquals(intArrayOf(2, 35), QisiVehicleBinder.hudDistanceUnits(3_500))
        assertArrayEquals(intArrayOf(3, 12), QisiVehicleBinder.hudDistanceUnits(12_500))
    }

    @Test fun segmentDistanceUsesChineseUnits() {
        assertEquals("现在", QisiDistanceFormat.segmentDistance(10))
        assertEquals("800米", QisiDistanceFormat.segmentDistance(800))
        assertEquals("3.5公里", QisiDistanceFormat.segmentDistance(3_500))
    }
}
