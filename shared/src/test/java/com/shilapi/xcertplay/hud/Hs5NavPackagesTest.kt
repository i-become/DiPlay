package com.shilapi.xcertplay.hud

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class Hs5NavPackagesTest {
    @Test fun prefersStockAmapAutoPackage() {
        val packages = mutableSetOf<String>()
        assertEquals(Hs5NavPackages.AMAP_AUTO, Hs5NavPackages.find {
            packages.add(it); it == Hs5NavPackages.AMAP_AUTO.packageName
        })
        assertTrue(packages.contains(Hs5NavPackages.AMAP_AUTO.packageName))
    }

    @Test fun returnsNullWhenNothingInstalled() {
        assertNull(Hs5NavPackages.find { false })
    }
}
