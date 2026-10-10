package com.shilapi.xcertplay.hud

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HongqiAmapAdapterTest {
    @Test fun prefersStockAmapAutoPackage() {
        val packages = mutableSetOf<String>()
        assertEquals(HongqiAmapAdapter.AMAP_AUTO, HongqiAmapAdapter.find {
            packages.add(it); it == HongqiAmapAdapter.AMAP_AUTO.packageName
        })
        assertTrue(packages.contains(HongqiAmapAdapter.AMAP_AUTO.packageName))
    }

    @Test fun returnsNullWhenNothingInstalled() {
        assertNull(HongqiAmapAdapter.find { false })
    }
}
