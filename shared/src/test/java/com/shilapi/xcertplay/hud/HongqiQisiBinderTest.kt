package com.shilapi.xcertplay.hud

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class HongqiQisiBinderTest {
    @Test fun hudDistanceUnitsMatchFawPlay() {
        assertArrayEquals(intArrayOf(0, 0), HongqiQisiBinder.hudDistanceUnits(10))
        assertArrayEquals(intArrayOf(1, 500), HongqiQisiBinder.hudDistanceUnits(500))
        assertArrayEquals(intArrayOf(2, 35), HongqiQisiBinder.hudDistanceUnits(3_500))
        assertArrayEquals(intArrayOf(3, 12), HongqiQisiBinder.hudDistanceUnits(12_500))
    }

    @Test fun segmentDistanceUsesChineseUnits() {
        assertEquals("现在", HongqiQisiLabels.segmentDistance(10))
        assertEquals("800米", HongqiQisiLabels.segmentDistance(800))
        assertEquals("3.5公里", HongqiQisiLabels.segmentDistance(3_500))
    }
}
