package com.shilapi.xcertplay.orchestration

import android.bluetooth.BluetoothAdapter

/** Wait for the OEM to start its radio; this never enables Bluetooth or changes pairing. */
internal fun awaitBluetoothStartup(
    state: () -> Int,
    allowBootWait: Boolean,
    cancelled: () -> Boolean,
    nowMillis: () -> Long = { System.nanoTime() / 1_000_000 },
    pause: (Long) -> Unit = Thread::sleep,
    timeoutMillis: Long = 8_000L,
): Boolean {
    val started = nowMillis()
    var turningOn = false
    while (true) {
        if (cancelled()) throw InterruptedException("Bluetooth startup cancelled")
        when (state()) {
            BluetoothAdapter.STATE_ON -> return true
            BluetoothAdapter.STATE_TURNING_ON -> turningOn = true
            BluetoothAdapter.STATE_OFF -> if (!allowBootWait || turningOn) return false
            else -> return false
        }
        val remaining = timeoutMillis - (nowMillis() - started)
        if (remaining <= 0) return false
        pause(minOf(100L, remaining))
    }
}
