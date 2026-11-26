package com.shilapi.xcertplay.network

import java.net.Inet6Address
import java.net.InetAddress

/** Pure readiness check shared by the P2P startup guard and unit tests. */
internal object P2pHotspotReadiness {
    fun failure(
        expected: WirelessHotspotInfo,
        groupOwner: Boolean?,
        groupName: String?,
        createdGroupName: String?,
        groupInterface: String?,
        interfaceUp: Boolean,
        interfaceAddresses: Collection<InetAddress>,
    ): String? {
        if (groupOwner != true) return "group_owner_lost"
        if (createdGroupName.isNullOrBlank() || groupName != createdGroupName || groupName != expected.ssid) {
            return "group_identity_changed"
        }
        if (expected.interfaceName.isNullOrBlank() || groupInterface != expected.interfaceName) {
            return "group_interface_changed"
        }
        if (!interfaceUp) return "interface_down"
        val expectedAddress = expected.hostAddress ?: return "published_address_missing"
        if (interfaceAddresses.none { sameAddress(expectedAddress, it) }) return "published_address_changed"
        return null
    }

    private fun sameAddress(expected: InetAddress, actual: InetAddress): Boolean =
        expected.address.contentEquals(actual.address) &&
            (expected !is Inet6Address || actual !is Inet6Address ||
                expected.scopeId == 0 || actual.scopeId == 0 || expected.scopeId == actual.scopeId)
}
