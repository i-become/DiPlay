package com.shilapi.xcertplay.network

import android.content.Context
import android.net.wifi.WifiManager
import android.os.SystemClock
import com.shilapi.xcertplay.hud.Hs5HeadUnit

/**
 * Waits for station Wi-Fi to be usable before Wi-Fi Direct or existing-Wi-Fi CarPlay.
 *
 * Head-unit firmware often reports Wi-Fi as off in [WifiManager.isWifiEnabled] while the
 * settings UI already shows it on, or leaves [WifiManager.WIFI_STATE_ENABLING] for seconds after boot.
 */
object WifiStationReady {
    enum class Outcome {
        READY,
        TIMED_OUT,
        CANCELLED,
        NEEDS_USER,
        UNAVAILABLE,
    }

    fun ensure(
        context: Context,
        isCancelled: () -> Boolean = { false },
        timeoutMillis: Long,
        tryEnable: Boolean = true,
        log: (String) -> Unit = {},
    ): Outcome {
        val wifi = context.applicationContext.getSystemService(WifiManager::class.java)
            ?: return Outcome.UNAVAILABLE
        if (timeoutMillis <= 0) {
            return if (readPhase(wifi) == Phase.READY) Outcome.READY else Outcome.NEEDS_USER
        }
        val deadline = System.nanoTime() + timeoutMillis * 1_000_000L
        var enableRequested = false
        val pollMillis = WirelessStartupPolicy.interfacePollMillis(context).coerceIn(50L, 300L)

        fun remainingMillis() = ((deadline - System.nanoTime()) / 1_000_000L).coerceAtLeast(0)

        while (true) {
            if (isCancelled() || Thread.currentThread().isInterrupted) return Outcome.CANCELLED
            when (readPhase(wifi)) {
                Phase.READY -> return Outcome.READY
                Phase.ENABLING -> {
                    log("Wi-Fi station enabling; waiting")
                    if (remainingMillis() == 0L) return Outcome.TIMED_OUT
                }
                Phase.DISABLED -> {
                    if (tryEnable && !enableRequested && remainingMillis() > 0) {
                        enableRequested = true
                        if (WifiClientEnable.requestEnable(wifi, log)) {
                            log("Wi-Fi enable requested before wireless link")
                        }
                    }
                    if (remainingMillis() == 0L) return Outcome.NEEDS_USER
                }
                Phase.UNAVAILABLE -> return Outcome.UNAVAILABLE
            }
            val wait = minOf(pollMillis, remainingMillis()).coerceAtLeast(1)
            try {
                Thread.sleep(wait)
            } catch (_: InterruptedException) {
                Thread.currentThread().interrupt()
                return Outcome.CANCELLED
            }
        }
    }

    /** Confirms [WifiManager.isWifiEnabled] stays true across several polls (post-reboot quirk). */
    fun confirmStableWhenOn(
        context: Context,
        isCancelled: () -> Boolean = { false },
        timeoutMillis: Long,
        log: (String) -> Unit = {},
    ): Outcome {
        val wifi = context.applicationContext.getSystemService(WifiManager::class.java)
            ?: return Outcome.UNAVAILABLE
        if (readPhase(wifi) != Phase.READY) {
            return ensure(context, isCancelled, timeoutMillis, tryEnable = true, log = log)
        }
        val samples = WirelessStartupPolicy.stableSamples(context).coerceAtLeast(2)
        val pollMillis = WirelessStartupPolicy.interfacePollMillis(context).coerceIn(50L, 200L)
        val deadline = System.nanoTime() + timeoutMillis * 1_000_000L
        var stable = 0
        while (true) {
            if (isCancelled() || Thread.currentThread().isInterrupted) return Outcome.CANCELLED
            if (System.nanoTime() >= deadline) return Outcome.TIMED_OUT
            if (readPhase(wifi) == Phase.READY) {
                stable++
                if (stable >= samples) {
                    log("Wi-Fi station stable after $stable samples")
                    return Outcome.READY
                }
            } else {
                stable = 0
                log("Wi-Fi station dropped while stabilizing; waiting again")
                return ensure(
                    context,
                    isCancelled,
                    ((deadline - System.nanoTime()) / 1_000_000L).coerceAtLeast(1),
                    tryEnable = true,
                    log = log,
                )
            }
            try {
                Thread.sleep(pollMillis)
            } catch (_: InterruptedException) {
                Thread.currentThread().interrupt()
                return Outcome.CANCELLED
            }
        }
    }

