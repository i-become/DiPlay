package com.shilapi.xcertplay.network

import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import android.net.wifi.WifiManager
import com.shilapi.xcertplay.orchestration.WirelessHotspotMode
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowSystemClock
import java.time.Duration

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], manifest = Config.NONE)
class WirelessStartupPolicyTest {
    private val context get() = RuntimeEnvironment.getApplication()

    @Test fun defaultHeadUnitUsesStockWirelessTimeouts() {
        assertEquals(WirelessStartupPolicy.HOTSPOT_READY_MILLIS, WirelessStartupPolicy.hotspotReadyMillis(context))
        assertEquals(WirelessStartupPolicy.INTERFACE_POLL_MILLIS, WirelessStartupPolicy.interfacePollMillis(context))
    }

    @Test fun hongqiPackagesUseShorterWirelessTimeouts() {
        shadowOf(context.packageManager).installPackage(PackageInfo().apply {
            packageName = "ltd.qisi.navi"
            applicationInfo = ApplicationInfo().apply {
                packageName = "ltd.qisi.navi"
                flags = ApplicationInfo.FLAG_SYSTEM
            }
        })
        assertEquals(6_000L, WirelessStartupPolicy.hotspotReadyMillis(context))
        assertEquals(100L, WirelessStartupPolicy.interfacePollMillis(context))
        assertEquals(2, WirelessStartupPolicy.stableSamples(context))
        assertEquals(2_500L, WirelessStartupPolicy.wirelessLinkPrepareMaxMillis(context))
    }

    @Test fun hongqiOptimisticWifiDirectSkipsPrepareBudget() {
        shadowOf(context.packageManager).installPackage(PackageInfo().apply {
            packageName = "ltd.qisi.navi"
            applicationInfo = ApplicationInfo().apply {
                packageName = "ltd.qisi.navi"
                flags = ApplicationInfo.FLAG_SYSTEM
            }
        })
        assertTrue(WirelessStartupPolicy.optimisticStationWifi(context, WirelessHotspotMode.WIFI_P2P))
        assertEquals(0L, WirelessStartupPolicy.wirelessLinkPrepareMaxMillis(context, WirelessHotspotMode.WIFI_P2P))
    }

    @Test fun hongqiFastBringUpUsesTighterBudgetWhenWifiIsOn() {
        ShadowSystemClock.advanceBy(Duration.ofMinutes(4))
        shadowOf(context.packageManager).installPackage(PackageInfo().apply {
            packageName = "ltd.qisi.navi"
            applicationInfo = ApplicationInfo().apply {
                packageName = "ltd.qisi.navi"
                flags = ApplicationInfo.FLAG_SYSTEM
            }
        })
        context.getSystemService(WifiManager::class.java).isWifiEnabled = true
        assertTrue(WirelessStartupPolicy.hongqiFastBringUp(context, WirelessHotspotMode.EXISTING_WIFI))
        assertEquals(2_500L, WirelessStartupPolicy.hotspotReadyMillis(context, WirelessHotspotMode.EXISTING_WIFI))
        assertEquals(1, WirelessStartupPolicy.stableSamples(context, WirelessHotspotMode.EXISTING_WIFI))
        assertEquals(0L, WirelessStartupPolicy.wirelessLinkPrepareMaxMillis(context, WirelessHotspotMode.EXISTING_WIFI))
    }
}
