package com.shilapi.xcertplay.network

import com.shilapi.xcertplay.transport.Iap2WirelessSecurity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.net.InetAddress

class P2pHotspotReadinessTest {
    private val expected = WirelessHotspotInfo(
        ssid = "DIRECT-test",
        passphrase = "secret",
        security = Iap2WirelessSecurity.WPA_WPA2,
        channel = 0,
        frequencyMHz = null,
        bssid = null,
        interfaceName = "p2p0",
        hostAddress = InetAddress.getByName("192.168.49.1"),
        bandLabel = "Auto",
        backend = WirelessHotspotBackend.WIFI_P2P,
    )
    private val address = InetAddress.getByName("192.168.49.1")

    private fun failure(
        owner: Boolean? = true,
        group: String? = expected.ssid,
        created: String? = expected.ssid,
        iface: String? = "p2p0",
        up: Boolean = true,
        addresses: Collection<InetAddress> = listOf(address),
    ) = P2pHotspotReadiness.failure(expected, owner, group, created, iface, up, addresses)

    @Test fun acceptsCurrentOwnerGroupAndPublishedAddress() {
        assertNull(failure())
    }

    @Test fun rejectsChangedOwnerGroupInterfaceOrPublishedAddress() {
        assertEquals("group_owner_lost", failure(owner = false))
        assertEquals("group_identity_changed", failure(group = "DIRECT-other"))
        assertEquals("group_interface_changed", failure(iface = "p2p1"))
        assertEquals("interface_down", failure(up = false))
        assertEquals("published_address_changed", failure(addresses = listOf(InetAddress.getByName("192.168.49.2"))))
    }
}