    /**
     * Hongqi head units often report station Wi-Fi as on while Wi-Fi Direct still fails until the
     * driver toggles Wi-Fi in settings. Cycles the radio when it already reads enabled.
     */
    fun reinitializeWhenAlreadyOn(
        context: Context,
        isCancelled: () -> Boolean = { false },
        timeoutMillis: Long,
        log: (String) -> Unit = {},
    ): Outcome {
        val app = context.applicationContext
        if (!Hs5HeadUnit.isHeadUnit(app)) {
            return ensure(app, isCancelled, timeoutMillis, tryEnable = true, log = log)
        }
        val wifi = app.getSystemService(WifiManager::class.java) ?: return Outcome.UNAVAILABLE
        if (readPhase(wifi) != Phase.READY) {
            return ensure(app, isCancelled, timeoutMillis, tryEnable = true, log = log)
        }
        log("HS5 station Wi-Fi reads on; cycling radio before Wi-Fi Direct")
        if (!WifiClientEnable.requestDisable(wifi, log)) {
            log("Wi-Fi disable not accepted; stabilizing in place")
            return confirmStableWhenOn(app, isCancelled, timeoutMillis, log = log)
        }
        val halfMillis = (timeoutMillis / 2).coerceAtLeast(500)
        val disableDeadline = System.nanoTime() + halfMillis * 1_000_000L
        val pollMillis = WirelessStartupPolicy.interfacePollMillis(app).coerceIn(50L, 300L)
        while (readPhase(wifi) == Phase.READY) {
            if (isCancelled() || Thread.currentThread().isInterrupted) return Outcome.CANCELLED
            if (System.nanoTime() >= disableDeadline) break
            try {
                Thread.sleep(minOf(pollMillis, ((disableDeadline - System.nanoTime()) / 1_000_000L).coerceAtLeast(1)))
            } catch (_: InterruptedException) {
                Thread.currentThread().interrupt()
                return Outcome.CANCELLED
            }
        }
        WifiClientEnable.requestEnable(wifi, log)
        val enableDeadline = System.nanoTime() + halfMillis * 1_000_000L
        val remaining = ((enableDeadline - System.nanoTime()) / 1_000_000L).coerceAtLeast(500)
        return ensure(app, isCancelled, remaining, tryEnable = true, log = log)
    }

    fun preflightWaitMillis(context: Context): Long {
        val afterBoot = SystemClock.elapsedRealtime() < WirelessStartupPolicy.bootGraceMillis(context)
        return when {
            Hs5HeadUnit.isHeadUnit(context) && afterBoot -> 12_000L
            Hs5HeadUnit.isHeadUnit(context) -> 4_000L
            afterBoot -> 8_000L
            else -> 3_000L
        }
    }

    fun outcomeMessage(outcome: Outcome): String = when (outcome) {
        Outcome.TIMED_OUT ->
            "Wi-Fi did not turn on before connection timeout; check the head unit's Wi-Fi settings"
        Outcome.NEEDS_USER -> "Turn on Wi-Fi in the head unit's settings, then reconnect"
        Outcome.UNAVAILABLE -> "Wi-Fi radio is unavailable on the head unit"
        Outcome.CANCELLED -> "Wi-Fi startup was cancelled"
        Outcome.READY -> "Wi-Fi is on"
    }

    private enum class Phase { READY, ENABLING, DISABLED, UNAVAILABLE }

    private fun readPhase(wifi: WifiManager): Phase = runCatching {
        if (wifi.isWifiEnabled) return Phase.READY
        @Suppress("DEPRECATION")
        when (wifi.wifiState) {
            WifiManager.WIFI_STATE_ENABLING -> Phase.ENABLING
            WifiManager.WIFI_STATE_ENABLED -> Phase.READY
            else -> Phase.DISABLED
        }
    }.getOrDefault(Phase.UNAVAILABLE)
}
