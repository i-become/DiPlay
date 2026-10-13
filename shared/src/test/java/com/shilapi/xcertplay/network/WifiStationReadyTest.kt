package com.shilapi.xcertplay.network

import android.net.wifi.WifiManager
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29], manifest = Config.NONE)
class WifiStationReadyTest {
    private val context get() = RuntimeEnvironment.getApplication()

    @Test fun alreadyEnabledReturnsImmediately() {
        context.getSystemService(WifiManager::class.java).isWifiEnabled = true
        assertEquals(
            WifiStationReady.Outcome.READY,
            WifiStationReady.ensure(context, isCancelled = { false }, timeoutMillis = 500),
        )
    }
}
