package com.shilapi.xcertplay.network

import android.net.wifi.WifiManager
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29], manifest = Config.NONE)
class WifiDirectRadioReadyTest {
    private val context get() = RuntimeEnvironment.getApplication()

    @Test fun bootstrapReadyWhenStationWifiIsOn() {
        context.getSystemService(WifiManager::class.java).isWifiEnabled = true
        assertEquals(WifiDirectRadioReady.Bootstrap.READY, WifiDirectRadioReady.bootstrap(context))
    }

    @Test fun bootstrapEnablingWhenRadioIsStarting() {
        val wifi = context.getSystemService(WifiManager::class.java)
        wifi.isWifiEnabled = false
        shadowOf(wifi).setWifiState(WifiManager.WIFI_STATE_ENABLING)
        assertEquals(WifiDirectRadioReady.Bootstrap.ENABLING, WifiDirectRadioReady.bootstrap(context))
    }
}
