package com.shilapi.xcertplay.network

import android.content.Context
import android.net.wifi.WifiManager
import android.os.Build
import com.shilapi.xcertplay.adb.AdbKeys
import com.shilapi.xcertplay.adb.LocalAdb
import com.shilapi.xcertplay.hud.HongqiHeadUnit

/** Turns on station Wi-Fi when it is off. Used for Wi-Fi Direct and existing Wi-Fi CarPlay links. */
object WifiClientEnable {
    enum class Result(val diagnostic: String) {
        READY("Wi-Fi is on"),
        FAILED("The car could not turn Wi-Fi on"),
        TIMED_OUT("Timed out waiting for Wi-Fi"),
        CANCELLED("Wi-Fi startup was cancelled"),
    }

    fun enable(
        context: Context,
        isCancelled: () -> Boolean,
        timeoutMillis: Long = WirelessStartupPolicy.HOTSPOT_READY_MILLIS,
        log: (String) -> Unit = {},
    ): Result {
        val app = context.applicationContext
        val wifi = app.getSystemService(WifiManager::class.java)
            ?: return Result.FAILED
        if (runCatching { wifi.isWifiEnabled }.getOrDefault(false)) return Result.READY

        val deadline = System.nanoTime() + timeoutMillis * 1_000_000L
        val pollMillis = WirelessStartupPolicy.interfacePollMillis(app)
        fun stopped(): Result? = when {
            isCancelled() || Thread.currentThread().isInterrupted -> Result.CANCELLED
            System.nanoTime() >= deadline -> Result.TIMED_OUT
            else -> null
        }
        fun remainingMillis() = ((deadline - System.nanoTime()) / 1_000_000L).coerceAtLeast(1)

        stopped()?.let { return it }
        if (requestEnable(wifi, log)) {
            waitEnabled(wifi, pollMillis, ::stopped, ::remainingMillis)?.let { return it }
        }

        stopped()?.let { return it }
        if (!HongqiHeadUnit.skipsBackgroundAdb(app) && enableViaAdb(app, log, ::stopped, ::remainingMillis)) {
            waitEnabled(wifi, pollMillis, ::stopped, ::remainingMillis)?.let { return it }
        }

        return stopped() ?: if (runCatching { wifi.isWifiEnabled }.getOrDefault(false)) Result.READY else Result.FAILED
    }

    private fun requestEnable(wifi: WifiManager, log: (String) -> Unit): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            runCatching {
                wifi.javaClass.getMethod("setWifiEnabled", Boolean::class.javaPrimitiveType)
                    .invoke(wifi, true) as Boolean
            }.getOrNull()?.let { accepted ->
                log("Wi-Fi client enable setWifiEnabled accepted=$accepted")
                return accepted
            }
        } else {
            @Suppress("DEPRECATION")
            runCatching { wifi.setWifiEnabled(true) }.getOrNull()?.let { accepted ->
                log("Wi-Fi client enable legacy setWifiEnabled accepted=$accepted")
                return accepted
            }
        }
        return false
    }

    private fun enableViaAdb(
        context: Context,
        log: (String) -> Unit,
        stopped: () -> Result?,
        remainingMillis: () -> Long,
    ): Boolean {
        if (stopped() != null) return false
        val client = LocalAdb(AdbKeys.load(context))
        return client.use {
            if (it.connect(mayAsk = false) != LocalAdb.Access.READY) return false
            val timeout = remainingMillis().coerceAtMost(30_000).toInt().coerceAtLeast(1)
            val output = it.shell("svc wifi enable", timeout) ?: return false
            log("Wi-Fi client enable svc wifi: ${output.trim().ifEmpty { "ok" }}")
            true
        }
    }

    private fun waitEnabled(
        wifi: WifiManager,
        pollMillis: Long,
        stopped: () -> Result?,
        remainingMillis: () -> Long,
    ): Result? {
        while (true) {
            stopped()?.let { return it }
            if (runCatching { wifi.isWifiEnabled }.getOrDefault(false)) return Result.READY
            val wait = minOf(pollMillis, remainingMillis())
            if (wait <= 0) return Result.TIMED_OUT
            try {
                Thread.sleep(wait)
            } catch (_: InterruptedException) {
                Thread.currentThread().interrupt()
                return Result.CANCELLED
            }
        }
    }
}
