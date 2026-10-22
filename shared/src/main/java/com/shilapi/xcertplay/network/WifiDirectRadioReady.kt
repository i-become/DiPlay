package com.shilapi.xcertplay.network

import com.shilapi.xcertplay.diagnostics.DiagnosticLogging

import android.content.Context
import android.net.wifi.WifiManager
import android.os.Build
import android.util.Log
import java.io.IOException

/** Ensures station Wi-Fi is on before Wi-Fi Direct group creation. */
object WifiDirectRadioReady {
    private const val TAG = "DiPlay-WifiDirectRadio"

    enum class Bootstrap {
        /** [WifiManager.isWifiEnabled] is true. */
        READY,
        /** Wi-Fi is turning on; caller should wait. */
        ENABLING,
        /** The app cannot enable Wi-Fi; the driver must turn it on in settings. */
        NEEDS_USER,
        /** No Wi-Fi manager or radio. */
        UNAVAILABLE,
    }

    /**
     * Requests Wi-Fi when it is off (API 29+), then reports whether the radio is ready or still enabling.
     */
    fun bootstrap(context: Context, log: (String) -> Unit = {}): Bootstrap {
        val wifi = context.applicationContext.getSystemService(WifiManager::class.java)
            ?: return Bootstrap.UNAVAILABLE
        return try {
            if (wifi.isWifiEnabled) return Bootstrap.READY
            when (wifi.wifiState) {
                WifiManager.WIFI_STATE_ENABLING -> Bootstrap.ENABLING
                else -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    if (WifiClientEnable.requestEnable(wifi, log)) {
                        log("Wi-Fi enable requested before Wi-Fi Direct")
                        DiagnosticLogging.logcat { Log.i(TAG, "Wi-Fi enable requested before Wi-Fi Direct") }
                        Bootstrap.ENABLING
                    } else {
                        DiagnosticLogging.logcat { Log.w(TAG, "Wi-Fi enable request refused; system action required") }
                        Bootstrap.NEEDS_USER
                    }
                } else {
                    @Suppress("DEPRECATION")
                    if (wifi.setWifiEnabled(true)) {
                        log("Wi-Fi enable requested before Wi-Fi Direct")
                        Bootstrap.ENABLING
                    } else {
                        Bootstrap.NEEDS_USER
                    }
                }
            }
        } catch (_: SecurityException) {
            DiagnosticLogging.logcat { Log.w(TAG, "Wi-Fi enable requires system authorization") }
            Bootstrap.NEEDS_USER
        } catch (error: RuntimeException) {
            DiagnosticLogging.logcat { Log.w(TAG, "Wi-Fi radio unavailable: ${error.javaClass.simpleName}") }
            Bootstrap.UNAVAILABLE
        }
    }

    fun ensure(
        context: Context,
        timeoutMillis: Long,
        isCancelled: () -> Boolean = { false },
        log: (String) -> Unit = {},
    ) {
        when (bootstrap(context, log)) {
            Bootstrap.READY -> return
            Bootstrap.UNAVAILABLE -> throw IOException(WifiStationReady.outcomeMessage(WifiStationReady.Outcome.UNAVAILABLE))
            Bootstrap.NEEDS_USER -> throw IOException(WifiStationReady.outcomeMessage(WifiStationReady.Outcome.NEEDS_USER))
            Bootstrap.ENABLING -> Unit
        }
        log("Wi-Fi P2P waiting for Wi-Fi radio to enable")
        val waitMillis = timeoutMillis.coerceIn(1L, 10_000L)
        when (
            WifiStationReady.ensure(
                context,
                isCancelled = isCancelled,
                timeoutMillis = waitMillis,
                tryEnable = true,
                log = log,
            )
        ) {
            WifiStationReady.Outcome.READY -> log("Wi-Fi P2P radio ready")
            WifiStationReady.Outcome.CANCELLED -> throw IOException("Wi-Fi P2P startup was cancelled")
            else -> throw IOException(WifiStationReady.outcomeMessage(WifiStationReady.Outcome.TIMED_OUT))
        }
    }
}
