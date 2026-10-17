package com.shilapi.xcertplay

import com.shilapi.xcertplay.orchestration.WirelessHotspotMode
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class Hs5BootLaunchPolicyTest {
    private fun decide(
        hongqi: Boolean = true,
        autoConnect: Boolean = true,
        phoneAddress: String? = "DC:10:57:49:89:07",
        wireless: Boolean = true,
        hotspotMode: WirelessHotspotMode = WirelessHotspotMode.WIFI_P2P,
        notificationsAllowed: Boolean = true,
        sessionRunning: Boolean = false,
        setupReady: Boolean = true,
    ) = Hs5BootLaunchPolicy.opensCarPlayDirectly(
        hongqi, autoConnect, phoneAddress, wireless, hotspotMode, notificationsAllowed, sessionRunning, setupReady,
    )

    @Test fun hongqiWithSavedPhoneOpensCarPlayDirectly() {
        assertTrue(decide())
        assertTrue(decide(wireless = false, hotspotMode = WirelessHotspotMode.MANUAL))
    }

    @Test fun anythingThatNeedsTheDriverKeepsTheHomePage() {
        assertFalse(decide(hongqi = false))
        assertFalse(decide(autoConnect = false))
        assertFalse(decide(phoneAddress = null))
        assertFalse(decide(phoneAddress = " "))
        assertFalse(decide(hotspotMode = WirelessHotspotMode.MANUAL))
        assertFalse(decide(notificationsAllowed = false))
        assertFalse(decide(sessionRunning = true))
        assertFalse(decide(setupReady = false))
    }
}
