package com.shilapi.xcertplay

import androidx.annotation.StringRes
import com.shilapi.xcertplay.host.R
import com.shilapi.xcertplay.orchestration.CarPlayStatus

/**
 * Maps CarPlay connection evidence to 27 monotonic steps for the waiting-screen dot progress.
 *
 * Wireless is the primary path; wired USB steps reuse the same indices where they overlap.
 */
internal object CarPlayConnectionProgress {
    const val STEP_COUNT = 27

    fun stepLabel(step: Int): String = when (step) {
        1 -> "mfi-or-usb-start"
        2 -> "mfi-wait"
        3 -> "mfi-or-usb-permission"
        4 -> "mfi-ready"
        5 -> "hotspot-or-usb-wait"
        6 -> "hotspot-bluetooth-ready"
        7 -> "hotspot-host-address"
        8 -> "usb-config"
        9 -> "airplay-attach"
        10 -> "bonjour-or-usb-data"
        11 -> "iphone-select-or-pair"
        12 -> "rfcomm-or-carkit"
        13 -> "rfcomm-connected"
        14 -> "iap2-channel"
        15 -> "iap2-control-loop"
        16 -> "iap2-identification"
        17 -> "iap2-authentication"
        18 -> "iap2-subscriptions"
        19 -> "wifi-config-request"
        20 -> "wifi-config-sent"
        21 -> "carplay-availability"
        22 -> "start-session"
        23 -> "iphone-wifi-handshake"
        24 -> "wait-first-tcp-3s"
        25 -> "wait-first-tcp-6s-or-bonjour-tcp"
        26 -> "wait-first-tcp-9s-or-first-tcp"
        27 -> "session-handoff-video"
        else -> "step-$step"
    }

    @StringRes
    fun stepTitleRes(step: Int): Int = when (step.coerceIn(0, STEP_COUNT)) {
        0 -> R.string.carplay_wait_step_00
        1 -> R.string.carplay_wait_step_01
        2 -> R.string.carplay_wait_step_02
        3 -> R.string.carplay_wait_step_03
        4 -> R.string.carplay_wait_step_04
        5 -> R.string.carplay_wait_step_05
        6 -> R.string.carplay_wait_step_06
        7 -> R.string.carplay_wait_step_07
        8 -> R.string.carplay_wait_step_08
        9 -> R.string.carplay_wait_step_09
        10 -> R.string.carplay_wait_step_10
        11 -> R.string.carplay_wait_step_11
        12 -> R.string.carplay_wait_step_12
        13 -> R.string.carplay_wait_step_13
        14 -> R.string.carplay_wait_step_14
        15 -> R.string.carplay_wait_step_15
        16 -> R.string.carplay_wait_step_16
        17 -> R.string.carplay_wait_step_17
        18 -> R.string.carplay_wait_step_18
        19 -> R.string.carplay_wait_step_19
        20 -> R.string.carplay_wait_step_20
        21 -> R.string.carplay_wait_step_21
        22 -> R.string.carplay_wait_step_22
        23 -> R.string.carplay_wait_step_23
        24 -> R.string.carplay_wait_step_24
        25 -> R.string.carplay_wait_step_25
        26 -> R.string.carplay_wait_step_26
        27 -> R.string.carplay_wait_step_27
        else -> R.string.carplay_wait_step_00
    }

    fun stepIndexFromDebugLog(message: String): Int? {
        val line = message.trim()
        var best = 0
        for (marker in logMarkers) {
            if (marker.matches(line)) best = maxOf(best, marker.step)
        }
        return best.takeIf { it > 0 }
    }

    fun stepIndexFromStatus(status: CarPlayStatus): Int? = when (status) {
        CarPlayStatus.DiscoveringMfi -> 1
        CarPlayStatus.WaitingForMfi -> 2
        CarPlayStatus.RequestingMfiPermission -> 3
        CarPlayStatus.MfiReady -> 4
        CarPlayStatus.StartingHotspot -> 5
        is CarPlayStatus.HotspotReady -> 7
        CarPlayStatus.WaitingForPairedIphone -> 11
        CarPlayStatus.ConnectingBluetooth -> 12
        CarPlayStatus.AttachingNetwork -> 9
        CarPlayStatus.RunningWireless -> 15
        CarPlayStatus.WirelessActive, CarPlayStatus.WirelessActiveFallback -> 26
        CarPlayStatus.DiscoveringIphone -> 5
        CarPlayStatus.WaitingForIphone -> 6
        CarPlayStatus.RequestingIphonePermission -> 7
        CarPlayStatus.WaitingForReenumeration -> 8
        CarPlayStatus.SelectingConfiguration -> 9
        CarPlayStatus.OpeningDataPaths -> 10
        CarPlayStatus.Pairing -> 11
        CarPlayStatus.ConnectingControl -> 12
        CarPlayStatus.RunningControl -> 15
        CarPlayStatus.ControlEnded, is CarPlayStatus.Failed -> null
    }

