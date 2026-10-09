package com.shilapi.xcertplay.network

import android.content.Context
import com.shilapi.xcertplay.orchestration.WirelessHotspotMode

/**
 * Brings up the head-unit radio that the saved wireless link mode needs before CarPlay starts.
 *
 * Car hotspot mode starts tethering when it is off; Wi-Fi Direct and existing Wi-Fi turn station
 * Wi-Fi on. USB and modes that manage their own AP skip this step.
 */
object WirelessLinkPrepare {
    fun prepare(
        context: Context,
        mode: WirelessHotspotMode,
        isCancelled: () -> Boolean,
        timeoutMillis: Long,
        log: (String) -> Unit,
    ) {
        when (mode) {
            WirelessHotspotMode.MANUAL -> prepareManualHotspot(context, isCancelled, timeoutMillis, log)
            WirelessHotspotMode.WIFI_P2P, WirelessHotspotMode.EXISTING_WIFI ->
                prepareWifiClient(context, isCancelled, timeoutMillis, log)
            WirelessHotspotMode.LOCAL_ONLY_HOTSPOT -> Unit
        }
    }

    private fun prepareManualHotspot(
        context: Context,
        isCancelled: () -> Boolean,
        timeoutMillis: Long,
        log: (String) -> Unit,
    ) {
        if (CarHotspotStatus.isEnabled(context) == true) return
        val result = CarHotspotTethering.enable(
            context,
            isCancelled = isCancelled,
            timeoutMillis = timeoutMillis,
            log = log,
        )
        val manualFallback = result == CarHotspotTethering.Result.UNSUPPORTED &&
            CarHotspotStatus.isEnabled(context) == null
        if (result != CarHotspotTethering.Result.READY && !manualFallback) {
            throw WirelessStartupException(
                if (result == CarHotspotTethering.Result.TIMED_OUT ||
                    result == CarHotspotTethering.Result.FAILED)
                    WirelessStartupFailure.HOTSPOT_NOT_READY else WirelessStartupFailure.HOTSPOT_CONFIGURATION,
                "${result.diagnostic}. Open the car hotspot settings and connect again.",
            )
        }
    }

    private fun prepareWifiClient(
        context: Context,
        isCancelled: () -> Boolean,
        timeoutMillis: Long,
        log: (String) -> Unit,
    ) {
        val result = WifiClientEnable.enable(context, isCancelled, timeoutMillis, log)
        if (result == WifiClientEnable.Result.READY) return
        throw WirelessStartupException(
            WirelessStartupFailure.HOTSPOT_NOT_READY,
            when (result) {
                WifiClientEnable.Result.TIMED_OUT -> "Timed out turning Wi-Fi on. Open Wi-Fi settings and connect again."
                WifiClientEnable.Result.CANCELLED -> "Wi-Fi startup was cancelled"
                else -> "Turn on Wi-Fi in the head unit's settings, then reconnect"
            },
        )
    }
}
