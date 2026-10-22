package com.shilapi.xcertplay.orchestration

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build

/** A saved, bonded phone can be dialled directly; discovery competes with the connection. */
fun cancelBluetoothDiscoveryForConnection(context: Context, adapter: BluetoothAdapter?) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
        context.checkSelfPermission(Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED
    ) return
    runCatching { adapter?.cancelDiscovery() }
}
