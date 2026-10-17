package com.shilapi.xcertplay

import com.shilapi.xcertplay.orchestration.WirelessHotspotMode

/**
 * Whether a Hongqi boot may open CarPlay directly instead of the DiPlay home page.
 * Skipping the home page saves its start-up; any case that needs the driver keeps it.
 */
internal object Hs5BootLaunchPolicy {
    fun opensCarPlayDirectly(
        hongqi: Boolean,
        autoConnect: Boolean,
        phoneAddress: String?,
        wireless: Boolean,
        hotspotMode: WirelessHotspotMode,
        notificationsAllowed: Boolean,
        sessionRunning: Boolean,
        setupReady: Boolean,
    ): Boolean {
        if (!hongqi || !autoConnect || sessionRunning || !setupReady) return false
        if (phoneAddress.isNullOrBlank()) return false
        if (wireless && hotspotMode == WirelessHotspotMode.MANUAL) return false
        return notificationsAllowed
    }
}
