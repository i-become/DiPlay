package com.shilapi.xcertplay.network

import android.net.wifi.WifiManager
import com.shilapi.xcertplay.orchestration.WirelessHotspotMode
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29], manifest = Config.NONE)
class WirelessLinkPrepareTest {
    private val context get() = RuntimeEnvironment.getApplication()

    @Test fun wifiDirectPrepareRunsWhenWifiIsAlreadyOn() {
        context.getSystemService(WifiManager::class.java).isWifiEnabled = true
        WirelessLinkPrepare.prepare(
            context,
            WirelessHotspotMode.WIFI_P2P,
            isCancelled = { false },
            timeoutMillis = 500,
            log = {},
        )
    }
}
