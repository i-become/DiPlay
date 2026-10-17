package com.shilapi.xcertplay.hud

import org.junit.Assert.assertFalse
import org.junit.Test

/** Documents [Hs5HeadUnit.detected] matching rules; value depends on the test JVM's Build.* tags. */
class Hs5HeadUnitDetectedTest {
    @Test fun detectedIsFalseOnGenericRobolectricBuild() {
        assertFalse(Hs5HeadUnit.detected)
    }
}
