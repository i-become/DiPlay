package com.shilapi.xcertplay

import android.content.Context
import android.os.SystemClock
import android.util.Log
import com.shilapi.xcertplay.hud.Hs5HeadUnit
import com.shilapi.xcertplay.network.WifiStationReady
import com.shilapi.xcertplay.network.WirelessLinkPrepare
import com.shilapi.xcertplay.network.WirelessStartupPolicy
import com.shilapi.xcertplay.orchestration.WirelessHotspotMode
import java.util.concurrent.atomic.AtomicBoolean

/** Turns Wi-Fi on early on Hongqi head units so wireless CarPlay does not wait on cold boot. */
internal object Hs5WirelessPrewarm {
    private const val TAG = "Xcert-Hs5-WiFi"
    private val running = AtomicBoolean(false)

    fun schedule(context: Context) {
        val app = context.applicationContext
        if (!Hs5HeadUnit.isHeadUnit(app)) return
        val wirelessPlanned = AirPlayPersistence.loadWirelessEnabled(app) ||
            DiPlayPreferences.autoConnectWireless(app)
        if (!wirelessPlanned) return
        val mode = AirPlayPersistence.loadWirelessHotspotMode(app)
        if (!running.compareAndSet(false, true)) return
        Thread({
            try {
                when (mode) {
                    WirelessHotspotMode.WIFI_P2P, WirelessHotspotMode.EXISTING_WIFI -> {
                        val afterBoot = SystemClock.elapsedRealtime() < WirelessStartupPolicy.bootGraceMillis(app)
                        val timeoutMillis = if (afterBoot) {
                            WirelessStartupPolicy.HOTSPOT_READY_MILLIS
                        } else {
                            WirelessStartupPolicy.hotspotReadyMillis(app, mode)
                        }
                        val outcome = if (WirelessStartupPolicy.clientWifiEnabled(app)) {
                            Log.i(TAG, "prewarm station Wi-Fi reads on; confirming stable")
                            WifiStationReady.confirmStableWhenOn(
                                app,
                                isCancelled = { false },
                                timeoutMillis = timeoutMillis,
                                log = { Log.i(TAG, it) },
                            )
                        } else {
                            WifiStationReady.ensure(
                                app,
                                isCancelled = { false },
                                timeoutMillis = timeoutMillis,
                                tryEnable = true,
                                log = { Log.i(TAG, it) },
                            )
                        }
                        Log.i(TAG, "prewarm finished outcome=$outcome")
                    }
                    WirelessHotspotMode.MANUAL -> {
                        WirelessLinkPrepare.prewarmManualHotspotInterface(app)
                        Log.i(TAG, "prewarm manual hotspot interface sampled")
                    }
                    else -> Unit
                }
            } finally {
                running.set(false)
            }
        }, "diplay-hq-wifi-prewarm").apply { isDaemon = true }.start()
    }
}
