package com.shilapi.xcertplay.hud

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class Hs5ClusterDisplayTest {
    @Test fun acceptsCommonClusterGeometry() {
        assertTrue(Hs5ClusterDisplay.accepts("any", 1920, 720))
        assertTrue(Hs5ClusterDisplay.accepts("Cluster-Display", 800, 480))
        assertTrue(Hs5ClusterDisplay.accepts("fission_bg_virtual", 1280, 480))
        assertFalse(Hs5ClusterDisplay.accepts("any", 720, 1920))
    }

    @Test fun recognizesInstrumentClusterNames() {
        assertTrue(Hs5ClusterDisplay.isInstrumentCluster("Cluster-Display"))
        assertFalse(Hs5ClusterDisplay.isInstrumentCluster("ScreenCapture"))
    }
}
