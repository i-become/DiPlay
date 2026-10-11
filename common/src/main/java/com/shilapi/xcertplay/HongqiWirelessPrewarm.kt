package com.shilapi.xcertplay

import android.content.Context
import android.util.Log
import com.shilapi.xcertplay.hud.HongqiHeadUnit
import com.shilapi.xcertplay.network.WifiClientEnable
import com.shilapi.xcertplay.network.WirelessLinkPrepare
import com.shilapi.xcertplay.network.WirelessStartupPolicy
import com.shilapi.xcertplay.orchestration.WirelessHotspotMode
import java.util.concurrent.atomic.AtomicBoolean

/** Turns Wi-Fi on early on Hongqi head units so wireless CarPlay does not wait on cold boot. */
internal object HongqiWirelessPrewarm {
    private const val TAG = "DiPlay-Hongqi-WiFi"
    private val running = AtomicBoolean(false)

    fun schedule(context: Context) {
        val app = context.applicationContext
        if (!HongqiHeadUnit.isHeadUnit(app)) return
        if (!AirPlayPersistence.loadWirelessEnabled(app)) return
        val mode = AirPlayPersistence.loadWirelessHotspotMode(app)
        if (!running.compareAndSet(false, true)) return
        Thread({
            try {
                when (mode) {
                    WirelessHotspotMode.WIFI_P2P, WirelessHotspotMode.EXISTING_WIFI -> {
                        if (WirelessStartupPolicy.clientWifiEnabled(app)) {
                            Log.i(TAG, "prewarm skip station Wi-Fi already on")
                            return@Thread
                        }
                        val result = WifiClientEnable.enable(
                            app,
                            isCancelled = { false },
                            timeoutMillis = WirelessStartupPolicy.hotspotReadyMillis(app, mode),
                            log = { Log.i(TAG, it) },
                        )
                        Log.i(TAG, "prewarm finished result=${result.diagnostic}")
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
