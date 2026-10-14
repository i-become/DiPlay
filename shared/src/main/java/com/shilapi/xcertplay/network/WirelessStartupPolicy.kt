package com.shilapi.xcertplay.network

import android.content.Context
import android.net.wifi.WifiManager
import android.os.SystemClock
import com.shilapi.xcertplay.hud.Hs5HeadUnit
import com.shilapi.xcertplay.orchestration.WirelessHotspotMode
import java.io.IOException

object WirelessStartupPolicy {
    const val HOTSPOT_READY_MILLIS = 15_000L
    /**
     * Upper bound for wireless bring-up (Wi-Fi client, car hotspot, AP interface address).
     * Poll loops exit as soon as the radio is ready; this is not an intentional delay.
     */
    private const val HONGQI_HOTSPOT_READY_MILLIS = 6_000L
    /** HS5/QiSi often boot with Wi-Fi and hotspot already up; interface detection needs less headroom. */
    private const val HONGQI_FAST_BRINGUP_MILLIS = 2_500L
    /** After boot, Hongqi Wi-Fi / P2P can look ready while the radio is still starting. */
    private const val HONGQI_BOOT_GRACE_MILLIS = 180_000L
    private const val GENERIC_BOOT_GRACE_MILLIS = 120_000L
    const val INTERFACE_POLL_MILLIS = 250L
    private const val HONGQI_INTERFACE_POLL_MILLIS = 100L
    private const val HONGQI_FAST_INTERFACE_POLL_MILLIS = 50L
    const val STABLE_SAMPLES = 3
    private const val HONGQI_STABLE_SAMPLES = 2

    fun hotspotReadyMillis(context: Context, linkMode: WirelessHotspotMode? = null): Long {
        if (!Hs5HeadUnit.isHeadUnit(context)) return HOTSPOT_READY_MILLIS
        if (linkMode != null && hongqiFastBringUp(context, linkMode)) return HONGQI_FAST_BRINGUP_MILLIS
        return HONGQI_HOTSPOT_READY_MILLIS
    }

    fun interfacePollMillis(context: Context, linkMode: WirelessHotspotMode? = null): Long {
        if (Hs5HeadUnit.isHeadUnit(context)) {
            if (linkMode != null && hongqiFastBringUp(context, linkMode)) return HONGQI_FAST_INTERFACE_POLL_MILLIS
            return HONGQI_INTERFACE_POLL_MILLIS
        }
        return INTERFACE_POLL_MILLIS
    }

    fun stableSamples(context: Context, linkMode: WirelessHotspotMode? = null): Int {
        if (Hs5HeadUnit.isHeadUnit(context)) {
            if (linkMode != null && hongqiFastBringUp(context, linkMode)) return 1
            return HONGQI_STABLE_SAMPLES
        }
        return STABLE_SAMPLES
    }

    /** True when station Wi-Fi is already on (typical on Hongqi head units). */
    fun clientWifiEnabled(context: Context): Boolean = runCatching {
        context.applicationContext.getSystemService(WifiManager::class.java)?.isWifiEnabled == true
    }.getOrDefault(false)

    /**
     * Assume the car keeps station Wi-Fi on: try Wi-Fi Direct / existing-Wi-Fi CarPlay first,
     * and only enable the radio after the first link attempt fails.
     */
    fun optimisticStationWifi(context: Context, mode: WirelessHotspotMode): Boolean {
        if (!Hs5HeadUnit.isHeadUnit(context)) return false
        // Try the wireless link first; do not block on station Wi-Fi appearing enabled.
        return mode == WirelessHotspotMode.WIFI_P2P || mode == WirelessHotspotMode.EXISTING_WIFI
    }

    /** When true, [WirelessLinkPrepare] does not wait for or enable station Wi-Fi before the link attempt. */
    fun skipStationWifiPrepare(mode: WirelessHotspotMode): Boolean =
        mode == WirelessHotspotMode.WIFI_P2P

    /**
     * Tighter budgets when the saved link mode's radio is already up.
     * Manual mode treats an unknown AP state as "do not block" because QiSi often hides [CarHotspotStatus].
     */
    fun bootGraceMillis(context: Context): Long =
        if (Hs5HeadUnit.isHeadUnit(context)) HONGQI_BOOT_GRACE_MILLIS else GENERIC_BOOT_GRACE_MILLIS

    fun wifiP2pStartTimeoutMillis(context: Context): Long {
        if (Hs5HeadUnit.isHeadUnit(context) &&
            SystemClock.elapsedRealtime() < HONGQI_BOOT_GRACE_MILLIS
        ) {
            return 35_000L
        }
        return 20_000L
    }

    fun hongqiFastBringUp(context: Context, mode: WirelessHotspotMode): Boolean {
        if (!Hs5HeadUnit.isHeadUnit(context)) return false
        if (SystemClock.elapsedRealtime() < HONGQI_BOOT_GRACE_MILLIS) return false
        if (optimisticStationWifi(context, mode)) return true
        return when (mode) {
            WirelessHotspotMode.WIFI_P2P, WirelessHotspotMode.EXISTING_WIFI -> clientWifiEnabled(context)
            WirelessHotspotMode.MANUAL ->
                clientWifiEnabled(context) && CarHotspotStatus.isEnabled(context) != false
            WirelessHotspotMode.LOCAL_ONLY_HOTSPOT -> false
        }
    }

    /** Station Wi-Fi enable must not consume the whole bring-up budget before AP detection runs. */
    fun wirelessLinkPrepareMaxMillis(context: Context, linkMode: WirelessHotspotMode? = null): Long {
        if (linkMode != null && optimisticStationWifi(context, linkMode)) return 0L
        val total = hotspotReadyMillis(context, linkMode)
        if (linkMode != null && hongqiFastBringUp(context, linkMode)) return 1L
        val cap = if (Hs5HeadUnit.isHeadUnit(context)) 2_500L else 5_000L
        return minOf(cap, total / 2).coerceAtLeast(1)
    }
    const val FIRST_TCP_MILLIS = 30_000L
    const val MAX_STARTUP_RETRIES = 5
    const val STABLE_SESSION_MILLIS = 60_000L
}

enum class WirelessStartupFailure {
    HOTSPOT_NOT_READY, FIRST_TCP_TIMEOUT, HOTSPOT_CONFIGURATION,

    /** The head unit's Android Bluetooth accepts connections that reach no phone. */
    BLUETOOTH_UNUSABLE;

    /** False when no further attempt can change what went wrong. */
    val retryable: Boolean get() = this != HOTSPOT_CONFIGURATION && this != BLUETOOTH_UNUSABLE
}

class WirelessStartupException(val reason: WirelessStartupFailure, message: String) : IOException(message)
