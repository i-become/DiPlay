package com.shilapi.xcertplay.newui

import android.Manifest
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.pm.PackageManager
import android.net.wifi.SupplicantState
import android.net.wifi.WifiManager
import android.os.Build
import com.shilapi.xcertplay.AirPlayPersistence
import com.shilapi.xcertplay.CarPlayBackgroundSession
import com.shilapi.xcertplay.network.CarHotspotStatus
import com.shilapi.xcertplay.orchestration.WirelessHotspotMode
import java.text.DateFormat
import java.util.Date

data class NewUiTopBarStatus(
    val timeText: String,
    val locationActive: Boolean,
    val bluetoothActive: Boolean,
    val wifiConnected: Boolean,
    val hotspotActive: Boolean,
) {
    companion object {
        val placeholder = NewUiTopBarStatus(
            timeText = "",
            locationActive = false,
            bluetoothActive = false,
            wifiConnected = false,
            hotspotActive = false,
        )
    }
}

internal object NewUiTopBarStatusReader {
    fun read(context: Context): NewUiTopBarStatus {
        val app = context.applicationContext
        return NewUiTopBarStatus(
            timeText = DateFormat.getTimeInstance(DateFormat.SHORT).format(Date()),
            locationActive = locationActive(app),
            bluetoothActive = bluetoothActive(app),
            wifiConnected = wifiConnected(app),
            hotspotActive = hotspotActive(app),
        )
    }

    private fun locationActive(context: Context): Boolean {
        val permitted = context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED
        return permitted && AirPlayPersistence.loadLocationReportingEnabled(context)
    }

    private fun bluetoothActive(context: Context): Boolean {
        if (Build.VERSION.SDK_INT >= 31 &&
            context.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return false
        }
        val adapter = context.getSystemService(BluetoothManager::class.java)?.adapter ?: return false
        return runCatching { adapter.isEnabled }.getOrDefault(false)
    }

    private fun wifiConnected(context: Context): Boolean {
        val wifi = context.getSystemService(WifiManager::class.java) ?: return false
        if (!runCatching { wifi.isWifiEnabled }.getOrDefault(false)) return false
        val info = runCatching { wifi.connectionInfo }.getOrNull() ?: return false
        return info.networkId != -1 &&
            info.supplicantState == SupplicantState.COMPLETED
    }

    private fun hotspotActive(context: Context): Boolean {
        when (AirPlayPersistence.loadWirelessHotspotMode(context)) {
            WirelessHotspotMode.MANUAL ->
                return CarHotspotStatus.isEnabled(context) == true
            WirelessHotspotMode.LOCAL_ONLY_HOTSPOT,
            WirelessHotspotMode.WIFI_P2P,
            ->
                return CarPlayBackgroundSession.hasSession()
            WirelessHotspotMode.EXISTING_WIFI -> return false
        }
    }
}