    private data class LogMarker(val step: Int, val matches: (String) -> Boolean)

    private val logMarkers = listOf(
        LogMarker(1) { it.startsWith("STEP mfi/start:") || it.startsWith("STEP usb/discover:") ||
            it.contains("wireless bring-up generation=") && it.endsWith("starting") },
        LogMarker(2) { it.startsWith("STEP mfi/wait:") },
        LogMarker(3) { it.startsWith("STEP mfi/permission:") || it.startsWith("STEP usb/permission:") },
        LogMarker(4) { it.startsWith("STEP mfi/ready:") },
        LogMarker(5) { it.startsWith("STEP wifi/ap:") || it.startsWith("STEP usb/wait:") },
        LogMarker(6) { it.contains("wireless bring-up phase=hotspot+bluetooth") },
        LogMarker(7) { it.startsWith("STEP wifi/ap-ready:") || it.contains("wireless bring-up phase=host-address") ||
            it.startsWith("STEP usb/reenum:") },
        LogMarker(8) { it.startsWith("STEP usb/config:") },
        LogMarker(9) { it.startsWith("STEP network/attach:") || it.contains("wireless AirPlay listener attached") },
        LogMarker(10) {
            it.contains("wireless bring-up phase=airplay+bonjour") ||
                it.contains("wireless Bonjour services started") ||
                it.startsWith("STEP usb/data:")
        },
        LogMarker(11) {
            it.startsWith("STEP bt/select:") || it.startsWith("STEP lockdown/pair:") ||
                it.contains("wireless selected Bluetooth target")
        },
        LogMarker(12) {
            it.startsWith("STEP bt/rfcomm:") || it.startsWith("STEP lockdown/carkit:") ||
                it.contains("wireless RFCOMM awaiting parallel connect")
        },
        LogMarker(13) {
            it.contains("wireless bring-up phase=rfcomm") || it.contains("wireless RFCOMM connected")
        },
        LogMarker(14) {
            it.contains("wireless bring-up phase=iap2-csm") ||
                it.contains("wireless iAP2 CSM channel opened") ||
                it.startsWith("STEP iap2/wired:")
        },
        LogMarker(15) { it.startsWith("STEP iap2/wireless:") },
        LogMarker(16) { it.contains("iap2 identification accepted") },
        LogMarker(17) {
            it.contains("iap2 authentication accepted") ||
                it.contains("iap2 mfi rx=0xaa05 authentication-succeeded")
        },
        LogMarker(18) { it.contains("iap2 subscriptions sent") },
        LogMarker(19) { it.contains("iap2 rx=0x5702 request-wifi-configuration") },
        LogMarker(20) { it.contains("iap2 tx=0x5703 accessory-wifi-configuration") },
        LogMarker(21) { it.contains("iap2 rx=0x4300 carplay-availability") },
        LogMarker(22) {
            it.contains("StartSession atNs=") || it.contains("iap2 tx=0x4301 carplay-start-session")
        },
        LogMarker(23) {
            it.contains("iap2 tx=0x5703 post-transport") ||
                it.contains("iap2 rx=0x4e0d wireless-carplay-update") ||
                it.contains("iap2 rx=0x4e0e device-transport-identifier")
        },
        LogMarker(24) { waitFirstTcpElapsedMs(it)?.let { ms -> ms >= 3_000 } == true },
        LogMarker(25) {
            waitFirstTcpElapsedMs(it)?.let { ms -> ms >= 6_000 } == true ||
                (it.contains("wireless bonjour:") && it.contains("stage=TCP_CONNECTED"))
        },
        LogMarker(26) {
            waitFirstTcpElapsedMs(it)?.let { ms -> ms >= 9_000 } == true ||
                it.contains("first TCP atNs=")
        },
        LogMarker(27) {
            it.contains("session established atNs=") ||
                it.startsWith("STEP handoff/complete:") ||
                it.startsWith("STEP handoff/fallback:") ||
                it.contains("wireless iAP2 tunnel ready") ||
                it.contains("first frame rendered")
        },
    )

    private fun waitFirstTcpElapsedMs(line: String): Long? {
        if (!line.contains("PROGRESS wait-first-tcp elapsedMs=")) return null
        return Regex("elapsedMs=(\\d+)").find(line)?.groupValues?.get(1)?.toLongOrNull()
    }
}
