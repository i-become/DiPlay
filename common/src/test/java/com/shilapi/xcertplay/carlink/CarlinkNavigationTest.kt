package com.shilapi.xcertplay.carlink

import org.junit.Assert.*
import org.junit.Test

class CarlinkNavigationTest {
    @Test fun phoneUnitsAreConvertedWithoutLosingChineseManeuversOrRouteSummaries() {
        val icon = byteArrayOf(1, 2, 3)
        val next = CarlinkNavigation.guidance(true, "1.2", "公里", "前方右转", "人民路",
            "18:20 到达", "剩余 12.8 公里", icon)!!
        assertEquals(1200, next.distanceMeters)
        assertEquals(5, next.icon)
        assertEquals("前方右转 · 人民路", next.road)
        assertEquals("18:20 到达   剩余 12.8 公里", next.routeSummary)
        assertArrayEquals(icon, next.directionIcon)
        assertEquals(1609, CarlinkNavigation.distanceMeters("1", "mi"))
        assertEquals(305, CarlinkNavigation.distanceMeters("1000", "ft"))
        assertEquals(2500, CarlinkNavigation.distanceMeters("2,5 km", null))
        assertEquals(0, CarlinkNavigation.distanceMeters(null, null))
    }

    @Test fun endingNavigationClearsItAndUnknownOperationsDoNotInventTurns() {
        assertNull(CarlinkNavigation.guidance(false, "100", "m", "left", "Road", null, null, null))
        assertEquals(0, CarlinkNavigation.icon("未知操作"))
        assertEquals(8, CarlinkNavigation.icon("前方掉头"))
        assertEquals(15, CarlinkNavigation.icon("到达目的地"))
        assertEquals(3, CarlinkNavigation.icon("turn right"))
    }
}
