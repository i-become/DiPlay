package com.shilapi.xcertplay.network

import android.content.Context
import com.shilapi.xcertplay.hud.HongqiHeadUnit
import com.shilapi.xcertplay.orchestration.WirelessHotspotMode

/**
 * Brings up the head-unit radio that the saved wireless link mode needs before CarPlay starts.
 *
 * Car hotspot mode starts tethering when it is off; Wi-Fi Direct and existing Wi-Fi turn station
 * Wi-Fi on. USB and modes that manage their own AP skip this step.
 */
object WirelessLinkPrepare {
    /** Warms AP interface discovery on Hongqi when Wi-Fi and hotspot are already up. */
    fun prewarmManualHotspotInterface(context: Context) {
        if (!WirelessStartupPolicy.hongqiFastBringUp(context, WirelessHotspotMode.MANUAL)) return
        ManualHotspotInterfaces(context.applicationContext).use { it.sample() }
    }

    fun prepare(
        context: Context,
        mode: WirelessHotspotMode,
        isCancelled: () -> Boolean,
        timeoutMillis: Long,
        log: (String) -> Unit,
        assumeStationWifiOn: Boolean = WirelessStartupPolicy.optimisticStationWifi(context, mode),
    ) {
        when (mode) {
            WirelessHotspotMode.MANUAL -> prepareManualHotspot(context, isCancelled, timeoutMillis, log)
            WirelessHotspotMode.WIFI_P2P, WirelessHotspotMode.EXISTING_WIFI ->
                prepareWifiClient(
                    context,
                    isCancelled,
                    timeoutMillis,
                    log,
                    assumeStationWifiOn || WirelessStartupPolicy.skipStationWifiPrepare(mode),
                )
            WirelessHotspotMode.LOCAL_ONLY_HOTSPOT -> Unit
        }
    }

    private fun prepareManualHotspot(
        context: Context,
        isCancelled: () -> Boolean,
        timeoutMillis: Long,
        log: (String) -> Unit,
    ) {
        when (CarHotspotStatus.isEnabled(context)) {
            true -> return
            null -> if (HongqiHeadUnit.isHeadUnit(context)) {
                log("manual hotspot: AP state hidden on Hongqi; skip auto-enable")
                return
            }
            false -> Unit
        }
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
        assumeStationWifiOn: Boolean,
    ) {
        if (assumeStationWifiOn) {
            log("optimistic station Wi-Fi: attempting CarPlay link before enabling radio")
            return
        }
        val result = WifiClientEnable.enable(context, isCancelled, timeoutMillis, log)
        if (result == WifiClientEnable.Result.READY) return
        throw WirelessStartupException(
            WirelessStartupFailure.HOTSPOT_NOT_READY,
            when (result) {
                WifiClientEnable.Result.TIMED_OUT ->
                    WifiStationReady.outcomeMessage(WifiStationReady.Outcome.TIMED_OUT)
                WifiClientEnable.Result.CANCELLED -> "Wi-Fi startup was cancelled"
                else -> WifiStationReady.outcomeMessage(WifiStationReady.Outcome.NEEDS_USER)
            },
        )
    }
}
