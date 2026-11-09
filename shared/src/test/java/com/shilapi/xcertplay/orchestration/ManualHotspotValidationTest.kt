package com.shilapi.xcertplay.orchestration

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ManualHotspotValidationTest {
    @Test
    fun `accepts a WPA2 car hotspot and an open one`() {
        assertNull(ManualHotspotValidation.validate("HS5_1234", "12345678"))
        assertNull(ManualHotspotValidation.validate("HS5_1234", ""))
        assertEquals(ManualHotspotSecurity.WPA2, ManualHotspotValidation.securityFor("12345678"))
        assertEquals(ManualHotspotSecurity.OPEN, ManualHotspotValidation.securityFor(""))
    }

    @Test
    fun `rejects what the iPhone cannot join`() {
        assertNotNull(ManualHotspotValidation.validate(" ", "12345678"))
        assertNotNull(ManualHotspotValidation.validate("HS5", "short"))
        assertNotNull(ManualHotspotValidation.validate("x".repeat(33), "12345678"))
        assertNotNull(ManualHotspotValidation.validate("HS5", "a".repeat(64)))
    }

    @Test
    fun chineseHotspotNamesAreFlaggedForTheIphoneWarning() {
        // #303: renaming a Chinese car hotspot to English let CarPlay start.
        assertTrue(ManualHotspotValidation.nameMayNotReachIphone("红旗热点"))
        assertTrue(ManualHotspotValidation.nameMayNotReachIphone("HS5-汉"))
        assertFalse(ManualHotspotValidation.nameMayNotReachIphone("HS5_Hongqi-5G 01"))
    }
}
