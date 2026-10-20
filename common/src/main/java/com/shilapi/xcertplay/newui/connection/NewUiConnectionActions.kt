package com.shilapi.xcertplay.newui.connection

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultLauncher
import com.shilapi.xcertplay.AirPlayPersistence
import com.shilapi.xcertplay.CarPlayBackgroundSession
import com.shilapi.xcertplay.ConnectionProgressUiState
import com.shilapi.xcertplay.CarPlayHostActivity
import com.shilapi.xcertplay.DiPlayLauncher
import com.shilapi.xcertplay.messageResource
import com.shilapi.xcertplay.network.CarHotspotTethering
import com.shilapi.xcertplay.DiPlayBootstrap
import com.shilapi.xcertplay.DiPlayPreferences
import com.shilapi.xcertplay.appDialogBuilder
import com.shilapi.xcertplay.host.R
import com.shilapi.xcertplay.orchestration.ManualHotspotValidation
import com.shilapi.xcertplay.orchestration.WirelessHotspotMode

/**
 * Connection actions for the Hongqi new UI shell (phone pick, connect, disconnect, permissions).
 */
class NewUiConnectionActions(
    private val activity: ComponentActivity,
    private val notificationPermission: ActivityResultLauncher<String>,
    private val bluetoothPermission: ActivityResultLauncher<String>,
    private val locationPermission: ActivityResultLauncher<Array<String>>,
    private val onOpenConnectionMethod: () -> Unit,
    private val onOpenConnectPhone: (connectAfterSave: Boolean) -> Unit,
    private val onOpenSwitchPhone: () -> Unit,
) {
    private var pendingWireless = false
    private var pendingCarHotspotSetup = false
    private var notificationTransport = true

    var setupError: String? = null
        private set

    fun onConnectionMethodSaved() {
        pendingCarHotspotSetup = false
        pendingWireless = false
    }

    fun refreshSetupError() {
        setupError = runCatching {
            DiPlayBootstrap.ensure(activity, AirPlayPersistence.loadMfiTarget(activity))
        }.exceptionOrNull()?.let {
            activity.getString(R.string.setup_error_auth)
        }
    }

    fun openCarPlay() {
        if (DiPlayPreferences.phoneAddress(activity) == null) return
        when {
            CarPlayBackgroundSession.active -> openCarPlayProjection()
            CarPlayBackgroundSession.hasSession() -> Unit
            else -> connect(wireless = true)
        }
    }

    fun connect(wireless: Boolean) {
        if (wireless && pendingCarHotspotSetup) {
            toast(activity.getString(R.string.save_your_hotspot_details_in_connection_setup_first))
            onOpenConnectionMethod()
            return
        }
        if (setupError != null) {
            toast(setupError!!)
            return
        }
        if (wireless && AirPlayPersistence.loadWirelessHotspotMode(activity) == WirelessHotspotMode.MANUAL &&
            hotspotError(storedSsid(), storedPassword()) != null
        ) {
            pendingCarHotspotSetup = true
            onOpenConnectionMethod()
            toast(activity.getString(R.string.save_the_name_and_password_from_the_car_s_hotspot_settings))
            return
        }
        if (wireless && carHotspotOff()) {
            carHotspotOffDialog()
            return
        }
        if (wireless && DiPlayPreferences.phoneAddress(activity) == null) {
            pendingWireless = true
            onOpenConnectPhone(true)
            return
        }
        val preferences = activity.getSharedPreferences("diplay", Context.MODE_PRIVATE)
        if (Build.VERSION.SDK_INT >= 33 &&
            activity.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED &&
            !preferences.getBoolean("notification_asked", false)
        ) {
            preferences.edit().putBoolean("notification_asked", true).apply()
            notificationTransport = wireless
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
            return
        }
        if (CarPlayBackgroundSession.hasSession()) {
            if (CarPlayBackgroundSession.active) {
                openCarPlayProjection()
                return
            }
            DiPlayLauncher.startConnectionHub(activity)
            return
        }
        AirPlayPersistence.saveWirelessEnabled(activity, wireless)
        startCarPlayHost(connectionHub = true)
    }

    fun disconnect(onComplete: () -> Unit) {
        CarPlayBackgroundSession.stop {
            ConnectionProgressUiState.reset()
            activity.runOnUiThread(onComplete)
        }
    }

    fun openPhonePicker() {
        if (DiPlayPreferences.phoneAddress(activity) == null) {
            onOpenConnectPhone(pendingWireless)
        } else {
            onOpenSwitchPhone()
        }
    }

    fun selectBondedPhone(address: String, displayName: String) {
        DiPlayPreferences.savePhone(activity, address, displayName.ifBlank { "iPhone" })
        val start = pendingWireless
        pendingWireless = false
        if (start) connect(true)
    }

    fun saveBondedPhone(address: String, displayName: String) {
        DiPlayPreferences.savePhone(activity, address, displayName.ifBlank { "iPhone" })
        pendingWireless = false
    }

    fun openBluetoothSettings() {
        openSystem(Intent(Settings.ACTION_BLUETOOTH_SETTINGS))
    }

    fun requestBluetoothPermissionForPhone() {
        if (Build.VERSION.SDK_INT >= 31) {
            bluetoothPermission.launch(Manifest.permission.BLUETOOTH_CONNECT)
        }
    }

    fun onNotificationPermissionResult() {
        connect(notificationTransport)
    }

    fun onBluetoothPermissionGranted() = Unit

    fun onLocationReportingChanged(enabled: Boolean, rerender: () -> Unit) {
        if (enabled && !hasPreciseLocation()) {
            rerender()
            locationPermission.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION,
                ),
            )
            return
        }
        applyLocationReporting(enabled, rerender)
    }

    fun applyLocationReporting(enabled: Boolean, rerender: () -> Unit) {
        if (AirPlayPersistence.loadLocationReportingEnabled(activity) == enabled) return
        AirPlayPersistence.saveLocationReportingEnabled(activity, enabled)
        rerender()
        if (CarPlayBackgroundSession.hasSession()) {
            connect(AirPlayPersistence.loadWirelessEnabled(activity))
        } else {
            toast(activity.getString(R.string.saved_for_your_next_connection))
        }
    }

    fun hasPreciseLocation(): Boolean =
        activity.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED

    private fun startCarPlayHost(connectionHub: Boolean) {
        if (connectionHub) {
            DiPlayLauncher.startConnectionHub(activity)
        } else {
            activity.startActivity(
                Intent(activity, CarPlayHostActivity::class.java)
                    .putExtra(DiPlayLauncher.EXTRA_ENTER_CARPLAY_PROJECTION, true)
                    .addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or Intent.FLAG_ACTIVITY_NO_ANIMATION),
            )
            activity.overridePendingTransition(0, 0)
        }
    }

    private fun openCarPlayProjection() {
        DiPlayLauncher.openCarPlayProjection(activity)
    }

    private fun carHotspotOff(): Boolean =
        AirPlayPersistence.loadWirelessHotspotMode(activity) == WirelessHotspotMode.MANUAL &&
            com.shilapi.xcertplay.network.CarHotspotStatus.isEnabled(activity) == false &&
            !CarHotspotTethering.permitted(activity)

    private fun carHotspotOffDialog() {
        activity.appDialogBuilder().setTitle(activity.getString(R.string.car_hotspot_is_off))
            .setMessage(
                activity.getString(
                    R.string.msg_car_hotspot_connect,
                    AirPlayPersistence.loadManualHotspotSsid(activity),
                ),
            )
            .setPositiveButton(activity.getString(R.string.open_car_settings)) { _, _ -> openCarWifiSettings() }
            .setNeutralButton(activity.getString(R.string.connect)) { _, _ -> connect(true) }
            .setNegativeButton(activity.getString(R.string.cancel), null)
            .show()
    }

    private fun openCarWifiSettings() {
        NewUiConnectionSettingsIntents.openCarHotspotSettings(activity)
    }

    private fun storedSsid() = AirPlayPersistence.loadManualHotspotSsid(activity)
    private fun storedPassword() = AirPlayPersistence.loadManualHotspotPassphrase(activity)

    private fun hotspotError(ssid: String, password: String): String? =
        ManualHotspotValidation.error(ssid, password)?.let { activity.getString(it.messageResource()) }

    private fun openSystem(intent: Intent) {
        runCatching { activity.startActivity(intent) }
            .onFailure { toast(activity.getString(R.string.open_this_setting_from_your_car_s_settings_app)) }
    }

    private fun toast(message: String) {
        Toast.makeText(activity, message, Toast.LENGTH_LONG).show()
    }
}
