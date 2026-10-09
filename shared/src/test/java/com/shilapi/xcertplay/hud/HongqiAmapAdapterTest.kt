package com.shilapi.xcertplay.hud

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HongqiAmapAdapterTest {
    @Test fun prefersStockAmapAutoPackage() {
        val packages = mutableSetOf<String>()
        assertEquals(HongqiAmapAdapter.AMAP_AUTO, HongqiAmapAdapter.find {
            packages.add(it); it == HongqiAmapAdapter.AMAP_AUTO.packageName
        })
        assertEquals(setOf(HongqiAmapAdapter.AMAP_AUTO.packageName), packages)
    }

    @Test fun returnsNullWhenNothingInstalled() {
        assertNull(HongqiAmapAdapter.find { false })
    }
}
