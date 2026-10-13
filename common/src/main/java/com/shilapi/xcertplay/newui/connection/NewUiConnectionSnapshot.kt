package com.shilapi.xcertplay.newui.connection

import android.content.Context
import com.shilapi.xcertplay.CarPlayBackgroundSession
import com.shilapi.xcertplay.DiPlayPreferences
import com.shilapi.xcertplay.host.R
import com.shilapi.xcertplay.orchestration.WirelessHotspotMode
import com.shilapi.xcertplay.AirPlayPersistence
import java.net.Inet4Address
import java.net.NetworkInterface

enum class NewUiConnectionPhase {
    NEEDS_SETUP,
    READY,
    CONNECTING,
    CONNECTED,
}

data class NewUiConnectionSnapshot(
    val phase: NewUiConnectionPhase,
    val statusTitle: String,
    val phoneName: String,
    val linkSummary: String?,
    val connectionMethodLabel: String,
    val locationReportingEnabled: Boolean,
    val canConnect: Boolean,
    val sessionRunning: Boolean,
    val hasSelectedPhone: Boolean,
) {
    val isConnected: Boolean get() = phase == NewUiConnectionPhase.CONNECTED

    companion object {
        /** Used before [android.app.Activity.onCreate]; must not touch [Context]-backed prefs. */
        val placeholder = NewUiConnectionSnapshot(
            phase = NewUiConnectionPhase.READY,
            statusTitle = "",
            phoneName = "",
            linkSummary = null,
            connectionMethodLabel = "",
            locationReportingEnabled = false,
            canConnect = true,
            sessionRunning = false,
            hasSelectedPhone = false,
        )
    }
}

internal object NewUiConnectionSnapshotReader {
    fun read(context: Context, setupError: String?): NewUiConnectionSnapshot {
        val sessionRunning = CarPlayBackgroundSession.hasSession()
        val carPlayActive = CarPlayBackgroundSession.active
        val phoneAddress = DiPlayPreferences.phoneAddress(context)
        val phoneName = DiPlayPreferences.phoneName(context)
        val phase = when {
            setupError != null -> NewUiConnectionPhase.NEEDS_SETUP
            carPlayActive -> NewUiConnectionPhase.CONNECTED
            sessionRunning -> NewUiConnectionPhase.CONNECTING
            DiPlayPreferences.phoneAddress(context) != null -> NewUiConnectionPhase.READY
            else -> NewUiConnectionPhase.READY
        }
        val statusTitle = when {
            setupError != null -> context.getString(R.string.setup_needs_attention)
            carPlayActive -> context.getString(R.string.new_ui_status_connected)
            sessionRunning -> context.getString(R.string.connecting_to_your_iphone)
            else -> context.getString(R.string.ready_when_you_are)
        }
        val linkSummary = if (carPlayActive || sessionRunning) {
            val mode = wirelessModeLabel(context, AirPlayPersistence.loadWirelessHotspotMode(context))
            val host = localIpv4()?.let { "$mode | $it" } ?: mode
            host
        } else {
            null
        }
        val connectionMethodLabel = wirelessModeLabel(context, AirPlayPersistence.loadWirelessHotspotMode(context))
        return NewUiConnectionSnapshot(
            phase = phase,
            statusTitle = statusTitle,
            phoneName = phoneName,
            linkSummary = linkSummary,
            connectionMethodLabel = connectionMethodLabel,
            locationReportingEnabled = AirPlayPersistence.loadLocationReportingEnabled(context),
            canConnect = setupError == null,
            sessionRunning = sessionRunning,
            hasSelectedPhone = phoneAddress != null,
        )
    }

    private fun wirelessModeLabel(context: Context, mode: WirelessHotspotMode): String = when (mode) {
        WirelessHotspotMode.WIFI_P2P -> context.getString(R.string.new_ui_link_wifi_direct)
        WirelessHotspotMode.MANUAL -> context.getString(R.string.new_ui_link_car_hotspot)
        WirelessHotspotMode.EXISTING_WIFI -> context.getString(R.string.new_ui_link_existing_wifi)
        WirelessHotspotMode.LOCAL_ONLY_HOTSPOT -> context.getString(R.string.new_ui_link_local_hotspot)
    }

    private fun localIpv4(): String? = runCatching {
        NetworkInterface.getNetworkInterfaces()?.toList()?.asSequence()
            ?.flatMap { iface -> iface.inetAddresses.toList().asSequence() }
            ?.firstOrNull { address ->
                !address.isLoopbackAddress && address is Inet4Address && !address.isLinkLocalAddress
            }
            ?.hostAddress
    }.getOrNull()
}
