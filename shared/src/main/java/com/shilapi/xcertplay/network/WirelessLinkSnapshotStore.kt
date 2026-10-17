package com.shilapi.xcertplay.network

import android.content.Context
import com.shilapi.xcertplay.orchestration.WirelessHotspotMode
import java.net.Inet6Address
import java.net.InetAddress
import java.net.NetworkInterface

/**
 * Remembers the last successful wireless CarPlay link layout on this head unit.
 * Credentials, phone names and pairing secrets are never stored here.
 */
internal object WirelessLinkSnapshotStore {
    private const val PREFS = "diplay_wireless_link_snapshot"
    private const val KEY_SAVED_AT = "saved_at_epoch_ms"
    private const val KEY_HOTSPOT_MODE = "hotspot_mode"
    private const val KEY_BACKEND = "backend"
    private const val KEY_INTERFACE = "interface_name"
    private const val KEY_HOST = "host_address"
    private const val KEY_ALTERNATE = "alternate_address"
    private const val KEY_FAMILY = "preferred_family"

    data class Snapshot(
        val savedAtEpochMs: Long,
        val hotspotMode: String,
        val backendLabel: String,
        val interfaceName: String?,
        val hostAddressLiteral: String,
        val alternateAddressLiteral: String?,
        val preferredFamily: String?,
    )

    fun save(context: Context, snapshot: Snapshot): Boolean =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putLong(KEY_SAVED_AT, snapshot.savedAtEpochMs)
            .putString(KEY_HOTSPOT_MODE, snapshot.hotspotMode)
            .putString(KEY_BACKEND, snapshot.backendLabel)
            .putString(KEY_INTERFACE, snapshot.interfaceName)
            .putString(KEY_HOST, snapshot.hostAddressLiteral)
            .putString(KEY_ALTERNATE, snapshot.alternateAddressLiteral)
            .putString(KEY_FAMILY, snapshot.preferredFamily)
            .commit()

    fun load(context: Context): Snapshot? {
        val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val host = prefs.getString(KEY_HOST, null)?.takeIf { it.isNotBlank() } ?: return null
        val mode = prefs.getString(KEY_HOTSPOT_MODE, null)?.takeIf { it.isNotBlank() } ?: return null
        val backend = prefs.getString(KEY_BACKEND, null)?.takeIf { it.isNotBlank() } ?: return null
        return Snapshot(
            savedAtEpochMs = prefs.getLong(KEY_SAVED_AT, 0L),
            hotspotMode = mode,
            backendLabel = backend,
            interfaceName = prefs.getString(KEY_INTERFACE, null)?.takeIf { it.isNotBlank() },
            hostAddressLiteral = host,
            alternateAddressLiteral = prefs.getString(KEY_ALTERNATE, null)?.takeIf { it.isNotBlank() },
            preferredFamily = prefs.getString(KEY_FAMILY, null)?.takeIf { it.isNotBlank() },
        )
    }

    fun seedCandidates(
        context: Context,
        hotspotInfo: WirelessHotspotInfo,
        hotspotMode: WirelessHotspotMode,
    ): List<InetAddress> {
        val snapshot = load(context) ?: return emptyList()
        if (snapshot.hotspotMode != hotspotMode.name) return emptyList()
        if (snapshot.backendLabel != hotspotInfo.backend.label) return emptyList()
        val iface = hotspotInfo.interfaceName ?: snapshot.interfaceName
        if (snapshot.interfaceName != null && iface != null &&
            !snapshot.interfaceName.equals(iface, ignoreCase = true)
        ) {
            return emptyList()
        }
        return buildList {
            scopedAddress(snapshot.hostAddressLiteral, iface)?.let(::add)
            snapshot.alternateAddressLiteral?.let { scopedAddress(it, iface)?.let(::add) }
        }.distinctBy { it.hostAddress?.substringBefore('%') }
    }

    fun fastSelectHostAddress(
        context: Context,
        hotspotInfo: WirelessHotspotInfo,
        hotspotMode: WirelessHotspotMode,
        preferredFamily: String?,
    ): InetAddress? {
        val seeds = seedCandidates(context, hotspotInfo, hotspotMode)
        if (seeds.isEmpty()) return null
        return HotspotAddressPolicy.select(seeds, preferredFamily ?: snapshotFamily(context))
    }

    private fun snapshotFamily(context: Context): String? = load(context)?.preferredFamily

    private fun scopedAddress(literal: String, interfaceName: String?): InetAddress? = runCatching {
        val parsed = InetAddress.getByName(literal)
        if (parsed !is Inet6Address || !parsed.isLinkLocalAddress || interfaceName.isNullOrBlank()) {
            return@runCatching parsed
        }
        val scope = NetworkInterface.getByName(interfaceName)?.index ?: return@runCatching null
        if (scope == 0) return@runCatching null
        Inet6Address.getByAddress(parsed.hostAddress, parsed.address, scope)
    }.getOrNull()?.takeIf { addressOnInterface(it, interfaceName) }

    private fun addressOnInterface(address: InetAddress, interfaceName: String?): Boolean {
        if (interfaceName.isNullOrBlank()) return true
        val iface = runCatching { NetworkInterface.getByName(interfaceName) }.getOrNull() ?: return false
        val needle = address.hostAddress?.substringBefore('%') ?: return false
        return iface.inetAddresses.toList().any {
            it.hostAddress?.substringBefore('%') == needle
        }
    }
}
