package com.shilapi.xcertplay

import android.bluetooth.BluetoothAdapter
import android.content.Context
import android.util.Log
import com.shilapi.xcertplay.hud.Hs5HeadUnit

/** Wireless CarPlay needs classic Bluetooth before pairing; on cold boot it is often still off. */
internal object Hs5BootBluetoothWarmup {
    private const val TAG = "DiPlay-BtWarmup"

    fun scheduleIfNeeded(context: Context) {
        val app = context.applicationContext
        if (!Hs5HeadUnit.isHeadUnit(app)) return
        if (!DiPlayPreferences.autoConnectWireless(app)) return
        Thread({
            runCatching { requestEnable(app) }
                .onFailure { Log.w(TAG, "Bluetooth warm-up failed: ${it.javaClass.simpleName}") }
        }, "diplay-bt-warmup").apply { isDaemon = true }.start()
    }

    private fun requestEnable(context: Context) {
        val adapter = BluetoothAdapter.getDefaultAdapter() ?: return
        if (adapter.isEnabled) return
        @Suppress("DEPRECATION")
        val started = adapter.enable()
        Log.i(TAG, "requested Bluetooth enable at boot started=$started")
    }
}
