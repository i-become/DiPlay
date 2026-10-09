package com.shilapi.xcertplay.hud

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HongqiClusterDisplayTest {
    @Test fun acceptsCommonClusterGeometry() {
        assertTrue(HongqiClusterDisplay.accepts("any", 1920, 720))
        assertTrue(HongqiClusterDisplay.accepts("Cluster-Display", 800, 480))
        assertTrue(HongqiClusterDisplay.accepts("fission_bg_virtual", 1280, 480))
        assertFalse(HongqiClusterDisplay.accepts("any", 720, 1920))
    }

    @Test fun recognizesInstrumentClusterNames() {
        assertTrue(HongqiClusterDisplay.isInstrumentCluster("Cluster-Display"))
        assertFalse(HongqiClusterDisplay.isInstrumentCluster("ScreenCapture"))
    }
}
