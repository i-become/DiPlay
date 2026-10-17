package com.shilapi.xcertplay.network

import android.content.Context
import com.shilapi.xcertplay.orchestration.WirelessHotspotMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29])
class WirelessLinkSnapshotStoreTest {
    private val context get() = RuntimeEnvironment.getApplication()

    @Before fun setUp() {
        context.getSharedPreferences("diplay_wireless_link_snapshot", Context.MODE_PRIVATE)
            .edit().clear().commit()
    }

    @Test fun saveAndLoadRoundTrip() {
        val snapshot = WirelessLinkSnapshotStore.Snapshot(
            savedAtEpochMs = 1_700_000_000_000L,
            hotspotMode = WirelessHotspotMode.WIFI_P2P.name,
            backendLabel = WirelessHotspotBackend.WIFI_P2P.label,
            interfaceName = "p2p0",
            hostAddressLiteral = "192.168.49.1",
            alternateAddressLiteral = null,
            preferredFamily = HotspotAddressPolicy.FAMILY_IPV4,
        )
        assertTrue(WirelessLinkSnapshotStore.save(context, snapshot))
        assertEquals(snapshot, WirelessLinkSnapshotStore.load(context))
    }

    @Test fun seedCandidatesIgnoreMismatchedHotspotMode() {
        WirelessLinkSnapshotStore.save(
            context,
            WirelessLinkSnapshotStore.Snapshot(
                savedAtEpochMs = 1L,
                hotspotMode = WirelessHotspotMode.WIFI_P2P.name,
                backendLabel = WirelessHotspotBackend.WIFI_P2P.label,
                interfaceName = "p2p0",
                hostAddressLiteral = "192.168.49.1",
                alternateAddressLiteral = null,
                preferredFamily = HotspotAddressPolicy.FAMILY_IPV4,
            ),
        )
        val info = WirelessHotspotInfo(
            ssid = "DIRECT-test",
            passphrase = "secret",
            security = com.shilapi.xcertplay.transport.Iap2WirelessSecurity.WPA_WPA2,
            channel = 149,
            frequencyMHz = 5745,
            bssid = null,
            interfaceName = "p2p0",
            hostAddress = null,
            bandLabel = "5GHz",
            backend = WirelessHotspotBackend.WIFI_P2P,
        )
        assertTrue(
            WirelessLinkSnapshotStore.seedCandidates(context, info, WirelessHotspotMode.MANUAL).isEmpty(),
        )
    }
}
